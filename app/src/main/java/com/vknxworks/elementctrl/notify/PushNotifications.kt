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
import com.vknxworks.elementctrl.Strings

/** The room the user is looking at right now, so a push for it doesn't also ring. */
object OpenRoom {
    @Volatile var id: String? = null
}

/** Turns pushed event IDs into chat-style notifications (one per room) with reply and mark-as-read. */
object PushNotifications {
    const val KEY_REPLY = "reply_text"
    private data class Line(
        val sender: String,
        val text: String,
        val ts: Long,
        val own: Boolean,
        val eventId: String? = null,
        val senderId: String? = null,
        val avatarUrl: String? = null,
    )
    /** Each chat's picture (room avatar, or the other person's for a DM), for the notification's large icon. */
    private val roomAvatars = HashMap<String, String?>()
    private val history = HashMap<String, ArrayDeque<Line>>()

    /** ENCRYPTED: shown as "🔒 New encrypted message" because the key hasn't arrived yet; worth fetching again. */
    enum class Outcome { SHOWN, ENCRYPTED, SKIPPED, NOT_FOUND }

    /**
     * Fetches, decrypts and shows one pushed event. Throws if the homeserver couldn't be reached, so the push queue
     * keeps it and tries again.
     */
    suspend fun resolve(context: Context, session: com.vknxworks.elementctrl.matrix.MatrixSession, roomId: String, eventId: String): Outcome {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Outcome.SKIPPED
        // Calls ring even in the chat you have open, so the event is fetched before skipping that chat.
        val item = when (val r = session.fetchNotification(roomId, eventId)) {
            is com.vknxworks.elementctrl.matrix.NotificationFetch.Found -> r.item
            com.vknxworks.elementctrl.matrix.NotificationFetch.NotFound -> return if (session.inForeground && OpenRoom.id == roomId) Outcome.SKIPPED else Outcome.NOT_FOUND
            com.vknxworks.elementctrl.matrix.NotificationFetch.Nothing -> return Outcome.SKIPPED
        }
        val rtc = com.vknxworks.elementctrl.call.CallPush.content(item)
        var callText: String? = null
        if (rtc != null) {
            when (val c = com.vknxworks.elementctrl.call.CallPush.handle(context, session, roomId, eventId, item, rtc)) {
                com.vknxworks.elementctrl.call.CallPush.Result.Rang -> return Outcome.SHOWN
                com.vknxworks.elementctrl.call.CallPush.Result.Skip -> return Outcome.SKIPPED
                is com.vknxworks.elementctrl.call.CallPush.Result.AsMessage -> callText = context.getString(
                    when {
                        !c.missed -> R.string.call_started
                        c.audioOnly -> R.string.call_missed_voice
                        else -> R.string.call_missed_video
                    },
                ).let { if (c.missed) "📞 $it" else it }
            }
        }
        if (session.inForeground && OpenRoom.id == roomId) return Outcome.SKIPPED
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
        val senderName = item.senderInfo.displayName ?: Strings.str(R.string.invite_someone)
        val senderAvatar = item.senderInfo.avatarUrl
        val senderId = (item.event as? NotificationEvent.Timeline)?.event?.senderId()
        val roomAvatar = item.roomInfo.avatarUrl ?: senderAvatar.takeIf { item.roomInfo.isDm }
        synchronized(history) { roomAvatars[roomId] = roomAvatar }
        // Pictures load through the SDK's encrypted media cache (from the phone after the first time), and never
        // hold up the notification for more than a few seconds.
        NotificationAvatars.load(session, senderAvatar)
        if (roomAvatar != senderAvatar) NotificationAvatars.load(session, roomAvatar)

        val text: String = when (val ev = item.event) {
            is NotificationEvent.Invite -> {
                postInvite(context, roomId, roomName, senderName, item.roomInfo.isDm)
                return Outcome.SHOWN
            }
            is NotificationEvent.Timeline -> when (val c = ev.event.content()) {
                is TimelineEventContent.MessageLike -> when (val m = c.content) {
                    is MessageLikeEventContent.RoomMessage -> m.messageType.previewText("")
                    is MessageLikeEventContent.Poll -> "📊 ${m.question}"
                    is MessageLikeEventContent.Sticker -> Strings.str(R.string.sticker_2)
                    is MessageLikeEventContent.ReactionContent -> Strings.str(R.string.reacted_to_your_message)
                    is MessageLikeEventContent.CallInvite -> Strings.str(R.string.incoming_call)
                    is MessageLikeEventContent.RtcNotification -> callText ?: Strings.str(R.string.incoming_call)
                    is MessageLikeEventContent.RoomEncrypted -> Strings.str(R.string.new_encrypted_message)
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
                    this[i] = Line(senderName, text, ts, false, eventId, senderId, senderAvatar)
                    replaced = true
                } else addLast(Line(senderName, text, ts, false, eventId, senderId, senderAvatar))
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
        if (room != null && ElementCtrlApp.graph.lock.showNotificationContent) builder.setContentTitle(room.name).setContentText(Strings.str(R.string.new_message))
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
            .setContentTitle(Strings.str(R.string.app_name))
            .setContentText(if (count > 1) Strings.plural(R.plurals.new_messages, count, count) else Strings.str(R.string.new_message))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

    /**
     * "Mark as read" button; safe even when content is hidden, since it reveals nothing. It does send read receipts
     * from your account, so on the lock screen the phone has to be unlocked first.
     */
    fun markReadAction(context: Context, roomId: String): NotificationCompat.Action = NotificationCompat.Action.Builder(
        0, Strings.str(R.string.room_mark_read),
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
        val me = Person.Builder().setName(Strings.str(R.string.you_title)).build()
        val style = NotificationCompat.MessagingStyle(me).setConversationTitle(if (isDm) null else roomName).setGroupConversation(!isDm)
        lines.forEach { l ->
            style.addMessage(
                l.text, l.ts,
                if (l.own) null else Person.Builder()
                    .setName(if (isDm) roomName else l.sender)
                    .apply { l.senderId?.let { setKey(it) } }
                    .apply { NotificationAvatars.icon(l.avatarUrl)?.let { setIcon(it) } }
                    .build(),
            )
        }
        val largeIcon = NotificationAvatars.cached(synchronized(history) { roomAvatars[roomId] })
        val reply = NotificationCompat.Action.Builder(
            0, Strings.str(R.string.reply),
            PendingIntent.getBroadcast(
                context, roomId.hashCode() + 1,
                Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_REPLY).putExtra(Notifier.EXTRA_ROOM_ID, roomId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        ).addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel(Strings.str(R.string.message)).build()).setAllowGeneratedReplies(true)
            // Sending a message as you from the lock screen needs the phone unlocked first.
            .setAuthenticationRequired(true)
            .build()
        val read = markReadAction(context, roomId)
        val n = NotificationCompat.Builder(context, Notifier.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .apply { largeIcon?.let { setLargeIcon(it) } }
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
            .setContentTitle(if (hide) Strings.str(R.string.app_name) else roomName)
            .setContentText(if (hide) Strings.str(R.string.new_invite) else if (isDm) Strings.str(R.string.wants_to_chat, inviter) else Strings.str(R.string.invite_from, inviter))
            .setContentIntent(openIntent(context, roomId))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, Notifier.CHANNEL_INVITES)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setContentTitle(Strings.str(R.string.app_name))
                    .setContentText(Strings.str(R.string.new_invite))
                    .build()
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(roomId.hashCode(), n) }
    }

    /** Adds what we replied to the notification so the conversation stays readable. */
    fun appendOwnReply(context: Context, roomId: String, text: String) {
        val lines = synchronized(history) {
            history.getOrPut(roomId) { ArrayDeque() }.apply { addLast(Line(Strings.str(R.string.you_title), text, System.currentTimeMillis(), true)) }.toList()
        }
        val session = ElementCtrlApp.graph.session
        val name = session?.rooms?.value?.firstOrNull { it.roomId == roomId }?.name ?: Strings.str(R.string.chat)
        val dm = session?.rooms?.value?.firstOrNull { it.roomId == roomId }?.isDm == true
        post(context, roomId, name, dm, false, lines)
    }

    fun forget(roomId: String) { synchronized(history) { history.remove(roomId) } }

    fun clearAll(context: Context) {
        synchronized(history) { history.clear(); roomAvatars.clear() }
        NotificationAvatars.clear()
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
