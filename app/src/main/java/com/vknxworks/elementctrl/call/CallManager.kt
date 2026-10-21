package com.vknxworks.elementctrl.call

import android.content.Context
import android.content.Intent
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.matrix.MatrixSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.RtcTransport

/** Someone is calling: what the ringing notification and screen show. */
data class IncomingCall(
    val roomId: String,
    /** The m.rtc.notification event that rings; declining refers to it. */
    val eventId: String,
    val senderId: String,
    val senderName: String,
    val roomName: String,
    val isDm: Boolean,
    val audioOnly: Boolean,
    val avatarUrl: String?,
    /** When the caller's ringing stops (from the event), in epoch millis. */
    val expiresAt: Long,
)

/** The call this phone is in (or joining): one at a time. */
data class ActiveCall(val roomId: String, val audioOnly: Boolean, val roomName: String)

/**
 * The app's call state: the one ringing call and the one ongoing call. While either exists the session keeps
 * syncing in the background (call membership and the encryption keys of the call arrive through sync), and the
 * app lock doesn't shut the session down until the call has ended.
 */
object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _ringing = MutableStateFlow<IncomingCall?>(null)
    val ringing: StateFlow<IncomingCall?> = _ringing.asStateFlow()

    private val _active = MutableStateFlow<ActiveCall?>(null)
    val active: StateFlow<ActiveCall?> = _active.asStateFlow()

    /** Asks the call screen to hang up (the ongoing-call notification's Hang up button). */
    private val _hangUpRequests = MutableStateFlow(0)
    val hangUpRequests: StateFlow<Int> = _hangUpRequests.asStateFlow()

    val inCall: Boolean get() = _active.value != null

    /** Set when the app locked during a call: the session is shut down (sealed) once the call ends. */
    @Volatile var sealWhenCallEnds = false

    private var syncHold: Job? = null

    private fun holdSync(session: MatrixSession?) {
        if (syncHold?.isActive == true || session == null) return
        syncHold = scope.launch { session.withBackgroundSync { awaitCancellation() } }
    }

    private fun releaseSyncIfIdle() {
        if (_active.value == null && _ringing.value == null) {
            syncHold?.cancel()
            syncHold = null
        }
    }

    // ------------------------------------------------------------------ outgoing / joining

    /** Opens the call screen to start or join the call in [roomId]. */
    fun open(context: Context, roomId: String, audioOnly: Boolean, answer: Boolean = false) {
        context.startActivity(
            Intent(context, CallActivity::class.java)
                .putExtra(CallActivity.EXTRA_ROOM_ID, roomId)
                .putExtra(CallActivity.EXTRA_AUDIO_ONLY, audioOnly)
                .putExtra(CallActivity.EXTRA_MODE, if (answer) CallActivity.MODE_ANSWER else CallActivity.MODE_START)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Brings the current call screen back to the front. */
    fun reopen(context: Context) {
        context.startActivity(Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun onJoining(call: ActiveCall) {
        _active.value = call
        val r = _ringing.value
        if (r != null && r.roomId == call.roomId) stopRinging(ElementCtrlApp.instance, missed = false)
        holdSync(ElementCtrlApp.graph.session)
    }

    fun onEnded(context: Context) {
        _active.value = null
        CallService.stop(context)
        releaseSyncIfIdle()
        if (sealWhenCallEnds) {
            sealWhenCallEnds = false
            // The app locked while you were in the call: shut the session down now, as it would have been.
            if (!com.vknxworks.elementctrl.ui.lock.AppLock.unlocked) ElementCtrlApp.graph.onLocked()
        }
    }

    fun requestHangUp() {
        _hangUpRequests.value++
    }

    // ------------------------------------------------------------------ incoming

    /**
     * An incoming call to ring for. Ignored (shown as missed) when another call is ringing or you're in a call.
     * Rings until it expires (at most 90 seconds), the caller hangs up, you answer or decline here or on another of
     * your devices.
     */
    fun ring(context: Context, session: MatrixSession, call: IncomingCall) {
        val now = System.currentTimeMillis()
        val ringFor = minOf(call.expiresAt - now, MAX_RING_MS)
        if (ringFor <= 0) return
        if (_active.value != null || (_ringing.value != null && _ringing.value?.eventId != call.eventId)) {
            CallNotifications.missed(context, call)
            return
        }
        if (_ringing.value?.eventId == call.eventId) return
        _ringing.value = call
        holdSync(session)
        CallNotifications.ring(context, call)
        RingWatcher.watch(context, session, call, ringFor)
    }

    /** Stops ringing for [eventId] (or whatever rings), optionally leaving a "Missed call" notification. */
    fun stopRinging(context: Context, missed: Boolean, eventId: String? = null) {
        val call = _ringing.value ?: return
        if (eventId != null && call.eventId != eventId) return
        _ringing.value = null
        RingWatcher.cancel()
        CallNotifications.cancelRinging(context)
        if (missed) CallNotifications.missed(context, call)
        releaseSyncIfIdle()
    }

    /** Declines the ringing call: tells the caller's devices (and your other devices) to stop ringing. */
    fun decline(context: Context) {
        val call = _ringing.value ?: return
        stopRinging(context, missed = false)
        ElementCtrlApp.graph.scope.launch(Dispatchers.IO) {
            val session = com.vknxworks.elementctrl.notify.Push.waitForSession() ?: return@launch
            runCatching { session.getRoom(call.roomId)?.use { it.declineCall(call.eventId) } }
        }
    }

    private const val MAX_RING_MS = 90_000L
}

/** What the homeserver offers for calls: the LiveKit services it announces (MSC4143), or none. */
object CallSupport {
    sealed interface Result {
        data class Ready(val serviceUrls: List<String>) : Result
        data object NotSetUp : Result
        data class Failed(val message: String) : Result
    }

    @Volatile private var cached: Pair<String, Result.Ready>? = null

    /**
     * Asks the homeserver (`/_matrix/client/v1/rtc/transports`, falling back to `.well-known` `rtc_foci`) through
     * the SDK. Only LiveKit services announced by your own server are ever used; there is no fallback service.
     */
    suspend fun check(session: MatrixSession): Result {
        cached?.let { (user, r) -> if (user == session.userId) return r }
        return runCatching {
            val urls = session.client.discoverRtcTransports().orEmpty()
                .filterIsInstance<RtcTransport.LiveKit>()
                .map { it.serviceUrl }
                .filter { it.startsWith("https://") }
            if (urls.isEmpty()) Result.NotSetUp else Result.Ready(urls).also { cached = session.userId to it }
        }.getOrElse { Result.Failed(it.message ?: it.javaClass.simpleName) }
    }

    fun forget() {
        cached = null
    }
}
