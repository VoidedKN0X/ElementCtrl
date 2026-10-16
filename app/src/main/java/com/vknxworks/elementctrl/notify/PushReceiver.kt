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

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        // Homeservers send {"notification":{"event_id":…,"room_id":…,"counts":{"unread":…}}}.
        val n = runCatching { JSONObject(String(message.content)).optJSONObject("notification") }.getOrNull() ?: return
        val roomId = n.optString("room_id").takeIf { it.isNotBlank() }
        val eventId = n.optString("event_id").takeIf { it.isNotBlank() }
        val unread = n.optJSONObject("counts")?.optInt("unread", -1) ?: -1
        val pending = goAsync()
        ElementCtrlApp.graph.scope.launch {
            try {
                if (roomId != null && eventId != null) {
                    PushNotifications.handle(context, roomId, eventId)
                } else if (unread == 0) {
                    // Read on another device: nothing left to show.
                    PushNotifications.clearAll(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        val pending = goAsync()
        ElementCtrlApp.graph.scope.launch {
            try { Push.onNewEndpoint(context, endpoint.url) } finally { pending.finish() }
        }
    }

    override fun onRegistrationFailed(context: Context, reason: FailedReason, instance: String) {
        Push.onRegistrationFailed(reason.toString())
    }

    override fun onUnregistered(context: Context, instance: String) {
        Push.onUnregistered(context)
    }
}
