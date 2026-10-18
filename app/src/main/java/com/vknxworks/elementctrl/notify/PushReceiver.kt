package com.vknxworks.elementctrl.notify

import android.content.Context
import com.vknxworks.elementctrl.ElementCtrlApp
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/** Receives push messages and endpoint changes from the UnifiedPush distributor (ntfy). */
class PushReceiver : MessagingReceiver() {

    // The receiver has to be exported for distributors to reach it. The connector library only delivers messages
    // that carry the registration token it stored, and we additionally check the instance and the IDs; the
    // event itself is then fetched from the homeserver, so a forged push can't inject notification text.
    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        if (instance != Push.INSTANCE || !Push.isEnabled(context)) return
        // Homeservers send {"notification":{"event_id":…,"room_id":…,"counts":{"unread":…}}}.
        val n = runCatching { JSONObject(String(message.content)).optJSONObject("notification") }.getOrNull() ?: return
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

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        if (instance != Push.INSTANCE) return
        val pending = goAsync()
        ElementCtrlApp.graph.scope.launch {
            try { Push.onNewEndpoint(context, endpoint.url) } finally { pending.finish() }
        }
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        Push.onRegistrationFailed(reason.toString())
    }

    override fun onUnregistered(context: Context, instance: String) {
        if (instance != Push.INSTANCE) return
        Push.onUnregistered(context)
    }
}
