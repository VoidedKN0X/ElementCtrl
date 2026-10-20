package com.vknxworks.elementctrl.notify

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.vknxworks.elementctrl.ElementCtrlApp
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Receives what ntfy hands over: new endpoints, registration errors and push messages from your homeserver.
 *
 * It has to be exported for ntfy to reach it, so every broadcast must carry the secret token of our registration
 * (and, from Android 14, come from ntfy) or it's ignored. A push only names a room and an event; the event itself is
 * then fetched from the homeserver, so a forged push can't inject notification text.
 */
class PushReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(UnifiedPush.EXTRA_TOKEN) ?: return
        if (token != UnifiedPush.token(context)) return
        if (Build.VERSION.SDK_INT >= 34) {
            val sender = sentFromPackage
            if (sender != null && sender != UnifiedPush.NTFY) return
        }
        // Keep the CPU awake while the message is saved and the fetch job is handed to Android.
        val wakeLock = context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ElementCtrl:push")
            ?.apply { setReferenceCounted(false); acquire(10_000L) }
        try {
            when (intent.action) {
                UnifiedPush.ACTION_NEW_ENDPOINT -> {
                    val endpoint = intent.getStringExtra(UnifiedPush.EXTRA_ENDPOINT) ?: return
                    UnifiedPush.ack(context, token, intent.getStringExtra(UnifiedPush.EXTRA_ID))
                    val pending = goAsync()
                    ElementCtrlApp.graph.scope.launch {
                        try { Push.onNewEndpoint(context, endpoint) } finally { pending.finish() }
                    }
                }
                UnifiedPush.ACTION_REGISTRATION_FAILED -> Push.onRegistrationFailed(context, intent.getStringExtra(UnifiedPush.EXTRA_REASON))
                UnifiedPush.ACTION_UNREGISTERED -> Push.onUnregistered(context)
                UnifiedPush.ACTION_TEMP_UNAVAILABLE -> Push.onTempUnavailable()
                UnifiedPush.ACTION_MESSAGE -> {
                    val bytes = intent.getByteArrayExtra(UnifiedPush.EXTRA_BYTES_MESSAGE)
                        ?: intent.getStringExtra(UnifiedPush.EXTRA_MESSAGE)?.toByteArray()
                    if (bytes != null) onMessage(context, bytes)
                    // Acknowledged only once the event is safely queued on disk, so ntfy can redeliver otherwise.
                    UnifiedPush.ack(context, token, intent.getStringExtra(UnifiedPush.EXTRA_ID))
                }
            }
        } finally {
            if (wakeLock?.isHeld == true) runCatching { wakeLock.release() }
        }
    }

    private fun onMessage(context: Context, bytes: ByteArray) {
        if (!Push.isEnabled(context)) return
        Push.onMessageReceived(context)
        // Homeservers send {"notification":{"event_id":…,"room_id":…,"counts":{"unread":…}}}.
        val n = runCatching { JSONObject(String(bytes)).optJSONObject("notification") }.getOrNull() ?: return
        val roomId = n.optString("room_id").takeIf { it.startsWith("!") && it.length <= 255 }
        val eventId = n.optString("event_id").takeIf { it.startsWith("$") && it.length <= 255 }
        val unread = n.optJSONObject("counts")?.optInt("unread", -1) ?: -1
        if (roomId != null && eventId != null) {
            // Don't fetch here: a broadcast gets only seconds, and an app that has been asleep may have no network
            // yet. Queue it and let an expedited job fetch it, retrying until it can (as Element X does).
            PushQueue.add(context, roomId, eventId)
            FetchPushWorker.enqueue(context)
        } else if (unread == 0) {
            // Read on another device: nothing left to show.
            PushNotifications.clearAll(context)
        }
    }
}

/**
 * ntfy binds to this for a few seconds before it delivers a message. That lifts ElementCtrl to foreground
 * importance, so the fetch can start straight away with network access even while the phone is in deep sleep.
 */
class RaiseToForegroundService : Service() {
    private val binder = Binder()
    override fun onBind(intent: Intent?): IBinder = binder
}
