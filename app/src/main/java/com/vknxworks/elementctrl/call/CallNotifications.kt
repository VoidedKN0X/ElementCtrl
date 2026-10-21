package com.vknxworks.elementctrl.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.MainActivity
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.notify.NotificationAvatars
import com.vknxworks.elementctrl.notify.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.CallDeclineListener
import org.matrix.rustcomponents.sdk.TaskHandle

/** The ringing, missed-call and ongoing-call notifications. */
object CallNotifications {
    const val CHANNEL_INCOMING = "calls_incoming"
    const val CHANNEL_ONGOING = "calls_ongoing"
    private const val ID_RINGING = 0x0CA11
    const val ID_ONGOING = 0x0CA12

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, context.getString(R.string.channel_calls_incoming), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 800, 800, 800)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.channel_calls_ongoing), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
            },
        )
    }

    private fun showContent() = ElementCtrlApp.graph.lock.showNotificationContent

    private fun canNotify(context: Context) =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun callScreen(context: Context, mode: String, requestCode: Int, call: IncomingCall?): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, CallActivity::class.java)
                .putExtra(CallActivity.EXTRA_MODE, mode)
                .apply {
                    if (call != null) {
                        putExtra(CallActivity.EXTRA_ROOM_ID, call.roomId)
                        putExtra(CallActivity.EXTRA_AUDIO_ONLY, call.audioOnly)
                    }
                }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun action(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, CallActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * The incoming-call notification: rings until answered, declined or timed out, and opens the full-screen ringing
     * screen (also over the phone's lock screen). With message content hidden (the app lock's default), it says
     * "Incoming call" without the caller's name or picture.
     */
    fun ring(context: Context, call: IncomingCall) {
        if (!canNotify(context)) return
        val show = showContent()
        val caller = Person.Builder()
            .setName(if (show) call.senderName else context.getString(R.string.app_name))
            .setImportant(true)
            .apply { if (show) NotificationAvatars.icon(call.avatarUrl)?.let { setIcon(it) } }
            .build()
        val answer = callScreen(context, CallActivity.MODE_ANSWER, 0x0CA21, call)
        val decline = action(context, CallActionReceiver.ACTION_DECLINE, 0x0CA22)
        val full = callScreen(context, CallActivity.MODE_RINGING, 0x0CA23, call)
        val text = context.getString(if (call.audioOnly) R.string.call_incoming_voice else R.string.call_incoming_video)
        val n = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, decline, answer).setIsVideo(!call.audioOnly))
            .addPerson(caller)
            .setContentTitle(if (show) call.senderName else context.getString(R.string.app_name))
            .setContentText(if (show && !call.isDm) "$text · ${call.roomName}" else text)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_INCOMING)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.call_incoming))
                    .build(),
            )
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .setDeleteIntent(decline)
            .setTimeoutAfter((call.expiresAt - System.currentTimeMillis()).coerceIn(1_000, 90_000))
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        runCatching { NotificationManagerCompat.from(context).notify(ID_RINGING, n) }
    }

    fun cancelRinging(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_RINGING)
    }

    /** "Missed call" in the chat's notification, opening the chat. */
    fun missed(context: Context, call: IncomingCall) {
        if (!canNotify(context)) return
        val show = showContent()
        val open = PendingIntent.getActivity(
            context, call.roomId.hashCode() + 7,
            Intent(context, MainActivity::class.java).putExtra(Notifier.EXTRA_ROOM_ID, call.roomId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = context.getString(if (call.audioOnly) R.string.call_missed_voice else R.string.call_missed_video)
        val n = NotificationCompat.Builder(context, Notifier.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentTitle(if (show) (if (call.isDm) call.senderName else call.roomName) else context.getString(R.string.app_name))
            .setContentText(if (show && !call.isDm) "$text · ${call.senderName}" else if (show) text else context.getString(R.string.call_missed))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, Notifier.CHANNEL_MESSAGES)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(context.getString(R.string.call_missed))
                    .build(),
            )
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(call.roomId.hashCode() + 7, n) }
    }

    /** The ongoing-call notification of the call's foreground service, with Hang up. */
    fun ongoing(context: Context, call: ActiveCall?): Notification {
        val show = showContent()
        val name = if (show && call != null) call.roomName else context.getString(R.string.app_name)
        val person = Person.Builder().setName(name).setImportant(true).build()
        val hangUp = action(context, CallActionReceiver.ACTION_HANG_UP, 0x0CA24)
        val open = callScreen(context, CallActivity.MODE_RESUME, 0x0CA25, null)
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUp))
            .setContentTitle(context.getString(R.string.call_ongoing_title))
            .setContentText(context.getString(R.string.call_ongoing_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setWhen(System.currentTimeMillis())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }
}

/** Decline (ringing notification) and Hang up (ongoing-call notification). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE -> CallManager.decline(context)
            ACTION_HANG_UP -> CallManager.requestHangUp()
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.vknxworks.elementctrl.call.DECLINE"
        const val ACTION_HANG_UP = "com.vknxworks.elementctrl.call.HANG_UP"
    }
}

/**
 * Stops ringing when it should, like Element X: when the ringing times out (then "Missed call"), the caller hangs up
 * before anyone answers (the room has no call any more: "Missed call"), you join from another device, or you decline
 * on another device. Keeps the phone awake for the ringing.
 */
internal object RingWatcher {
    private var job: Job? = null
    private var declineHandle: TaskHandle? = null

    fun watch(context: Context, session: MatrixSession, call: IncomingCall, ringFor: Long) {
        cancel()
        val app = context.applicationContext
        job = ElementCtrlApp.graph.scope.launch {
            val wake = app.getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${app.packageName}:IncomingCall")
            runCatching { wake?.acquire(ringFor + 5_000) }
            try {
                withContext(Dispatchers.IO) {
                    val room = session.getRoom(call.roomId) ?: return@withContext
                    room.use { r ->
                        declineHandle = runCatching {
                            r.subscribeToCallDeclineEvents(call.eventId, object : CallDeclineListener {
                                override fun call(declinerUserId: String) {
                                    // Declined on one of your other devices.
                                    if (declinerUserId == session.userId) {
                                        ElementCtrlApp.graph.scope.launch { CallManager.stopRinging(app, missed = false, eventId = call.eventId) }
                                    }
                                }
                            })
                        }.getOrNull()
                        val until = System.currentTimeMillis() + ringFor
                        var sawCall = false
                        while (System.currentTimeMillis() < until) {
                            val info = runCatching { r.roomInfo() }.getOrNull()
                            if (info != null) {
                                if (info.hasRoomCall) sawCall = true
                                // Joined on another of your devices.
                                if (session.userId in info.activeRoomCallParticipants) {
                                    withContext(Dispatchers.Main) { CallManager.stopRinging(app, missed = false, eventId = call.eventId) }
                                    return@withContext
                                }
                                // The caller hung up before anyone answered.
                                if (sawCall && !info.hasRoomCall) {
                                    withContext(Dispatchers.Main) { CallManager.stopRinging(app, missed = true, eventId = call.eventId) }
                                    return@withContext
                                }
                            }
                            delay(1_000)
                        }
                        withContext(Dispatchers.Main) { CallManager.stopRinging(app, missed = true, eventId = call.eventId) }
                    }
                }
            } finally {
                runCatching { declineHandle?.cancel() }
                declineHandle = null
                runCatching { if (wake?.isHeld == true) wake.release() }
            }
        }
    }

    fun cancel() {
        val j = job
        job = null
        runCatching { declineHandle?.cancel() }
        declineHandle = null
        j?.cancel()
    }
}
