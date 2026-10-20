package com.vknxworks.elementctrl.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.MainActivity
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.AppState
import com.vknxworks.elementctrl.matrix.RoomSummary
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.matrix.rustcomponents.sdk.ReceiptType

/**
 * The background check: while the app is closed, a job syncs every ~15 minutes (Android's minimum) and notifies
 * about rooms with new unread messages. It runs with or without push through ntfy, so a push that never arrived
 * (ntfy disconnected, the homeserver dropped the pusher) still turns into a notification.
 */
object Notifier {
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_INVITES = "invites"
    const val EXTRA_ROOM_ID = "room_id"
    private const val WORK = "background-sync"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_INVITES, "Invites", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun prefs(context: Context) = context.getSharedPreferences("notified", Context.MODE_PRIVATE)

    /** Forgets which rooms were already notified about (on sign-out). */
    fun forgetAll(context: Context) {
        prefs(context).edit().clear().commit()
    }

    fun clear(context: Context, roomId: String) {
        NotificationManagerCompat.from(context).cancel(roomId.hashCode())
    }

    /** Remembers that a room was notified up to [ts], so the background check doesn't show it a second time. */
    fun markNotified(context: Context, roomId: String, ts: Long) {
        val p = prefs(context)
        if (ts > p.getLong(roomId, 0L)) p.edit().putLong(roomId, ts).apply()
    }

    /** Posts or updates one notification per room with something new since the last check. */
    fun notifyRooms(context: Context, rooms: List<RoomSummary>) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val p = prefs(context)
        val edit = p.edit()
        // Chats that already have a notification up (usually from a push): leave it as it is.
        val showing = runCatching { NotificationManagerCompat.from(context).activeNotifications.map { it.id }.toSet() }.getOrDefault(emptySet())
        for (r in rooms) {
            val ts = r.lastTimestamp ?: continue
            val wanted = when {
                r.isInvite -> true
                r.isMuted -> false
                else -> r.unreadNotifications > 0 && !r.lastIsOwn
            }
            if (!wanted || ts <= p.getLong(r.roomId, 0L)) continue
            edit.putLong(r.roomId, ts)
            if (r.roomId.hashCode() in showing) continue
            post(context, r)
        }
        edit.apply()
    }

    private fun post(context: Context, r: RoomSummary) {
        val open = PendingIntent.getActivity(
            context, r.roomId.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ROOM_ID, r.roomId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, if (r.isInvite) CHANNEL_INVITES else CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(r.name)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setWhen(r.lastTimestamp ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                PushNotifications.redacted(context, if (r.isInvite) CHANNEL_INVITES else CHANNEL_MESSAGES)
                    .setContentText(if (r.isInvite) "New invite" else "New message")
                    .build()
            )
        if (!ElementCtrlApp.graph.lock.showNotificationContent) {
            // Content hidden (default with app lock): no room name, sender or text.
            builder.setContentTitle("ElementCtrl").setContentText(if (r.isInvite) "New invite" else "New message")
            if (!r.isInvite) builder.setNumber(r.unreadNotifications.toInt()).addAction(PushNotifications.markReadAction(context, r.roomId))
        } else if (r.isInvite) {
            builder.setContentText("${r.inviterName ?: "Someone"} invited you")
            NotificationAvatars.cached(r.avatarUrl)?.let { builder.setLargeIcon(it) }
        } else {
            val line = listOfNotNull(r.lastSender?.takeIf { !r.isDm }, r.lastMessage).joinToString(": ")
            builder.setContentText(line)
                .setStyle(NotificationCompat.BigTextStyle().bigText(line))
                .setNumber(r.unreadNotifications.toInt())
                .setPriority(if (r.unreadMentions > 0 || r.isDm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .addAction(PushNotifications.markReadAction(context, r.roomId))
            NotificationAvatars.cached(r.avatarUrl)?.let { builder.setLargeIcon(it) }
        }
        runCatching { NotificationManagerCompat.from(context).notify(r.roomId.hashCode(), builder.build()) }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = ElementCtrlApp.graph
        if (!graph.prefs.notifications.value) return Result.success()
        val state = withTimeoutOrNull(30_000) { graph.state.first { it !is AppState.Loading } }
        val session = (state as? AppState.SignedIn)?.session ?: return Result.success()
        if (session.inForeground) return Result.success()
        // Pushed messages still waiting to be fetched (the fetch job was held back): fetch them now.
        if (com.vknxworks.elementctrl.notify.Push.isEnabled(applicationContext) && PushQueue.pending(applicationContext).isNotEmpty()) {
            FetchPushWorker.enqueue(applicationContext)
        }
        session.withBackgroundSync {
            // Give the sliding sync loop time to bring the room list up to date.
            withTimeoutOrNull(20_000) { session.roomsLoaded.first { it } }
            delay(8_000)
            // Chat pictures first (from the SDK's media cache when it has them), so the notifications show them.
            session.rooms.value.filter { it.hasUnread || it.isInvite }.take(10).forEach { NotificationAvatars.load(session, it.avatarUrl) }
            Notifier.notifyRooms(applicationContext, session.rooms.value)
        }
        return Result.success()
    }
}
