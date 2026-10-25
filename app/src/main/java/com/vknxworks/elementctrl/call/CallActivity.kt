package com.vknxworks.elementctrl.call

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.AppState
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.isOneToOne
import com.vknxworks.elementctrl.ui.Avatar
import com.vknxworks.elementctrl.ui.ElementCtrlTheme
import com.vknxworks.elementctrl.ui.WindowPrivacy
import com.vknxworks.elementctrl.ui.lock.AppLock
import com.vknxworks.elementctrl.ui.lock.AppLockOverlay
import com.vknxworks.elementctrl.ui.openUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.matrix.rustcomponents.sdk.ClientProperties
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.WidgetCapabilities
import org.matrix.rustcomponents.sdk.WidgetCapabilitiesProvider
import org.matrix.rustcomponents.sdk.WidgetDriverAndHandle
import org.matrix.rustcomponents.sdk.generateWebviewUrl
import org.matrix.rustcomponents.sdk.getElementCallRequiredPermissions
import org.matrix.rustcomponents.sdk.makeWidgetDriver
import org.matrix.rustcomponents.sdk.newVirtualElementCallWidget
import uniffi.matrix_sdk.EncryptionSystem
import uniffi.matrix_sdk.VirtualElementCallWidgetConfig
import uniffi.matrix_sdk.VirtualElementCallWidgetProperties
import uniffi.matrix_sdk_base.EncryptionState
import java.util.Locale
import java.util.UUID
import uniffi.matrix_sdk.Intent as CallIntent

/**
 * The call screen: the ringing screen for an incoming call, then the bundled Element Call web app in the system
 * WebView, driven through the Rust SDK's widget driver (the same way Element X does it), with picture-in-picture.
 *
 * The shared app lock applies: answering or opening the call screen while ElementCtrl is locked shows the lock
 * screen first, a call that's already running keeps going while the app locks, and the call is never shown in
 * Recents while the lock is on.
 */
class CallActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ROOM_ID = "call_room_id"
        const val EXTRA_AUDIO_ONLY = "call_audio_only"
        const val EXTRA_MODE = "call_mode"
        const val MODE_START = "start"
        const val MODE_ANSWER = "answer"
        const val MODE_RINGING = "ringing"
        const val MODE_RESUME = "resume"

        private const val LOAD_TIMEOUT_MS = 30_000L
    }

    private sealed interface Phase {
        data object Idle : Phase
        data object Ringing : Phase
        data object Loading : Phase
        data object InCall : Phase
        data class NotSetUp(val server: String) : Phase
        data class Failed(val message: String) : Phase
    }

    private data class Target(val roomId: String, val audioOnly: Boolean)

    private var phase by mutableStateOf<Phase>(Phase.Idle)
    private var pendingJoin by mutableStateOf<Target?>(null)
    private var inPip by mutableStateOf(false)
    /** The audio output picker, while it's open. */
    private var outputPicker by mutableStateOf<List<AudioOutput>?>(null)
    private var webView: WebView? = null
    private var bridge: WidgetBridge? = null
    private var audio: CallAudio? = null
    private var controls: CallControls? = null
    private var driver: WidgetDriverAndHandle? = null
    private var room: Room? = null
    private var widgetId: String? = null
    private var loaded = false
    private var ending = false
    private var portraitPip = true
    private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timeoutJob: Job? = null

    private var permissionRequest: PermissionRequest? = null
    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val request = permissionRequest ?: return@registerForActivityResult
        permissionRequest = null
        val granted = request.resources.filter { res ->
            when (res) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> result[Manifest.permission.RECORD_AUDIO] == true || has(Manifest.permission.RECORD_AUDIO)
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> result[Manifest.permission.CAMERA] == true || has(Manifest.permission.CAMERA)
                else -> false
            }
        }
        if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
        // The ongoing-call service can now also say it uses the microphone or camera.
        if (loaded) CallService.start(this)
    }

    private fun has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * The call screen is a WebView, and its audio engine (Chromium's) only treats Bluetooth earphones as a call device
     * when the app holds the "Nearby devices" (BLUETOOTH_CONNECT) permission, checked once when it starts its audio.
     * Without it, it keeps moving the call back to the phone's speaker while ElementCtrl moves it to the earphones, and
     * the call ends up silent both ways. So it's asked for before the call loads; the call goes ahead either way.
     */
    private var afterBluetoothAnswer: (() -> Unit)? = null
    private val askBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        com.vknxworks.elementctrl.matrix.ConnectionLog.log("call audio: nearby devices permission ${if (granted) "granted" else "not granted"}")
        afterBluetoothAnswer?.invoke()
        afterBluetoothAnswer = null
    }

    private fun withBluetoothPermission(then: () -> Unit) {
        if (has(Manifest.permission.BLUETOOTH_CONNECT)) return then()
        afterBluetoothAnswer = then
        com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn()
        runCatching { askBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT) }.onFailure { afterBluetoothAnswer = null; then() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The call screen stays dark in both modes: light status and navigation bar icons over #08080A.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Dark in both modes (see setContent below): its window background too.
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF08080A.toInt()))
        super.onCreate(savedInstanceState)
        // Calls ring over the phone's lock screen, like a phone call. The app lock still applies on top.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Blank in Recents until the lock settings below have been applied.
        setRecentsScreenshotEnabled(false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })

        handle(intent)

        callScope.launch {
            CallManager.hangUpRequests.drop(1).collect { endCall() }
        }

        setContent {
            // The call screen is mostly video and Element Call's own dark interface, so it stays dark in light mode.
            ElementCtrlTheme(forceDark = true) {
                val graph = ElementCtrlApp.graph
                val lock = graph.lock
                val legacyLock = lock.legacyPinSet
                val locked = (legacyLock && !graph.vault.enabled) || (graph.vault.enabled && !AppLock.unlocked)
                val ringing by CallManager.ringing.collectAsStateWithLifecycle()
                WindowPrivacy(
                    this@CallActivity,
                    secure = lock.hideScreenshots || locked || AppLock.request != null,
                    // With the app lock on, the call never shows in Recents.
                    hideRecents = lock.hideRecents || legacyLock || graph.vault.enabled,
                )
                // Start the call once the app is unlocked.
                LaunchedEffect(pendingJoin, locked) {
                    val target = pendingJoin
                    if (target != null && !locked) {
                        pendingJoin = null
                        beginCall(target)
                    }
                }
                // The caller hung up (or you declined elsewhere) before you answered.
                LaunchedEffect(ringing, phase) {
                    if (phase == Phase.Ringing && ringing == null && pendingJoin == null) finishAndRemoveTask()
                }
                // While locked, nothing of the call can be read by accessibility services either.
                LaunchedEffect(locked) {
                    webView?.importantForAccessibility =
                        if (locked) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                }

                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    when (val p = phase) {
                        Phase.Idle -> Unit
                        Phase.Ringing -> ringing?.let { RingingScreen(it, locked) }
                        Phase.Loading -> Loading()
                        Phase.InCall -> webView?.let { wv -> AndroidView(factory = { wv }, modifier = Modifier.fillMaxSize()) }
                        is Phase.NotSetUp -> Message(
                            stringResource(R.string.call_not_set_up_title),
                            stringResource(R.string.call_not_set_up_body, p.server),
                            link = stringResource(R.string.call_not_set_up_link),
                        )
                        is Phase.Failed -> Message(stringResource(R.string.call_error_title), p.message)
                    }
                    // The audio output picker only belongs to a call on screen (not picture-in-picture).
                    LaunchedEffect(phase, inPip) { if (phase != Phase.InCall || inPip) outputPicker = null }
                    outputPicker?.let { options ->
                        AudioOutputSheet(
                            options,
                            onPick = { id -> audio?.choose(id); outputPicker = null },
                            onDismiss = { outputPicker = null },
                        )
                    }
                    val coverCall = phase != Phase.Ringing || pendingJoin != null
                    if (locked && inPip) {
                        LockedPip()
                    } else if (locked && coverCall) {
                        AppLockOverlay(legacyLock)
                    } else if (AppLock.request != null) {
                        AppLockOverlay(legacyLock)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_RESUME
        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID)
        val audioOnly = intent?.getBooleanExtra(EXTRA_AUDIO_ONLY, false) ?: false
        val active = CallManager.active.value
        when (mode) {
            MODE_START, MODE_ANSWER -> {
                if (roomId == null) return finishIfIdle()
                when {
                    active != null && active.roomId == roomId -> Unit // already in this call: just show it
                    active != null -> Toast.makeText(this, R.string.call_already_in_call, Toast.LENGTH_LONG).show()
                    phase == Phase.Idle || phase == Phase.Ringing || phase is Phase.Failed || phase is Phase.NotSetUp -> {
                        pendingJoin = Target(roomId, audioOnly)
                        if (phase != Phase.Ringing) phase = Phase.Loading
                    }
                }
            }
            MODE_RINGING -> {
                if (active == null && phase == Phase.Idle) {
                    if (CallManager.ringing.value == null) return finishIfIdle()
                    phase = Phase.Ringing
                }
            }
            else -> if (active == null && phase == Phase.Idle) {
                if (CallManager.ringing.value != null) phase = Phase.Ringing else finishIfIdle()
            }
        }
    }

    private fun finishIfIdle() {
        if (phase == Phase.Idle) finishAndRemoveTask()
    }

    // ------------------------------------------------------------------ starting the call

    private suspend fun waitForSession(): MatrixSession? = withTimeoutOrNull(30_000) {
        ElementCtrlApp.graph.state.first { it is AppState.SignedIn || it is AppState.SignedOut }
    }.let { (it as? AppState.SignedIn)?.session }

    private fun beginCall(target: Target) {
        phase = Phase.Loading
        callScope.launch {
            val session = waitForSession()
            if (session == null) {
                phase = Phase.Failed(getString(R.string.call_error_no_session))
                return@launch
            }
            val focusUrls = when (val support = withContext(Dispatchers.IO) { CallSupport.check(session) }) {
                is CallSupport.Result.Ready -> support.serviceUrls
                CallSupport.Result.NotSetUp -> {
                    CallManager.stopRinging(this@CallActivity, missed = false)
                    phase = Phase.NotSetUp(serverName(session))
                    return@launch
                }
                is CallSupport.Result.Failed -> {
                    phase = Phase.Failed(getString(R.string.call_check_failed, support.message))
                    return@launch
                }
            }
            val result = runCatching { withContext(Dispatchers.IO) { prepare(session, target) } }
            val prepared = result.getOrElse {
                phase = Phase.Failed(it.message ?: getString(R.string.call_error_generic))
                return@launch
            }
            CallManager.onJoining(ActiveCall(target.roomId, target.audioOnly, prepared.roomName))
            startWebView(session, prepared, focusUrls)
            phase = Phase.InCall
        }
    }

    private class Prepared(val url: String, val roomName: String)

    /** Builds the Element Call widget for the room and starts the SDK's widget driver for it. */
    private suspend fun prepare(session: MatrixSession, target: Target): Prepared {
        val r = session.getRoom(target.roomId) ?: error(getString(R.string.call_error_no_room))
        room = r
        val info = r.roomInfo()
        val encrypted = runCatching { r.latestEncryptionState() }.getOrNull() == EncryptionState.ENCRYPTED
        val isDm = info.isOneToOne()
        val hasCall = info.hasRoomCall
        val intent = when {
            isDm && hasCall -> if (target.audioOnly) CallIntent.JOIN_EXISTING_DM_VOICE else CallIntent.JOIN_EXISTING_DM
            hasCall -> CallIntent.JOIN_EXISTING
            isDm -> if (target.audioOnly) CallIntent.START_CALL_DM_VOICE else CallIntent.START_CALL_DM
            else -> CallIntent.START_CALL
        }
        val id = UUID.randomUUID().toString()
        widgetId = id
        val settings = newVirtualElementCallWidget(
            VirtualElementCallWidgetProperties(
                elementCallUrl = CALL_URL,
                widgetId = id,
                encryption = if (encrypted) EncryptionSystem.PerParticipantKeys else EncryptionSystem.Unencrypted,
                // No analytics, crash reporting or bug-report upload: all left empty.
                posthogUserId = null,
                posthogApiHost = null,
                posthogApiKey = null,
                rageshakeSubmitUrl = null,
                sentryDsn = null,
                sentryEnvironment = null,
            ),
            VirtualElementCallWidgetConfig(
                intent = intent,
                // The phone routes the audio (speaker, earpiece, Bluetooth); Element Call shows the picker.
                controlledAudioDevices = true,
            ),
        )
        val url = generateWebviewUrl(
            settings,
            r,
            ClientProperties(
                clientId = packageName,
                languageTag = Locale.getDefault().toLanguageTag(),
                theme = "dark",
            ),
        )
        val dh = makeWidgetDriver(settings)
        driver = dh
        val provider = object : WidgetCapabilitiesProvider {
            override suspend fun acquireCapabilities(capabilities: WidgetCapabilities): WidgetCapabilities =
                getElementCallRequiredPermissions(session.userId, session.deviceId)
        }
        callScope.launch(Dispatchers.IO) { runCatching { dh.driver.run(r, provider) } }
        callScope.launch(Dispatchers.IO) {
            while (isActive) {
                val msg = runCatching { dh.handle.recv() }.getOrNull() ?: break
                bridge?.toWidget(msg)
            }
        }
        // Element Call's own fallback STUN/TURN server for legacy calls stays off as well.
        return Prepared(url + "&allowIceFallback=false", info.displayName ?: target.roomId)
    }

    private fun serverName(session: MatrixSession) = session.userId.substringAfter(':', session.homeserver)

    private fun startWebView(session: MatrixSession, prepared: Prepared, focusUrls: List<String>) {
        val domains = buildSet {
            add(serverName(session).substringBefore(':').lowercase())
            runCatching { android.net.Uri.parse(session.homeserver).host }.getOrNull()?.let { add(it.lowercase()) }
            focusUrls.forEach { u -> runCatching { android.net.Uri.parse(u).host }.getOrNull()?.let { add(it.lowercase()) } }
        }
        val wv = WebView(this)
        wv.setUpForCall()
        wv.setBackgroundColor(android.graphics.Color.BLACK)
        val b = WidgetBridge(wv) { json -> onFromWidget(json) }
        bridge = b
        audio = CallAudio(this, wv, onShowPicker = { outputPicker = it })
        controls = CallControls(wv, onBack = { onBack() }, onPipOrientation = { portrait -> portraitPip = portrait; updatePip() })
        wv.webViewClient = CallWebClient(
            this,
            domains,
            pageStarted = { b.install() },
            pageFinished = {
                audio?.start()
                controls?.install()
            },
            onError = { msg -> if (!loaded) phase = Phase.Failed(msg ?: getString(R.string.call_error_generic)) },
        )
        wv.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.origin?.host != "appassets.androidplatform.net") return request.deny()
                val needed = request.resources.mapNotNull {
                    when (it) {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                        else -> null
                    }
                }
                if (needed.isEmpty()) return request.deny()
                if (needed.all { has(it) }) {
                    request.grant(request.resources.filter {
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    }.toTypedArray())
                    if (loaded) CallService.start(this@CallActivity)
                } else {
                    permissionRequest?.deny()
                    permissionRequest = request
                    askPermissions.launch(needed.toTypedArray())
                }
            }

            // Element Call's console output is never logged in release builds.
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean = true
        }
        webView = wv
        withBluetoothPermission {
            if (ending || isDestroyed) return@withBluetoothPermission
            wv.loadUrl(prepared.url)
            timeoutJob = callScope.launch {
                delay(LOAD_TIMEOUT_MS)
                if (!loaded && !ending) phase = Phase.Failed(getString(R.string.call_error_generic))
            }
        }
    }

    /** A message from Element Call: passed on to the widget driver; closing and loading are acted on here. */
    private fun onFromWidget(json: String) {
        runCatching { driver?.handle?.send(json) }
        val m = WidgetMessages.parse(json) ?: return
        if (m.api != "fromWidget" || m.isResponse) return
        when (m.action) {
            "content_loaded" -> runOnUiThread { onLoaded() }
            "io.element.close" -> runOnUiThread { cleanUpAndFinish() }
        }
    }

    private fun onLoaded() {
        if (loaded) return
        loaded = true
        timeoutJob?.cancel()
        CallService.start(this)
        updatePip()
    }

    // ------------------------------------------------------------------ picture-in-picture, back, ending

    private fun pipParams(): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(if (portraitPip) Rational(9, 16) else Rational(16, 9))
        .setAutoEnterEnabled(loaded && !ending)
        .build()

    private fun updatePip() {
        runCatching { setPictureInPictureParams(pipParams()) }
    }

    private fun onBack() {
        if (phase == Phase.InCall && loaded && !ending) {
            runCatching { enterPictureInPictureMode(pipParams()) }
        } else if (phase == Phase.InCall || phase == Phase.Loading) {
            endCall()
        } else {
            finishAndRemoveTask()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        controls?.setPip(isInPictureInPictureMode)
        val graph = ElementCtrlApp.graph
        if (isInPictureInPictureMode) {
            // The app stays "on screen" in picture-in-picture, so the app-wide check never sees it leave: count
            // this as leaving. "As soon as you leave" locks now (the call carries on, the small window shows the
            // lock); with a lock time it counts from now.
            AppLock.onBackground(graph.lockEnabled, graph.lock.lockTimeout.millis)
        } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // Opened full screen again: check the time away.
            AppLock.onForeground(graph.lockEnabled, graph.lock.lockTimeout.millis)
        }
        // The picture-in-picture window was closed (not expanded): that ends the call.
        if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) endCall()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Leaving for picture-in-picture: lock before the small window appears.
        if (phase == Phase.InCall && loaded && !ending) {
            val graph = ElementCtrlApp.graph
            AppLock.onBackground(graph.lockEnabled, graph.lock.lockTimeout.millis)
        }
    }

    /** Hangs up: tells Element Call to leave the call (so the others see you go), then closes. */
    private fun endCall() {
        if (ending) return
        val id = widgetId
        val b = bridge
        if (!loaded || id == null || b == null) return cleanUpAndFinish()
        ending = true
        updatePip()
        b.toWidget(WidgetMessages.hangUp(id))
        callScope.launch {
            // Element Call answers with io.element.close; don't wait for it for long.
            delay(2_500)
            cleanUpAndFinish()
        }
    }

    private var cleanedUp = false

    private fun cleanUp() {
        if (cleanedUp) return
        cleanedUp = true
        ending = true
        timeoutJob?.cancel()
        audio?.stop()
        runCatching { driver?.driver?.close() }
        runCatching { driver?.handle?.close() }
        driver = null
        runCatching { room?.close() }
        room = null
        webView?.let { wv ->
            runCatching {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            }
        }
        webView = null
        if (CallManager.active.value != null) CallManager.onEnded(applicationContext)
        callScope.cancel()
    }

    private fun cleanUpAndFinish() {
        cleanUp()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        cleanUp()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ screens

    @Composable
    private fun RingingScreen(call: IncomingCall, locked: Boolean) {
        val show = ElementCtrlApp.graph.lock.showNotificationContent
        val session = ElementCtrlApp.graph.session
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            if (show && session != null) {
                Avatar(call.senderName, call.avatarUrl, call.senderId, size = 120.dp)
            } else {
                Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(120.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (call.audioOnly) Icons.Outlined.Phone else Icons.Outlined.Videocam, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                if (show) call.senderName else stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            if (show && !call.isDm) {
                Text(call.roomName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (call.audioOnly) R.string.call_incoming_voice else R.string.call_incoming_video),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (locked) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.call_unlock_to_answer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(Icons.Outlined.CallEnd, stringResource(R.string.call_decline), Color(0xFFE5484D)) {
                    CallManager.decline(this@CallActivity)
                    finishAndRemoveTask()
                }
                RoundAction(if (call.audioOnly) Icons.Outlined.Call else Icons.Outlined.Videocam, stringResource(R.string.call_answer), Color(0xFF30A46C)) {
                    // With the app locked, the lock screen comes first; the call starts once unlocked.
                    pendingJoin = Target(call.roomId, call.audioOnly)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White),
            ) { Icon(icon, label, Modifier.size(32.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }

    @Composable
    private fun Loading() {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.call_starting), style = MaterialTheme.typography.bodyLarge)
        }
    }

    @Composable
    private fun LockedPip() {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.call_locked_pip), style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    @Composable
    private fun Message(title: String, body: String, link: String? = null) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            if (link != null) {
                com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton(onClick = { openUrl(this@CallActivity, CALLS_HELP_URL) }) { Text(link) }
                Spacer(Modifier.height(8.dp))
            }
            com.vknxworks.elementctrl.ui.components.CtrlButton(onClick = { cleanUpAndFinish() }) { Text(stringResource(R.string.close)) }
        }
    }
}

/** The README's section on what a server needs for calls. */
const val CALLS_HELP_URL = "https://github.com/VoidedKN0X/ElementCtrl#calls"
