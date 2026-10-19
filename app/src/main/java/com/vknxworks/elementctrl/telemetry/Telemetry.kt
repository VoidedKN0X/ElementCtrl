package com.vknxworks.elementctrl.telemetry

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.vknxworks.elementctrl.BuildConfig
import com.vknxworks.elementctrl.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Opt-in, anonymous usage stats sent to the project's own self-hosted Aptabase server.
 *
 * Nothing is sent unless the user turned "Share anonymous usage stats" on, and builds without an
 * Aptabase key (anyone building from source) can't send anything at all.
 *
 * What an event contains: its name, a few fixed-value properties chosen in code (the kind of screen,
 * "text" or "voice"), the app version, the Android version, the phone model, the system language and a
 * random session ID that changes every launch or after an hour of inactivity.
 * Never sent: the homeserver, user IDs, room IDs or names, message text, search text, counts tied to a
 * room, or any persistent device or install ID. (Like any web request, the stats server sees the phone's
 * IP address; Aptabase uses it for a country lookup and doesn't store it.)
 */
class Telemetry(
    private val context: Context,
    private val prefs: Prefs,
    scope: CoroutineScope,
) {
    /** Whether this build can send stats at all (it has a key and an HTTPS server address). */
    val available: Boolean = BuildConfig.APTABASE_KEY.startsWith("A-SH-") && BuildConfig.APTABASE_HOST.startsWith("https://")

    // Declared before init: the coroutines started there can call track() right away.
    private val systemProps: JSONObject by lazy {
        JSONObject()
            .put("isDebug", BuildConfig.DEBUG)
            .put("osName", "Android")
            .put("osVersion", Build.VERSION.RELEASE ?: "")
            .put("locale", Locale.getDefault().language)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("appBuildNumber", BuildConfig.VERSION_CODE.toString())
            .put("sdkVersion", "elementctrl@${BuildConfig.VERSION_NAME}")
            .put("deviceModel", Build.MODEL ?: "")
    }

    private val queue = Channel<JSONObject>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var sessionId = newSessionId()
    @Volatile private var lastEventAt = 0L

    init {
        if (available) {
            scope.launch {
                // Settings are read synchronously at start-up, so the first value is the stored one.
                var wasOn = prefs.usageStats.value
                prefs.usageStats.collect { on ->
                    // Switched on in Settings or the first-run question: start a fresh session.
                    if (on && !wasOn) { resetSession(); trackOpened() }
                    wasOn = on
                }
            }
            // One event at a time, in order. Failures are dropped: stats are never stored or retried.
            scope.launch(Dispatchers.IO) {
                for (event in queue) send(event)
            }
        }
    }

    /** Called when the app's screen is opened (not when a push or background sync starts the process). */
    fun appOpened() {
        if (available) trackOpened()
    }

    private fun trackOpened() {
        val notifications = when {
            !prefs.notifications.value -> "off"
            com.vknxworks.elementctrl.notify.Push.isEnabled(context) -> "push"
            else -> "background_check"
        }
        track("app_opened", mapOf("form_factor" to formFactor(), "notifications" to notifications))
    }

    private val enabled: Boolean
        get() = available && prefs.usageStats.value

    /** Records an event if the user opted in. [props] values must be fixed strings or numbers from code. */
    fun track(name: String, props: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (now - lastEventAt > SESSION_TIMEOUT_MS) sessionId = newSessionId()
        lastEventAt = now
        val event = JSONObject()
            .put("timestamp", timestamp(now))
            .put("sessionId", sessionId)
            .put("eventName", name)
            .put("systemProps", systemProps)
            .put("props", JSONObject(props))
        queue.trySend(event)
    }

    /** A fresh session after opting back in, so stats from before and after aren't linked. */
    private fun resetSession() {
        sessionId = newSessionId()
        lastEventAt = 0L
    }

    // Its own plain connection: nothing shared with the Matrix client (no tokens, no cookies, no redirects).
    private fun send(event: JSONObject) {
        // The user may have opted out while this was queued.
        if (!enabled) return
        runCatching {
            val body = event.toString().toByteArray(Charsets.UTF_8)
            val conn = (URL("${BuildConfig.APTABASE_HOST.trimEnd('/')}/api/v0/event").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                useCaches = false
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("App-Key", BuildConfig.APTABASE_KEY)
                setRequestProperty("User-Agent", "ElementCtrl/${BuildConfig.VERSION_NAME} (Android)")
                setFixedLengthStreamingMode(body.size)
            }
            try {
                conn.outputStream.use { it.write(body) }
                conn.responseCode
            } finally {
                conn.disconnect()
            }
        }
    }

    /** "phone" or "tablet", from the screen's smallest width. */
    fun formFactor(): String {
        val sw = context.resources.configuration.smallestScreenWidthDp
        return if (sw != Configuration.SMALLEST_SCREEN_WIDTH_DP_UNDEFINED && sw >= 600) "tablet" else "phone"
    }

    private fun newSessionId(): String = UUID.randomUUID().toString()

    private fun timestamp(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))

    companion object {
        private const val TIMEOUT_MS = 10_000
        private val SESSION_TIMEOUT_MS = TimeUnit.HOURS.toMillis(1)

        /** The kind of thing sent, from its MIME type: never the file's name, size or contents. */
        fun attachmentKind(mime: String): String = if (mime.startsWith("image/")) "image" else "file"
    }
}
