package com.vknxworks.elementctrl.call

import android.content.Context
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.notify.NotificationAvatars
import kotlinx.coroutines.delay
import org.matrix.rustcomponents.sdk.MessageLikeEventContent
import org.matrix.rustcomponents.sdk.NotificationEvent
import org.matrix.rustcomponents.sdk.NotificationItem
import org.matrix.rustcomponents.sdk.RtcCallIntent
import org.matrix.rustcomponents.sdk.RtcNotificationType
import org.matrix.rustcomponents.sdk.TimelineEventContent

/**
 * Turns a pushed MatrixRTC call notification (m.rtc.notification, sent by Element X, Element Web and ElementCtrl
 * when a call starts) into ringing, the same way Element X decides: a "ring" notification that hasn't expired, from
 * someone else, in a room that really has a call going on. A "notification" (a group call starting) and anything
 * that can't ring any more are left to the normal message notification.
 */
object CallPush {
    /** What [PushNotifications] should do with a call event. */
    sealed interface Result {
        /** It rings (or was shown as missed): nothing more to show. */
        data object Rang : Result
        /** Show it as a normal message notification with this text. */
        data class AsMessage(val missed: Boolean, val audioOnly: Boolean) : Result
        /** Nothing to show (your own call, or expired). */
        data object Skip : Result
    }

    fun content(item: NotificationItem): MessageLikeEventContent.RtcNotification? {
        val ev = item.event as? NotificationEvent.Timeline ?: return null
        val c = runCatching { ev.event.content() }.getOrNull() as? TimelineEventContent.MessageLike ?: return null
        return c.content as? MessageLikeEventContent.RtcNotification
    }

    suspend fun handle(
        context: Context,
        session: MatrixSession,
        roomId: String,
        eventId: String,
        item: NotificationItem,
        rtc: MessageLikeEventContent.RtcNotification,
    ): Result {
        val ev = item.event as NotificationEvent.Timeline
        val sender = runCatching { ev.event.senderId() }.getOrNull() ?: return Result.Skip
        if (sender == session.userId) return Result.Skip
        val audioOnly = rtc.callIntent == RtcCallIntent.AUDIO
        if (rtc.notificationType != RtcNotificationType.RING) return Result.AsMessage(missed = false, audioOnly = audioOnly)
        val expiresAt = rtc.expirationTs.toLong()
        if (expiresAt <= System.currentTimeMillis()) return Result.AsMessage(missed = true, audioOnly = audioOnly)
        // The room's call membership may need a moment to sync after the push.
        val active = session.withBackgroundSync {
            session.subscribeToRoom(roomId)
            var found = false
            repeat(10) {
                if (found) return@repeat
                found = runCatching { session.getRoom(roomId)?.use { it.roomInfo().hasRoomCall } }.getOrNull() == true
                if (!found) delay(500)
            }
            found
        }
        if (!active) return Result.AsMessage(missed = true, audioOnly = audioOnly)
        val senderAvatar = item.senderInfo.avatarUrl
        NotificationAvatars.load(session, senderAvatar)
        CallManager.ring(
            context,
            session,
            IncomingCall(
                roomId = roomId,
                eventId = eventId,
                senderId = sender,
                senderName = item.senderInfo.displayName ?: sender,
                roomName = item.roomInfo.displayName,
                isDm = item.roomInfo.isDm,
                audioOnly = audioOnly,
                avatarUrl = senderAvatar,
                expiresAt = expiresAt,
            ),
        )
        return Result.Rang
    }
}
