package com.vknxworks.elementctrl.matrix

import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.CheckCodeSender
import org.matrix.rustcomponents.sdk.ContinuationMessageSender
import org.matrix.rustcomponents.sdk.GrantGeneratedQrLoginProgress
import org.matrix.rustcomponents.sdk.GrantGeneratedQrLoginProgressListener
import org.matrix.rustcomponents.sdk.GrantLoginWithQrCodeTask
import org.matrix.rustcomponents.sdk.GrantQrLoginProgress
import org.matrix.rustcomponents.sdk.GrantQrLoginProgressListener
import org.matrix.rustcomponents.sdk.QrCodeData
import org.matrix.rustcomponents.sdk.QrCodeDecodeException
import uniffi.matrix_sdk_crypto.QrCodeIntent

/** Where linking a new device from this signed-in one stands. */
sealed interface LinkStep {
    data object Idle : LinkStep
    data object Starting : LinkStep
    /** Show this QR code; the new device scans it. */
    data class ShowQr(val bytes: ByteArray) : LinkStep
    /** The new device scanned our code: type the two digits it shows. */
    data class EnterCheckCode(val sender: CheckCodeSender) : LinkStep
    /** We scanned the new device's code: it asks for these two digits. */
    data class ShowCheckCode(val code: String) : LinkStep
    /** Approve the sign-in on your account page, then continue. */
    data class Approve(val url: String, val continuation: ContinuationMessageSender) : LinkStep
    data object Syncing : LinkStep
    data object Done : LinkStep
}

/**
 * "Link new device" (MSC4108): signs a new phone or computer in to this account with a QR code, either by showing
 * a code here for the new device to scan or by scanning the code the new device shows. The new device gets this
 * account's encryption keys over the secure channel, so it's verified straight away.
 */
class LinkDeviceService(private val session: MatrixSession) {
    private val _step = MutableStateFlow<LinkStep>(LinkStep.Idle)
    val step: StateFlow<LinkStep> = _step.asStateFlow()
    @Volatile private var task: GrantLoginWithQrCodeTask? = null

    /** Whether the homeserver can sign devices in with a QR code (OAuth sign-in with MSC4108 turned on). */
    suspend fun supported(): Boolean = withContext(Dispatchers.IO) {
        runCatching { session.client.isLoginWithQrCodeSupported() }.getOrDefault(false)
    }

    /** Shows a QR code here for the new device to scan. Returns when the new device is signed in (or throws). */
    suspend fun showCode() = withContext(Dispatchers.IO) {
        _step.value = LinkStep.Starting
        try {
            val t = session.client.newGrantLoginWithQrCodeHandler().generate(object : GrantGeneratedQrLoginProgressListener {
                override fun onUpdate(state: GrantGeneratedQrLoginProgress) {
                    _step.value = when (state) {
                        GrantGeneratedQrLoginProgress.Starting -> LinkStep.Starting
                        is GrantGeneratedQrLoginProgress.QrReady -> LinkStep.ShowQr(state.qrCode.toBytes())
                        is GrantGeneratedQrLoginProgress.QrScanned -> LinkStep.EnterCheckCode(state.checkCodeSender)
                        is GrantGeneratedQrLoginProgress.WaitingForAuth -> LinkStep.Approve(state.verificationUri, state.continuationSender)
                        GrantGeneratedQrLoginProgress.SyncingSecrets -> LinkStep.Syncing
                        GrantGeneratedQrLoginProgress.Done -> LinkStep.Done
                    }
                }
            })
            task = t
            t.wait()
            _step.value = LinkStep.Done
        } catch (e: Throwable) {
            _step.value = LinkStep.Idle
            throw e
        } finally {
            task = null
        }
    }

    /** Uses the QR code a new device shows ("Sign in with QR code" there). Returns when it's signed in (or throws). */
    suspend fun scanCode(bytes: ByteArray) = withContext(Dispatchers.IO) {
        _step.value = LinkStep.Starting
        try {
            val data = try {
                QrCodeData.fromBytes(bytes)
            } catch (e: QrCodeDecodeException) {
                throw QrLoginProblem(Strings.str(R.string.qr_not_a_sign_in_code))
            }
            if (data.intent() != QrCodeIntent.LOGIN) throw QrLoginProblem(Strings.str(R.string.qr_wrong_side_existing_device))
            val t = session.client.newGrantLoginWithQrCodeHandler().scan(data, object : GrantQrLoginProgressListener {
                override fun onUpdate(state: GrantQrLoginProgress) {
                    _step.value = when (state) {
                        GrantQrLoginProgress.Starting -> LinkStep.Starting
                        is GrantQrLoginProgress.EstablishingSecureChannel -> LinkStep.ShowCheckCode(state.checkCodeString)
                        is GrantQrLoginProgress.WaitingForAuth -> LinkStep.Approve(state.verificationUri, state.continuationSender)
                        GrantQrLoginProgress.SyncingSecrets -> LinkStep.Syncing
                        GrantQrLoginProgress.Done -> LinkStep.Done
                    }
                }
            })
            task = t
            t.wait()
            _step.value = LinkStep.Done
        } catch (e: Throwable) {
            _step.value = LinkStep.Idle
            throw e
        } finally {
            task = null
        }
    }

    /** Sends the two digits the new device shows. */
    suspend fun sendCheckCode(sender: CheckCodeSender, code: Int) = withContext(Dispatchers.IO) {
        _step.value = LinkStep.Starting
        sender.send(code.toUByte())
    }

    /** The sign-in was approved in the browser: carry on. */
    suspend fun approved(continuation: ContinuationMessageSender) = withContext(Dispatchers.IO) {
        _step.value = LinkStep.Starting
        continuation.confirm()
    }

    fun cancel() {
        val s = _step.value
        if (s is LinkStep.Approve) {
            session.scope.launch { runCatching { s.continuation.cancel() } }
        }
        runCatching { task?.cancel() }
        _step.value = LinkStep.Idle
    }

    fun reset() {
        _step.value = LinkStep.Idle
    }
}
