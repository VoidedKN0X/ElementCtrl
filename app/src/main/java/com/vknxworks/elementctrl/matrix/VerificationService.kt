package com.vknxworks.elementctrl.matrix

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationData
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails

data class SasEmoji(val symbol: String, val description: String)

data class IncomingVerification(
    val senderId: String,
    val senderName: String,
    val flowId: String,
    val deviceId: String,
    val deviceName: String?,
)

/** Where an interactive (emoji) verification currently stands. */
sealed interface VerifyStep {
    data object Idle : VerifyStep
    /** We asked; waiting for the other device or person to accept. */
    data object Requested : VerifyStep
    /** Someone else asked us. */
    data class Incoming(val request: IncomingVerification) : VerifyStep
    data object Accepted : VerifyStep
    data object Starting : VerifyStep
    data class Compare(val emojis: List<SasEmoji>, val decimals: List<Int>) : VerifyStep
    data object Confirming : VerifyStep
    data object Done : VerifyStep
    data object Cancelled : VerifyStep
    data class Failed(val message: String = "Verification didn't complete.") : VerifyStep
}

/** Emoji (SAS) verification of this session against another one, or of another user. */
class VerificationService(private val session: MatrixSession) : SessionVerificationControllerDelegate {
    private val _step = MutableStateFlow<VerifyStep>(VerifyStep.Idle)
    val step: StateFlow<VerifyStep> = _step.asStateFlow()

    /** Who the current flow is with: null for our own other device. */
    var otherUserId: String? = null
        private set

    private var outgoing = false
    private val mutex = Mutex()
    private var controller: SessionVerificationController? = null

    private suspend fun controller(): SessionVerificationController = mutex.withLock {
        controller ?: run {
            session.client.encryption().waitForE2eeInitializationTasks()
            session.client.getSessionVerificationController().also {
                it.setDelegate(this)
                controller = it
            }
        }
    }

    /** Starts listening for incoming requests as soon as the session is up. */
    fun prepare() {
        session.scope.launch { runCatching { controller() } }
    }

    private suspend fun attempt(block: suspend (SessionVerificationController) -> Unit) {
        runCatching { withContext(NonCancellable) { block(controller()) } }
            .onFailure { _step.value = VerifyStep.Failed(it.userMessage()) }
    }

    suspend fun verifyThisDevice() {
        otherUserId = null
        outgoing = true
        _step.value = VerifyStep.Requested
        attempt { it.requestDeviceVerification() }
    }

    suspend fun verifyUser(userId: String) {
        otherUserId = userId
        outgoing = true
        _step.value = VerifyStep.Requested
        attempt { it.requestUserVerification(userId) }
    }

    suspend fun acceptIncoming() {
        outgoing = false
        _step.value = VerifyStep.Accepted
        attempt { it.acceptVerificationRequest() }
    }

    suspend fun startSas() {
        _step.value = VerifyStep.Starting
        attempt { it.startSasVerification() }
    }

    suspend fun confirmMatch() {
        _step.value = VerifyStep.Confirming
        attempt { it.approveVerification() }
    }

    suspend fun denyMatch() = attempt { it.declineVerification() }

    suspend fun cancel() {
        runCatching { controller?.cancelVerification() }
        _step.value = VerifyStep.Cancelled
    }

    fun reset() {
        _step.value = VerifyStep.Idle
        otherUserId = null
    }

    // SDK callbacks (background thread).

    override fun didReceiveVerificationRequest(details: SessionVerificationRequestDetails) {
        val incoming = IncomingVerification(
            senderId = details.senderProfile.userId,
            senderName = details.senderProfile.displayName ?: details.senderProfile.userId,
            flowId = details.flowId,
            deviceId = details.deviceId,
            deviceName = details.deviceDisplayName,
        )
        otherUserId = incoming.senderId.takeIf { it != session.userId }
        outgoing = false
        session.scope.launch {
            // Acknowledge straight away so the other side sees we're looking; the person still has to accept.
            runCatching { controller().acknowledgeVerificationRequest(incoming.senderId, incoming.flowId) }
            _step.value = VerifyStep.Incoming(incoming)
        }
    }

    override fun didAcceptVerificationRequest() {
        _step.value = VerifyStep.Accepted
        // When we asked, we're the side that kicks off the emoji comparison once the other side accepts.
        if (outgoing) session.scope.launch { startSas() }
    }

    override fun didStartSasVerification() {
        _step.value = VerifyStep.Starting
    }

    override fun didReceiveVerificationData(data: SessionVerificationData) {
        _step.value = when (data) {
            is SessionVerificationData.Emojis -> VerifyStep.Compare(
                emojis = data.emojis.map { SasEmoji(it.symbol(), it.description()) },
                decimals = emptyList(),
            )
            is SessionVerificationData.Decimals -> VerifyStep.Compare(emptyList(), data.values.map { it.toInt() })
        }
    }

    override fun didFail() {
        _step.value = VerifyStep.Failed()
    }

    override fun didCancel() {
        _step.value = VerifyStep.Cancelled
    }

    override fun didFinish() {
        _step.value = VerifyStep.Done
    }
}
