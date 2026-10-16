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
    private data class Line(val sender: String, val text: String, val ts: Long, val own: Boolean)
    private val history = HashMap<String, ArrayDeque<Line>>()

    suspend fun handle(context: Context, roomId: String, eventId: String) {
        val session = Push.waitForSession() ?: return
        if (session.inForeground && OpenRoom.id == roomId) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val item = runCatching { session.notificationItem(roomId, eventId) }.getOrNull() ?: return
        val roomName = item.roomInfo.displayName
        val senderName = item.senderInfo.displayName ?: "Someone"

        val text: String = when (val ev = item.event) {
            is NotificationEvent.Invite -> {
                postInvite(context, roomId, roomName, senderName, item.roomInfo.isDm)
                return
            }
            is NotificationEvent.Timeline -> when (val c = ev.event.content()) {
                is TimelineEventContent.MessageLike -> when (val m = c.content) {
                    is MessageLikeEventContent.RoomMessage -> m.messageType.previewText("")
                    is MessageLikeEventContent.Poll -> "📊 ${m.question}"
                    is MessageLikeEventContent.Sticker -> "Sticker"
                    is MessageLikeEventContent.ReactionContent -> "Reacted to your message"
                    is MessageLikeEventContent.CallInvite, is MessageLikeEventContent.RtcNotification -> "📞 Incoming call"
                    is MessageLikeEventContent.RoomEncrypted -> "🔒 New encrypted message"
                    else -> return
                }
                is TimelineEventContent.State -> return
            }
        }
        val ts = (item.event as? NotificationEvent.Timeline)?.event?.timestamp()?.toLong() ?: System.currentTimeMillis()
        val lines = synchronized(history) {
            history.getOrPut(roomId) { ArrayDeque() }.apply {
                addLast(Line(senderName, text, ts, false))
                while (size > 8) removeFirst()
            }.toList()
        }
        post(context, roomId, roomName, item.roomInfo.isDm, item.isNoisy != false || item.hasMention == true, lines)
    }

    private fun openIntent(context: Context, roomId: String) = PendingIntent.getActivity(
        context, roomId.hashCode(),
        Intent(context, MainActivity::class.java).putExtra(Notifier.EXTRA_ROOM_ID, roomId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun post(context: Context, roomId: String, roomName: String, isDm: Boolean, noisy: Boolean, lines: List<Line>) {
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
        ).addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Message").build()).setAllowGeneratedReplies(true).build()
        val read = NotificationCompat.Action.Builder(
            0, "Mark as read",
            PendingIntent.getBroadcast(
                context, roomId.hashCode() + 2,
                Intent(context, NotificationActionReceiver::class.java).putExtra(Notifier.EXTRA_ROOM_ID, roomId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()
        val n = NotificationCompat.Builder(context, Notifier.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setStyle(style)
            .setContentIntent(openIntent(context, roomId))
            .setAutoCancel(true)
            .setOnlyAlertOnce(!noisy)
            .setSilent(!noisy)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(reply)
            .addAction(read)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(roomId.hashCode(), n) }
    }

    private fun postInvite(context: Context, roomId: String, roomName: String, inviter: String, isDm: Boolean) {
        val n = NotificationCompat.Builder(context, Notifier.CHANNEL_INVITES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(roomName)
            .setContentText(if (isDm) "$inviter wants to chat" else "$inviter invited you")
            .setContentIntent(openIntent(context, roomId))
            .setAutoCancel(true)
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

    fun forget(roomId: String) = synchronized(history) { history.remove(roomId) }

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
                        runCatching { r.markAsRead(ReceiptType.READ) }
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
