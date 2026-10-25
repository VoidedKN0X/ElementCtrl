package com.vknxworks.elementctrl.call

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vknxworks.elementctrl.BuildConfig
import com.vknxworks.elementctrl.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

/** Where the bundled Element Call is served from inside the WebView (never the network). */
internal const val CALL_ORIGIN = "https://appassets.androidplatform.net"
internal const val CALL_URL = "$CALL_ORIGIN/element-call/index.html"
private const val CALL_HOST = "appassets.androidplatform.net"

/**
 * Element Call's configuration, served in place of a config.json: nothing but defaults. No analytics (PostHog),
 * crash reporting (Sentry), rageshake or OpenTelemetry endpoint, and no fallback LiveKit service: media only goes
 * through the service your homeserver announces.
 */
private const val CALL_CONFIG = "{}"

/**
 * Serves the bundled Element Call files from the APK's assets, and keeps the WebView to the few hosts a call needs:
 * your homeserver and its domain, and the MatrixRTC (LiveKit) service it announces. Any other web request is
 * refused. (WebSocket and WebRTC media connections, to the LiveKit server and the TURN servers it hands out, don't
 * go through here.)
 */
internal class CallWebClient(
    private val context: Context,
    private val allowedDomains: Set<String>,
    private val pageStarted: (WebView) -> Unit,
    private val pageFinished: (WebView) -> Unit,
    private val onError: (String?) -> Unit,
) : WebViewClient() {

    private fun allowed(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return allowedDomains.any { d -> h == d || h.endsWith(".$d") }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url
        if (url.host == CALL_HOST && url.scheme == "https") return asset(url)
        // data: and blob: URLs the page makes itself stay inside the WebView.
        if (url.scheme != "https" && url.scheme != "http") return null
        if (allowed(url.host)) return null
        return blocked()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
        // The call page never navigates anywhere else.
        !(request.url.host == CALL_HOST && request.url.scheme == "https")

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        pageStarted(view)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        pageFinished(view)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
        if (request?.isForMainFrame == true) onError(error?.description?.toString())
    }

    private fun blocked() = WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun asset(url: Uri): WebResourceResponse {
        val path = url.path.orEmpty().removePrefix("/")
        if (!path.startsWith("element-call/") || path.contains("..")) return notFound()
        if (path == "element-call/config.json") {
            return WebResourceResponse("application/json", "utf-8", 200, "OK", noCache(), ByteArrayInputStream(CALL_CONFIG.toByteArray()))
        }
        return runCatching {
            WebResourceResponse(mimeType(path), if (isText(path)) "utf-8" else null, 200, "OK", noCache(), context.assets.open(path))
        }.getOrElse { notFound() }
    }

    private fun noCache() = mapOf("Cache-Control" to "no-store")

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    private fun isText(path: String) = path.substringAfterLast('.') in setOf("html", "js", "mjs", "css", "json", "svg", "txt")

    private fun mimeType(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "wasm" -> "application/wasm"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "ttf" -> "font/ttf"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "webm" -> "video/webm"
        else -> "application/octet-stream"
    }
}

/**
 * The bridge between Element Call (the widget) and the Rust SDK's widget driver: postMessage traffic from the page
 * goes to [onFromWidget], and [toWidget] posts the driver's messages back into the page.
 */
internal class WidgetBridge(private val webView: WebView, private val onFromWidget: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    init {
        webView.addJavascriptInterface(this, NAME)
    }

    @JavascriptInterface
    fun postMessage(json: String?) {
        if (json != null && json.length < 4_000_000) onFromWidget(json)
    }

    /** Listens for the widget's requests (and its replies to us) inside the page. Run when the page starts loading. */
    fun install() {
        webView.evaluateJavascript(
            """
            (function() {
              if (window.__elementCtrlBridge) return;
              window.__elementCtrlBridge = true;
              window.addEventListener('message', function(event) {
                var d = event.data;
                if (!d || typeof d !== 'object') return;
                if ((d.response && d.api == 'toWidget') || (!d.response && d.api == 'fromWidget')) {
                  $NAME.postMessage(JSON.stringify(d));
                }
              });
            })();
            """.trimIndent(),
            null,
        )
    }

    /** Sends a message from the widget driver to Element Call. [json] is the driver's JSON object. */
    fun toWidget(json: String) {
        // Passed in as a quoted string and parsed in the page, so the driver's message can only ever be data there,
        // never code (it carries other people's call events and the call's encryption keys).
        val quoted = JSONObject.quote(json)
        main.post { runCatching { webView.evaluateJavascript("window.postMessage(JSON.parse($quoted), '$CALL_ORIGIN');", null) } }
    }

    companion object {
        private const val NAME = "elementCtrlWidget"
    }
}

/** The bits of the widget API messages the app itself acts on. */
internal object WidgetMessages {
    data class Parsed(val api: String, val action: String, val isResponse: Boolean)

    fun parse(json: String): Parsed? = runCatching {
        val o = JSONObject(json)
        Parsed(o.optString("api"), o.optString("action"), o.has("response"))
    }.getOrNull()

    fun hangUp(widgetId: String): String = JSONObject()
        .put("api", "toWidget")
        .put("widgetId", widgetId)
        .put("requestId", "widgetapi-${System.currentTimeMillis()}")
        .put("action", "im.vector.hangup")
        .put("data", JSONObject())
        .toString()
}

/**
 * Speaker, earpiece, wired and Bluetooth audio for the call. Element Call shows the device picker
 * (controlledAudioDevices) and the phone routes the call's audio: this lists the phone's communication devices for
 * it, switches to the one picked, puts the phone in call mode so the volume keys control the call, and turns the
 * screen off with the proximity sensor while the earpiece is used.
 */
/** One choice in the call's audio output picker. */
internal data class AudioOutput(val id: String, val name: String, val kind: Kind, val selected: Boolean) {
    enum class Kind { EARPIECE, SPEAKER, BLUETOOTH, WIRED, USB, OTHER }
}

internal class CallAudio(
    private val context: Context,
    private val webView: WebView,
    /** Shows the app's audio output picker (from the button in the call's bar). */
    private val onShowPicker: (List<AudioOutput>) -> Unit = {},
) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var started = false
    /** The call has ended: the page finishing a load (about:blank on close) must not turn call mode back on. */
    private var ended = false
    private var registered = false
    private var wanted: String? = null
    private val proximity: PowerManager.WakeLock? by lazy {
        context.getSystemService(PowerManager::class.java)
            ?.takeIf { it.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) }
            ?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "${context.packageName}:CallProximity")
    }

    private val types = listOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    )

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            // Android's list of communication devices can lag behind this callback: add the new ones ourselves,
            // so earphones connected during the call are offered (and picked) straight away.
            val added = addedDevices.orEmpty().filter { it.type in types && it.isSink }
            if (added.isNotEmpty()) log("added: ${added.joinToString { describe(it) }}")
            publish(extra = added)
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val removed = removedDevices.orEmpty().filter { it.type in types && it.isSink }
            if (removed.isNotEmpty()) log("removed: ${removed.joinToString { describe(it) }}")
            publish()
        }
    }

    /** Runs the communication-device listener; one per call, shut down when the call ends. */
    private val listenerExecutor = Executors.newSingleThreadExecutor()

    private val commsListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
        log("now playing through ${device?.let { describe(it) } ?: "nothing"}")
        // Keep the device picked in the call, even if something else switched it.
        val w = wanted
        if (w != null && device?.id?.toString() != w) main.post { if (started) select(w) }
    }

    private fun log(message: String) = com.vknxworks.elementctrl.matrix.ConnectionLog.log("call audio: $message")

    private fun describe(d: AudioDeviceInfo): String = "${typeName(d.type)}#${d.id}"

    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth-call"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth-media"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "bluetooth-le"
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
        else -> "type$type"
    }

    private fun isBluetooth(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.type == AudioDeviceInfo.TYPE_BLE_HEADSET

    init {
        webView.addJavascriptInterface(this, "elementCtrlAudio")
    }

    @JavascriptInterface
    fun setAudioDevice(id: String?) {
        if (id != null) main.post {
            if (!started) return@post
            log("Element Call picked #$id")
            wanted = id
            select(id)
        }
    }

    /** The audio output button in the call's bar was tapped. */
    @JavascriptInterface
    fun showPicker() {
        main.post { if (started) onShowPicker(outputs()) }
    }

    /** What the picker lists: the same devices Element Call gets, with the one in use marked. */
    fun outputs(): List<AudioOutput> {
        val current = audio.communicationDevice?.id?.toString() ?: wanted
        return devices().map { d ->
            AudioOutput(
                id = d.id.toString(),
                name = name(d),
                kind = when (d.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioOutput.Kind.EARPIECE
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutput.Kind.SPEAKER
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioOutput.Kind.BLUETOOTH
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioOutput.Kind.WIRED
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> AudioOutput.Kind.USB
                    else -> AudioOutput.Kind.OTHER
                },
                selected = d.id.toString() == current,
            )
        }
    }

    /** Picked in the app's picker: switch now, and tell Element Call so its own menu agrees. */
    fun choose(id: String) {
        log("picked #$id in the app's picker")
        wanted = id
        select(id)
        val safe = JSONObject.quote(id)
        webView.evaluateJavascript("if (window.controls && controls.setAudioDevice) controls.setAudioDevice($safe);", null)
    }

    @JavascriptInterface
    fun onTrackReady() {
        // Element Call's audio has started: give the WebView a moment, then offer the devices.
        main.postDelayed({
            if (!started) return@postDelayed
            publish()
            if (!registered) {
                audio.registerAudioDeviceCallback(deviceCallback, main)
                audio.addOnCommunicationDeviceChangedListener(listenerExecutor, commsListener)
                registered = true
            }
        }, 2_000)
    }

    /** The page has loaded: call mode on, and Element Call's audio hooks pointed at this class. */
    fun start() {
        if (started || ended) return
        started = true
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        log("call started, mode ${audio.mode}, devices: ${devices().joinToString { describe(it) }}")
        webView.evaluateJavascript(
            "if (window.controls) { controls.onAudioPlaybackStarted = () => { elementCtrlAudio.onTrackReady(); };" +
                " controls.onAudioDeviceSelect = (id) => { elementCtrlAudio.setAudioDevice(id); };" +
                // The audio output button in the call's bar opens the app's own picker.
                " controls.showNativeAudioDevicePicker = () => { elementCtrlAudio.showPicker(); }; }",
            null,
        )
    }

    fun stop() {
        ended = true
        if (!started) {
            listenerExecutor.shutdown()
            return
        }
        started = false
        wanted = null
        // Delayed Bluetooth re-checks and device switches posted during the call mustn't run after it.
        main.removeCallbacksAndMessages(null)
        runCatching { if (proximity?.isHeld == true) proximity?.release() }
        if (registered) {
            runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
            runCatching { audio.removeOnCommunicationDeviceChangedListener(commsListener) }
            registered = false
        }
        runCatching { audio.clearCommunicationDevice() }
        runCatching { audio.mode = AudioManager.MODE_NORMAL }
        listenerExecutor.shutdown()
    }

    private fun devices(extra: List<AudioDeviceInfo> = emptyList()): List<AudioDeviceInfo> {
        val all = (audio.availableCommunicationDevices + extra)
            .filter { it.type in types && it.isSink }
            .distinctBy { it.id }
            .sortedBy { types.indexOf(it.type) }
        // Earphones can show up twice, as a classic Bluetooth call device and as a Bluetooth LE Audio device. Offer
        // them once: the classic one (it works with every phone), the LE one only when that's all there is.
        val classicNames = all.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }.map { it.productName?.toString().orEmpty() }.toSet()
        val listed = all.filterNot { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET && it.productName?.toString().orEmpty() in classicNames }
        // Without "Nearby devices" the call's audio engine won't use Bluetooth and keeps pulling the call back to the
        // phone; offering the earphones then only makes the call go silent. Leave them out in that case.
        val bluetoothAllowed = context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        return if (bluetoothAllowed) listed else listed.filterNot { isBluetooth(it) }
    }

    private fun name(d: AudioDeviceInfo): String {
        val kind = context.getString(
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET -> R.string.audio_bluetooth
                AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> R.string.audio_usb
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> R.string.audio_wired
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.string.audio_speaker
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> R.string.audio_earpiece
                else -> R.string.audio_other
            },
        )
        val builtIn = d.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || d.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        val product = d.productName?.toString().orEmpty()
        return if (builtIn || product.isBlank()) kind else "$kind - $product"
    }

    private fun publish(extra: List<AudioDeviceInfo> = emptyList()) {
        if (!started) return
        val list = JSONArray()
        devices(extra).forEach { d ->
            list.put(
                JSONObject()
                    .put("id", d.id.toString())
                    .put("name", name(d))
                    .put("isEarpiece", d.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                    .put("isSpeaker", d.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                    .put("isExternalHeadset", d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.type == AudioDeviceInfo.TYPE_BLE_HEADSET),
            )
        }
        webView.evaluateJavascript("if (window.controls) controls.setAvailableAudioDevices($list);", null)
    }

    @SuppressLint("WakelockTimeout")
    private fun select(id: String) {
        if (!started) return
        val available = audio.availableCommunicationDevices
        // If the picked device isn't there any more (Bluetooth earphones re-announce themselves with a new ID when
        // they switch between music and call mode), use the best one there is.
        var device: AudioDeviceInfo = available.firstOrNull { it.id.toString() == id }
            ?: run {
                log("picked #$id is gone; available: ${available.joinToString { describe(it) }}")
                devices().firstOrNull()
            }
            ?: return
        val ok = runCatching { audio.setCommunicationDevice(device) }.getOrDefault(false)
        log("switch to ${describe(device)}: ${if (ok) "accepted" else "REFUSED"}")
        if (!ok && isBluetooth(device)) {
            // Android refused this Bluetooth route: try the earphones' other Bluetooth mode, then give up on Bluetooth
            // rather than leave the call silent.
            val other = devices().firstOrNull { isBluetooth(it) && it.id != device.id }
            val fallback = other ?: devices().firstOrNull { !isBluetooth(it) }
            if (fallback != null) {
                val ok2 = runCatching { audio.setCommunicationDevice(fallback) }.getOrDefault(false)
                log("fallback to ${describe(fallback)}: ${if (ok2) "accepted" else "REFUSED"}")
                if (ok2) { wanted = fallback.id.toString(); device = fallback }
            }
        }
        if (isBluetooth(device)) {
            // Bluetooth call audio takes a moment to connect. Check it really did, and try once more if not.
            val target = device
            main.postDelayed({
                if (!started) return@postDelayed
                val now = audio.communicationDevice
                if (now?.id != target.id) {
                    log("after 2 s still on ${now?.let { describe(it) } ?: "nothing"}, asking again for ${describe(target)}")
                    val again = runCatching { audio.setCommunicationDevice(target) }.getOrDefault(false)
                    log("second try: ${if (again) "accepted" else "REFUSED"}")
                }
            }, 2_000)
        }
        runCatching {
            val p = proximity ?: return@runCatching
            if (device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
                if (!p.isHeld) p.acquire()
            } else if (p.isHeld) {
                p.release()
            }
        }
    }
}

/** Element Call's back button and picture-in-picture hooks. */
internal class CallControls(
    private val webView: WebView,
    private val onBack: () -> Unit,
    private val onPipOrientation: (portrait: Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    init {
        webView.addJavascriptInterface(this, "elementCtrlControls")
    }

    @JavascriptInterface
    fun onBackPressed() {
        main.post(onBack)
    }

    @JavascriptInterface
    fun setPipOrientation(orientation: String?) {
        when (orientation) {
            "portrait" -> main.post { onPipOrientation(true) }
            "landscape" -> main.post { onPipOrientation(false) }
        }
    }

    fun install() {
        webView.evaluateJavascript(
            "if (window.controls) { controls.onBackButtonPressed = () => { elementCtrlControls.onBackPressed(); };" +
                " controls.onPipMediaOrientationUpdate = (o) => { elementCtrlControls.setPipOrientation(o); }; }",
            null,
        )
    }

    fun setPip(on: Boolean) {
        webView.evaluateJavascript("try { if (window.controls) { ${if (on) "controls.enablePip()" else "controls.disablePip()"}; } } catch (e) {}", null)
    }
}

/** Settings for the call WebView: JavaScript and camera/microphone for the call, no file access, no debugging. */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.setUpForCall() {
    with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        // The call page is https only: never load http content into it.
        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        databaseEnabled = true
        setGeolocationEnabled(false)
        safeBrowsingEnabled = false
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
    }
    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
}
