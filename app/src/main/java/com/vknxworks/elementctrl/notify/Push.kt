package com.vknxworks.elementctrl.notify

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.matrix.MatrixSession
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.HttpPusherData
import org.matrix.rustcomponents.sdk.PushFormat
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
import org.unifiedpush.android.connector.UnifiedPush

/** A UnifiedPush distributor app installed on the phone, such as ntfy. */
data class Distributor(val packageName: String, val label: String)

sealed interface PushStatus {
    data object Off : PushStatus
    data object Registering : PushStatus
    data class Active(val distributor: String, val gateway: String) : PushStatus
    data class Failed(val message: String) : PushStatus
}

/**
 * Instant notifications through UnifiedPush (ntfy, NextPush, …) instead of Google: the distributor gives us an
 * endpoint URL, we register it as a Matrix pusher with the homeserver, and the homeserver sends each new event
 * to that endpoint through a Matrix push gateway (ntfy has one built in).
 */
object Push {
    private const val TAG = "Push"
    private const val INSTANCE = "default"
    const val APP_ID = "com.vknxworks.elementctrl"
    /** Public gateway run by the UnifiedPush project, for distributors without their own. */
    private const val FALLBACK_GATEWAY = "https://matrix.gateway.unifiedpush.org/_matrix/push/v1/notify"

    private val _status = MutableStateFlow<PushStatus>(PushStatus.Off)
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences("push", Context.MODE_PRIVATE)

    fun distributors(context: Context): List<Distributor> =
        UnifiedPush.getDistributors(context).filter { it != context.packageName }.map { pkg ->
            val label = runCatching {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
            }.getOrDefault(pkg)
            Distributor(pkg, label)
        }

    fun isEnabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun selectedDistributor(context: Context): String? = prefs(context).getString("distributor", null)

    /** Picks a distributor and asks it for an endpoint; [onNewEndpoint] finishes the job. */
    fun enable(context: Context, distributor: Distributor) {
        prefs(context).edit().putBoolean("enabled", true).putString("distributor", distributor.packageName).apply()
        _status.value = PushStatus.Registering
        UnifiedPush.saveDistributor(context, distributor.packageName)
        UnifiedPush.register(context, INSTANCE, "ElementCtrl")
    }

    /** Called at start-up so a lost registration (distributor reinstalled, server changed) is renewed. */
    fun refresh(context: Context) {
        if (!isEnabled(context)) return
        val endpoint = prefs(context).getString("endpoint", null)
        val gateway = prefs(context).getString("gateway", null)
        val dist = selectedDistributor(context)
        if (endpoint != null && gateway != null && dist != null) _status.value = PushStatus.Active(labelOf(context, dist), gateway)
        runCatching { UnifiedPush.register(context, INSTANCE, "ElementCtrl") }
    }

    suspend fun disable(context: Context, session: MatrixSession?) {
        val endpoint = prefs(context).getString("endpoint", null)
        prefs(context).edit().clear().apply()
        runCatching { UnifiedPush.unregister(context, INSTANCE) }
        if (endpoint != null && session != null) {
            withContext(Dispatchers.IO) { runCatching { session.client.deletePusher(PusherIdentifiers(endpoint, APP_ID)) } }
        }
        _status.value = PushStatus.Off
    }

    private fun labelOf(context: Context, pkg: String) = distributors(context).firstOrNull { it.packageName == pkg }?.label ?: pkg

    suspend fun onNewEndpoint(context: Context, endpoint: String) {
        val gateway = discoverGateway(endpoint)
        val session = waitForSession() ?: run {
            _status.value = PushStatus.Failed("Not signed in")
            return
        }
        runCatching {
            val old = prefs(context).getString("endpoint", null)
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
            _status.value = PushStatus.Active(labelOf(context, selectedDistributor(context) ?: ""), gateway)
            // Push replaces the 15-minute background check.
            Notifier.schedule(context, false)
        }.onFailure {
            Log.w(TAG, "setPusher failed", it)
            _status.value = PushStatus.Failed(it.message ?: "The homeserver refused the pusher")
        }
    }

    fun onRegistrationFailed(reason: String) {
        _status.value = PushStatus.Failed("The push app couldn't register: $reason")
    }

    fun onUnregistered(context: Context) {
        prefs(context).edit().remove("endpoint").apply()
        _status.value = PushStatus.Failed("The push app unregistered ElementCtrl. Pick it again in Settings.")
    }

    /**
     * Distributors like ntfy run a Matrix gateway on the same host as the endpoint; ask it, and fall back to
     * the public UnifiedPush gateway otherwise.
     */
    private suspend fun discoverGateway(endpoint: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(endpoint)
            val port = if (url.port != -1) ":${url.port}" else ""
            val candidate = "${url.protocol}://${url.host}$port/_matrix/push/v1/notify"
            val conn = (URL(candidate).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
            }
            try {
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    if (JSONObject(body).optJSONObject("unifiedpush")?.optString("gateway") == "matrix") candidate else null
                } else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull() ?: FALLBACK_GATEWAY
    }

    suspend fun waitForSession(): MatrixSession? {
        val graph = ElementCtrlApp.graph
        return kotlinx.coroutines.withTimeoutOrNull(30_000) {
            graph.state.first { it !is com.vknxworks.elementctrl.matrix.AppState.Loading }
        }.let { (it as? com.vknxworks.elementctrl.matrix.AppState.SignedIn)?.session }
    }
}
