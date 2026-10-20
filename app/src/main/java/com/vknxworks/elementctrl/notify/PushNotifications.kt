package com.vknxworks.elementctrl.notify

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.MainActivity
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.previewText
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.MessageLikeEventContent
import org.matrix.rustcomponents.sdk.NotificationEvent
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.TimelineEventContent
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown

/** The room the user is looking at right now, so a push for it doesn't also ring. */
object OpenRoom {
    @Volatile var id: String? = null
}

/** Turns pushed event IDs into chat-style notifications (one per room) with reply and mark-as-read. */
object PushNotifications {
    const val KEY_REPLY = "reply_text"
    private data class Line(val sender: String, val text: String, val ts: Long, val own: Boolean, val eventId: String? = null)
    private val history = HashMap<String, ArrayDeque<Line>>()

    /** ENCRYPTED: shown as "🔒 New encrypted message" because the key hasn't arrived yet; worth fetching again. */
    enum class Outcome { SHOWN, ENCRYPTED, SKIPPED, NOT_FOUND }

    /**
     * Fetches, decrypts and shows one pushed event. Throws if the homeserver couldn't be reached, so the push queue
     * keeps it and tries again.
     */
    suspend fun resolve(context: Context, session: com.vknxworks.elementctrl.matrix.MatrixSession, roomId: String, eventId: String): Outcome {
        if (session.inForeground && OpenRoom.id == roomId) return Outcome.SKIPPED
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Outcome.SKIPPED
        val item = when (val r = session.fetchNotification(roomId, eventId)) {
            is com.vknxworks.elementctrl.matrix.NotificationFetch.Found -> r.item
            com.vknxworks.elementctrl.matrix.NotificationFetch.NotFound -> return Outcome.NOT_FOUND
            com.vknxworks.elementctrl.matrix.NotificationFetch.Nothing -> return Outcome.SKIPPED
        }
        // Only rooms we're in (or invited to): ignore pushes naming a room we've left, been banned from, etc.
        // A room not in the local store yet (joined on another device since the last sync) is still allowed,
        // since the homeserver only returned the event because this account can see it.
        val membership = session.getRoom(roomId)?.use { r -> runCatching { r.roomInfo().membership }.getOrNull() }
        val isInvite = item.event is NotificationEvent.Invite
        when (membership) {
            null, Membership.JOINED -> Unit
            Membership.INVITED -> if (!isInvite) return Outcome.SKIPPED
            else -> return Outcome.SKIPPED
        }
        val roomName = item.roomInfo.displayName
        val senderName = item.senderInfo.displayName ?: "Someone"

        val text: String = when (val ev = item.event) {
            is NotificationEvent.Invite -> {
                postInvite(context, roomId, roomName, senderName, item.roomInfo.isDm)
                return Outcome.SHOWN
            }
            is NotificationEvent.Timeline -> when (val c = ev.event.content()) {
                is TimelineEventContent.MessageLike -> when (val m = c.content) {
                    is MessageLikeEventContent.RoomMessage -> m.messageType.previewText("")
                    is MessageLikeEventContent.Poll -> "📊 ${m.question}"
                    is MessageLikeEventContent.Sticker -> "Sticker"
                    is MessageLikeEventContent.ReactionContent -> "Reacted to your message"
                    is MessageLikeEventContent.CallInvite, is MessageLikeEventContent.RtcNotification -> "📞 Incoming call"
                    is MessageLikeEventContent.RoomEncrypted -> "🔒 New encrypted message"
                    else -> return Outcome.SKIPPED
                }
                is TimelineEventContent.State -> return Outcome.SKIPPED
            }
        }
        val encrypted = (item.event as? NotificationEvent.Timeline)?.let { ev ->
            ((ev.event.content() as? TimelineEventContent.MessageLike)?.content) is MessageLikeEventContent.RoomEncrypted
        } == true
        val ts = (item.event as? NotificationEvent.Timeline)?.event?.timestamp()?.toLong() ?: System.currentTimeMillis()
        var replaced = false
        val lines = synchronized(history) {
            history.getOrPut(roomId) { ArrayDeque() }.apply {
                // A message fetched again once its key arrived replaces its "encrypted" placeholder in place.
                val i = indexOfFirst { it.eventId == eventId }
                if (i >= 0) {
                    if (this[i].text == text) return if (encrypted) Outcome.ENCRYPTED else Outcome.SKIPPED
                    this[i] = Line(senderName, text, ts, false, eventId)
                    replaced = true
                } else addLast(Line(senderName, text, ts, false, eventId))
                while (size > 8) removeFirst()
            }.toList()
        }
        // Updating a placeholder with the real text shouldn't ring a second time.
        post(context, roomId, roomName, item.roomInfo.isDm, !replaced && (item.isNoisy != false || item.hasMention == true), lines)
        // The background check doesn't need to show this message again.
        Notifier.markNotified(context, roomId, ts)
        return if (encrypted) Outcome.ENCRYPTED else Outcome.SHOWN
    }

    /**
     * Shown when a pushed message couldn't be fetched (no network in deep sleep, server not answering): better a
     * plain "New message" than nothing. Doesn't replace a chat's notification that is already showing.
     */
    fun postFallback(context: Context, roomId: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = NotificationManagerCompat.from(context)
        if (runCatching { nm.activeNotifications.any { it.id == roomId.hashCode() } }.getOrDefault(false)) return
        val room = ElementCtrlApp.graph.session?.rooms?.value?.firstOrNull { it.roomId == roomId }
        val builder = redacted(context, Notifier.CHANNEL_MESSAGES)
            .setContentIntent(openIntent(context, roomId))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redacted(context, Notifier.CHANNEL_MESSAGES).build())
            .addAction(markReadAction(context, roomId))
        if (room != null && ElementCtrlApp.graph.lock.showNotificationContent) builder.setContentTitle(room.name).setContentText("New message")
        runCatching { nm.notify(roomId.hashCode(), builder.build()) }
        Battery.noteMissedFetch(context)
    }

    private fun openIntent(context: Context, roomId: String) = PendingIntent.getActivity(
        context, roomId.hashCode(),
        Intent(context, MainActivity::class.java).putExtra(Notifier.EXTRA_ROOM_ID, roomId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** What the lock screen (and, with content hidden, the shade) shows instead of the message. */
    fun redacted(context: Context, channel: String, count: Int = 1): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle("ElementCtrl")
            .setContentText(if (count > 1) "$count new messages" else "New message")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

    /**
     * "Mark as read" button; safe even when content is hidden, since it reveals nothing. It does send read receipts
     * from your account, so on the lock screen the phone has to be unlocked first.
     */
    fun markReadAction(context: Context, roomId: String): NotificationCompat.Action = NotificationCompat.Action.Builder(
        0, "Mark as read",
        PendingIntent.getBroadcast(
            context, roomId.hashCode() + 2,
            Intent(context, NotificationActionReceiver::class.java).putExtra(Notifier.EXTRA_ROOM_ID, roomId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false)
        .setAuthenticationRequired(true)
        .build()

    private fun post(context: Context, roomId: String, roomName: String, isDm: Boolean, noisy: Boolean, lines: List<Line>) {
        if (!ElementCtrlApp.graph.lock.showNotificationContent) {
            // App lock on (or the user chose to hide content): no sender, room or text anywhere, and no inline
            // reply, which would let anyone with the phone answer without the PIN.
            val n = redacted(context, Notifier.CHANNEL_MESSAGES, lines.count { !it.own })
                .setContentIntent(openIntent(context, roomId))
                .setAutoCancel(true)
                .setOnlyAlertOnce(!noisy)
                .setSilent(!noisy)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(redacted(context, Notifier.CHANNEL_MESSAGES).build())
                .addAction(markReadAction(context, roomId))
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(roomId.hashCode(), n) }
            return
        }
        val me = Person.Builder().setName("You").build()
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(if (isDm) null else roomName).setGroupConversation(!isDm)
        lines.forEach { l ->
            style.addMessage(l.text, l.ts, if (l.own) null else Person.Builder().setName(if (isDm) roomName else l.sender).build())
        }
        val reply = NotificationCompat.Action.Builder(
            0, "Reply",
            PendingIntent.getBroadcast(
                context, roomId.hashCode() + 1,
                Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_REPLY).putExtra(Notifier.EXTRA_ROOM_ID, roomId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        ).addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Message").build()).setAllowGeneratedReplies(true)
            // Sending a message as you from the lock screen needs the phone unlocked first.
            .setAuthenticationRequired(true)
            .build()
        val read = markReadAction(context, roomId)
        val n = NotificationCompat.Builder(context, Notifier.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .setContentIntent(openIntent(context, roomId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(!noisy)
            .setSilent(!noisy)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(redacted(context, Notifier.CHANNEL_MESSAGES).build())
            .addAction(reply)
            .addAction(read)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(roomId.hashCode(), n) }
    }

    private fun postInvite(context: Context, roomId: String, roomName: String, inviter: String, isDm: Boolean) {
        val hide = !ElementCtrlApp.graph.lock.showNotificationContent
        val n = NotificationCompat.Builder(context, Notifier.CHANNEL_INVITES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(if (hide) "ElementCtrl" else roomName)
            .setContentText(if (hide) "New invite" else if (isDm) "$inviter wants to chat" else "$inviter invited you")
            .setContentIntent(openIntent(context, roomId))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, Notifier.CHANNEL_INVITES)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setContentTitle("ElementCtrl")
                    .setContentText("New invite")
                    .build()
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(roomId.hashCode(), n) }
    }

    /** Adds what we replied to the notification so the conversation stays readable. */
    fun appendOwnReply(context: Context, roomId: String, text: String) {
        val lines = synchronized(history) {
            history.getOrPut(roomId) { ArrayDeque() }.apply { addLast(Line("You", text, System.currentTimeMillis(), true)) }.toList()
        }
        val session = ElementCtrlApp.graph.session
        val name = session?.rooms?.value?.firstOrNull { it.roomId == roomId }?.name ?: "Chat"
        val dm = session?.rooms?.value?.firstOrNull { it.roomId == roomId }?.isDm == true
        post(context, roomId, name, dm, false, lines)
    }

    fun forget(roomId: String) { synchronized(history) { history.remove(roomId) } }

    fun clearAll(context: Context) {
        synchronized(history) { history.clear() }
        NotificationManagerCompat.from(context).cancelAll()
    }
}

/** Reply and mark-as-read buttons on message notifications. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val roomId = intent.getStringExtra(Notifier.EXTRA_ROOM_ID) ?: return
        val replyText = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PushNotifications.KEY_REPLY)?.toString()
        val pending = goAsync()
        ElementCtrlApp.graph.scope.launch {
            try {
                val session = Push.waitForSession() ?: return@launch
                val room = session.getRoom(roomId) ?: return@launch
                room.use { r ->
                    if (intent.action == ACTION_REPLY && !replyText.isNullOrBlank()) {
                        runCatching {
                            r.timeline().use { t -> t.send(messageEventContentFromMarkdown(replyText)) }
                        }
                        runCatching { r.markAsRead(ReceiptType.READ) }
                        PushNotifications.appendOwnReply(context, roomId, replyText)
                    } else {
                        // Also move the "new messages" line, so the chat opens without it.
                        runCatching { r.markAsRead(ReceiptType.READ) }
                        runCatching { r.markAsRead(ReceiptType.FULLY_READ) }
                        runCatching { r.setUnreadFlag(false) }
                        PushNotifications.forget(roomId)
                        Notifier.clear(context, roomId)
                    }
                }
                session.refreshRoom(roomId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.vknxworks.elementctrl.REPLY"
    }
}
