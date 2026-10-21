package com.vknxworks.elementctrl.notify

import android.content.Context
import android.util.Log
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.userMessage
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.HttpPusherData
import org.matrix.rustcomponents.sdk.PushFormat
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

sealed interface PushStatus {
    data object Off : PushStatus
    data object Registering : PushStatus
    data class Active(val gateway: String) : PushStatus
    /** ntfy's server is down for now; the endpoint is kept and ntfy says when it's back. */
    data object Unavailable : PushStatus
    /** Instant push is on, but ntfy isn't installed (any more). */
    data object NoNtfy : PushStatus
    data class Failed(val message: String) : PushStatus
}

/**
 * Instant notifications through ntfy: ntfy gives us an endpoint URL on your ntfy server, we register it as a Matrix
 * pusher with the homeserver, and the homeserver sends each new event's ID to the Matrix push gateway built into
 * that same ntfy server (/_matrix/push/v1/notify), which passes it to ntfy on the phone. Nothing goes through any
 * other server. The 15-minute background check keeps running alongside, as a safety net.
 */
object Push {
    private const val TAG = "Push"
    const val APP_ID = "com.vknxworks.elementctrl"
    private const val DESCRIPTION = "ElementCtrl"
    private const val GATEWAY_PATH = "/_matrix/push/v1/notify"
    /** The public gateway earlier versions fell back to. Only used to clean up a pusher that still points there. */
    private const val OLD_PUBLIC_GATEWAY = "https://matrix.gateway.unifiedpush.org/_matrix/push/v1/notify"

    private val _status = MutableStateFlow<PushStatus>(PushStatus.Off)
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences("push", Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun storedEndpoint(context: Context): String? = prefs(context).getString("endpoint", null)
    private fun storedGateway(context: Context): String? = prefs(context).getString("gateway", null)

    /** Turns instant push on. Returns false (and changes nothing) when ntfy isn't installed. */
    fun enable(context: Context): Boolean {
        if (!UnifiedPush.isNtfyInstalled(context)) {
            _status.value = PushStatus.NoNtfy
            return false
        }
        prefs(context).edit().putBoolean("enabled", true).remove("distributor").apply()
        register(context)
        return true
    }

    private fun register(context: Context) {
        if (_status.value !is PushStatus.Active) _status.value = PushStatus.Registering
        if (!UnifiedPush.register(context, DESCRIPTION)) {
            _status.value = PushStatus.NoNtfy
            return
        }
        // ntfy answers within a second or two. If it doesn't, say so instead of "Setting up…" forever.
        ElementCtrlApp.graph.scope.launch {
            delay(30_000)
            if (_status.value == PushStatus.Registering) {
                _status.value = PushStatus.Failed(Strings.str(R.string.ntfy_didn_t_answer_open_ntfy_once_so))
            }
        }
    }

    /**
     * Called at start-up once signed in, so a lost registration (ntfy reinstalled, server changed) is renewed. Also
     * takes over a registration from an earlier version: kept as it is with ntfy, removed with any other push app.
     */
    fun refresh(context: Context) {
        val previous = UnifiedPush.migrateFromLibrary(context)
        val p = prefs(context)
        if (!isEnabled(context)) return
        if (previous != null && previous != UnifiedPush.NTFY) {
            // Earlier versions let you pick another push app. Only ntfy is used now.
            Log.w(TAG, "moving push from another push app to ntfy")
            if (!UnifiedPush.isNtfyInstalled(context)) {
                ElementCtrlApp.graph.scope.launch { disable(context, waitForSession()) }
                return
            }
        }
        p.edit().remove("distributor").apply()
        if (!UnifiedPush.isNtfyInstalled(context)) {
            _status.value = PushStatus.NoNtfy
        } else {
            val endpoint = storedEndpoint(context)
            val gateway = storedGateway(context)
            val sameApp = previous == null || previous == UnifiedPush.NTFY
            if (sameApp && endpoint != null && gateway != null && gateway != OLD_PUBLIC_GATEWAY) _status.value = PushStatus.Active(gateway)
            register(context)
        }
        // Make sure the homeserver still has our pusher, now and twice a day.
        PusherCheckWorker.schedule(context)
        // Anything that arrived while the app couldn't fetch it.
        if (PushQueue.pending(context).isNotEmpty()) FetchPushWorker.enqueue(context)
    }

    /** Re-checks whether ntfy is (still) installed, e.g. when the Notifications settings come back on screen. */
    fun recheck(context: Context) {
        if (!isEnabled(context)) return
        val installed = UnifiedPush.isNtfyInstalled(context)
        when {
            !installed -> _status.value = PushStatus.NoNtfy
            _status.value == PushStatus.NoNtfy -> register(context)
        }
    }

    /** Asks ntfy for an endpoint again, e.g. after it lost our registration. */
    fun reregister(context: Context) {
        if (!isEnabled(context)) return
        register(context)
    }

    suspend fun disable(context: Context, session: MatrixSession?) {
        val endpoint = prefs(context).getString("endpoint", null)
        prefs(context).edit().clear().apply()
        UnifiedPush.unregister(context)
        PusherCheckWorker.cancel(context)
        FetchPushWorker.cancel(context)
        PushQueue.clear(context)
        if (endpoint != null && session != null) {
            withContext(Dispatchers.IO) { runCatching { session.client.deletePusher(PusherIdentifiers(endpoint, APP_ID)) } }
        }
        _status.value = PushStatus.Off
    }

    suspend fun onNewEndpoint(context: Context, endpoint: String) {
        if (!isEnabled(context)) return
        val session = waitForSession() ?: run {
            _status.value = PushStatus.Failed(Strings.str(R.string.not_signed_in))
            return
        }
        val old = storedEndpoint(context)
        val oldGateway = storedGateway(context)
        val gateway = when (val g = discoverGateway(endpoint)) {
            is Gateway.Found -> g.url
            is Gateway.Problem -> {
                if (old == endpoint && oldGateway != null && oldGateway != OLD_PUBLIC_GATEWAY) {
                    // Couldn't check right now (no network?), but this endpoint already works through its gateway.
                    Log.w(TAG, "gateway check failed, keeping the working pusher")
                    _status.value = PushStatus.Active(oldGateway)
                    return
                }
                // Nothing may go through any other gateway: remove a pusher left over from the public one.
                if (old != null) {
                    withContext(Dispatchers.IO) { runCatching { session.client.deletePusher(PusherIdentifiers(old, APP_ID)) } }
                }
                prefs(context).edit().remove("endpoint").remove("gateway").apply()
                _status.value = PushStatus.Failed(g.message)
                return
            }
        }
        runCatching {
            if (old != null && old != endpoint) runCatching { session.client.deletePusher(PusherIdentifiers(old, APP_ID)) }
            session.client.setPusher(
                identifiers = PusherIdentifiers(pushkey = endpoint, appId = APP_ID),
                kind = PusherKind.Http(HttpPusherData(url = gateway, format = PushFormat.EVENT_ID_ONLY, defaultPayload = null)),
                appDisplayName = "ElementCtrl",
                deviceDisplayName = ElementCtrlApp.graph.factory.deviceName,
                profileTag = null,
                lang = java.util.Locale.getDefault().toLanguageTag(),
                append = false,
            )
        }.onSuccess {
            prefs(context).edit().putString("endpoint", endpoint).putString("gateway", gateway).apply()
            _status.value = PushStatus.Active(gateway)
            PusherCheckWorker.schedule(context)
        }.onFailure {
            Log.w(TAG, "setPusher failed: ${it.javaClass.simpleName}")
            _status.value = PushStatus.Failed(it.userMessage().ifBlank { Strings.str(R.string.the_homeserver_refused_the_pusher) })
        }
    }

    fun onRegistrationFailed(context: Context, reason: String?) {
        // The next registration has to use a new token.
        UnifiedPush.dropToken(context)
        _status.value = PushStatus.Failed(
            when (reason) {
                "NETWORK" -> Strings.str(R.string.ntfy_couldn_t_reach_its_server)
                "ACTION_REQUIRED" -> Strings.str(R.string.ntfy_needs_your_attention_open_ntfy)
                "VAPID_REQUIRED" -> Strings.str(R.string.your_ntfy_server_requires_a_vapid)
                else -> Strings.str(R.string.ntfy_couldn_t_register_elementctrl, reason?.let { " ($it)" } ?: "")
            },
        )
    }

    fun onUnregistered(context: Context) {
        UnifiedPush.dropToken(context)
        prefs(context).edit().remove("endpoint").apply()
        _status.value = PushStatus.Failed(Strings.str(R.string.ntfy_removed_elementctrl_s))
    }

    fun onTempUnavailable() {
        _status.value = PushStatus.Unavailable
    }

    /** A push came through, so ntfy's server is reachable again. */
    fun onMessageReceived(context: Context) {
        if (_status.value == PushStatus.Unavailable) {
            _status.value = storedGateway(context)?.let { PushStatus.Active(it) } ?: PushStatus.Registering
        }
    }

    private sealed interface Gateway {
        data class Found(val url: String) : Gateway
        data class Problem(val message: String) : Gateway
    }

    /** ntfy servers run a Matrix push gateway on the same host as the endpoint. Only that one is ever used. */
    private suspend fun discoverGateway(endpoint: String): Gateway = withContext(Dispatchers.IO) {
        val url = runCatching { URL(endpoint) }.getOrNull()
            ?: return@withContext Gateway.Problem(Strings.str(R.string.ntfy_gave_an_endpoint_that_isn_t_a))
        val port = if (url.port != -1) ":${url.port}" else ""
        val server = "${url.protocol}://${url.host}$port"
        val candidate = "$server$GATEWAY_PATH"
        runCatching {
            val conn = (URL(candidate).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            try {
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.use { com.vknxworks.elementctrl.matrix.MatrixHttp.readCapped(it, 64 * 1024) }
                    if (JSONObject(body).optJSONObject("unifiedpush")?.optString("gateway") == "matrix") Gateway.Found(candidate) else null
                } else null
            } finally {
                conn.disconnect()
            }
        }.fold(
            onSuccess = {
                it ?: Gateway.Problem(
                    Strings.str(R.string.ntfy_no_gateway, server, GATEWAY_PATH),
                )
            },
            onFailure = { Gateway.Problem(Strings.str(R.string.couldn_t_reach_your_ntfy_server_to, server)) },
        )
    }

    suspend fun waitForSession(timeoutMs: Long = 30_000): MatrixSession? {
        val graph = ElementCtrlApp.graph
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            graph.state.first { it !is com.vknxworks.elementctrl.matrix.AppState.Loading }
        }.let { (it as? com.vknxworks.elementctrl.matrix.AppState.SignedIn)?.session }
    }
}
