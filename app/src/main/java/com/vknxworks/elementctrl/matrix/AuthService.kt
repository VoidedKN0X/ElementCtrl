package com.vknxworks.elementctrl.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.LoginWithQrCodeTask
import org.matrix.rustcomponents.sdk.QrCodeData
import org.matrix.rustcomponents.sdk.QrCodeDecodeException
import org.matrix.rustcomponents.sdk.QrLoginProgress
import org.matrix.rustcomponents.sdk.QrLoginProgressListener
import uniffi.matrix_sdk_crypto.QrCodeIntent
import org.matrix.rustcomponents.sdk.OAuthConfiguration
import org.matrix.rustcomponents.sdk.OAuthPrompt
import org.matrix.rustcomponents.sdk.SsoHandler
import uniffi.matrix_sdk.OAuthAuthorizationData
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

/** What a homeserver lets you do on the sign-in screen. */
data class ServerDetails(
    val address: String,
    val url: String,
    val supportsOAuth: Boolean,
    val supportsPassword: Boolean,
    val supportsSso: Boolean,
    val canRegister: Boolean,
)

/** Where signing in with a QR code from another device stands. */
sealed interface QrSignInStep {
    data object Idle : QrSignInStep
    /** Talking to the server and the other device. */
    data object Connecting : QrSignInStep
    /** A secure channel is up: type these two digits on the other device. */
    data class ShowCheckCode(val code: String) : QrSignInStep
    /** The other device is approving the sign-in; it should show the same [userCode]. */
    data class WaitingForApproval(val userCode: String) : QrSignInStep
    /** Signed in; fetching the encryption keys from the other device. */
    data object Syncing : QrSignInStep
}

/** A problem with QR sign-in or linking a device that already has a readable explanation. */
class QrLoginProblem(message: String) : Exception(message)

/**
 * Drives sign-in against one homeserver: discovery, password, OAuth (Matrix Authentication Service, used by
 * matrix.org) and legacy SSO. Holds a temporary client until sign-in finishes.
 */
class AuthService(private val graph: AppGraph) {
    private var client: Client? = null
    private var paths: SessionPaths? = null
    private var oauth: OAuthAuthorizationData? = null
    private var sso: SsoHandler? = null

    val redirectScheme get() = graph.redirectScheme
    private val oauthRedirect get() = "$redirectScheme:/"
    private val ssoRedirect get() = "$redirectScheme://sso"

    private val _qrStep = MutableStateFlow<QrSignInStep>(QrSignInStep.Idle)
    val qrStep: StateFlow<QrSignInStep> = _qrStep.asStateFlow()
    @Volatile private var qrTask: LoginWithQrCodeTask? = null

    internal val oauthConfig
        get() = OAuthConfiguration(
            clientName = "ElementCtrl",
            redirectUri = oauthRedirect,
            clientUri = "https://github.com/VoidedKN0X/ElementCtrl",
            logoUri = null,
            tosUri = null,
            policyUri = null,
            staticRegistrations = emptyMap(),
        )

    /** Looks the server up (with .well-known), checks it supports sliding sync and reads its sign-in options. */
    suspend fun discover(address: String): ServerDetails = withContext(Dispatchers.IO) {
        reset()
        val newPaths = graph.factory.newPaths()
        paths = newPaths
        try {
            val c = graph.factory.loginBuilder(newPaths, address).build()
            client = c
            val details = c.homeserverLoginDetails()
            ServerDetails(
                address = address,
                url = details.url(),
                supportsOAuth = details.supportsOauthLogin(),
                supportsPassword = details.supportsPasswordLogin(),
                supportsSso = details.supportsSsoLogin(),
                canRegister = details.supportedOauthPrompts().any { it is OAuthPrompt.Create },
            )
        } catch (t: Throwable) {
            reset()
            throw t
        }
    }

    suspend fun loginWithPassword(username: String, password: String): MatrixSession = withContext(Dispatchers.IO) {
        val c = client ?: error(Strings.str(R.string.choose_a_server_first))
        c.login(username.trim(), password, graph.factory.deviceName, null)
        finish(c)
    }

    /** The browser URL for OAuth sign-in (or account creation when [register] is true). */
    suspend fun oauthUrl(register: Boolean): String = withContext(Dispatchers.IO) {
        val c = client ?: error(Strings.str(R.string.choose_a_server_first))
        oauth?.let { runCatching { c.abortOauthAuth(it) } }
        val data = c.urlForOauth(
            oauthConfiguration = oauthConfig,
            prompt = if (register) OAuthPrompt.Create else null,
            loginHint = null,
            deviceId = null,
            additionalScopes = null,
        )
        oauth = data
        data.loginUrl()
    }

    suspend fun ssoUrl(): String = withContext(Dispatchers.IO) {
        val c = client ?: error(Strings.str(R.string.choose_a_server_first))
        val handler = c.startSsoLogin(ssoRedirect, null)
        sso = handler
        handler.url()
    }

    fun isSsoCallback(url: String) = url.startsWith(ssoRedirect)

    /** Completes sign-in with the URL the browser redirected back to. */
    suspend fun completeFromRedirect(url: String): MatrixSession = withContext(Dispatchers.IO) {
        val c = client ?: error(Strings.str(R.string.sign_in_expired_start_again))
        val handler = sso
        if (isSsoCallback(url) && handler != null) {
            handler.finish(url)
        } else {
            c.loginWithOauthCallback(url)
        }
        oauth = null
        sso = null
        finish(c)
    }

    /**
     * Signs in by scanning the QR code another signed-in device shows ("Link new device" in Element, Element X or
     * ElementCtrl), as in MSC4108. Only works with servers that sign in through OAuth (Matrix Authentication Service)
     * with QR sign-in turned on; otherwise this throws a [QrLoginProblem] that says so. Progress is in [qrStep].
     */
    suspend fun loginWithQr(bytes: ByteArray): MatrixSession = withContext(Dispatchers.IO) {
        _qrStep.value = QrSignInStep.Connecting
        val c = try {
            val data = try {
                QrCodeData.fromBytes(bytes)
            } catch (e: QrCodeDecodeException) {
                throw QrLoginProblem(Strings.str(R.string.qr_not_a_sign_in_code))
            }
            if (data.intent() != QrCodeIntent.RECIPROCATE) throw QrLoginProblem(Strings.str(R.string.qr_wrong_side_new_device))
            val server = data.serverName() ?: data.baseUrl() ?: throw QrLoginProblem(Strings.str(R.string.qr_code_unsupported))
            reset()
            val newPaths = graph.factory.newPaths()
            paths = newPaths
            val built = graph.factory.loginBuilder(newPaths, server).build()
            client = built
            // QR sign-in hands over an OAuth session, so servers without Matrix Authentication Service can't do it.
            if (!built.homeserverLoginDetails().supportsOauthLogin()) throw QrLoginProblem(Strings.str(R.string.qr_server_unsupported))
            val task = built.newLoginWithQrCodeHandler(oauthConfig).scan(data, object : QrLoginProgressListener {
                override fun onUpdate(state: QrLoginProgress) {
                    when (state) {
                        QrLoginProgress.Starting -> _qrStep.value = QrSignInStep.Connecting
                        is QrLoginProgress.EstablishingSecureChannel -> _qrStep.value = QrSignInStep.ShowCheckCode(state.checkCodeString)
                        is QrLoginProgress.WaitingForToken -> _qrStep.value = QrSignInStep.WaitingForApproval(state.userCode)
                        QrLoginProgress.SyncingSecrets, QrLoginProgress.Done -> _qrStep.value = QrSignInStep.Syncing
                    }
                }
            })
            qrTask = task
            task.wait()
            qrTask = null
            built
        } catch (t: Throwable) {
            qrTask = null
            _qrStep.value = QrSignInStep.Idle
            reset()
            throw t
        }
        try {
            finish(c)
        } finally {
            _qrStep.value = QrSignInStep.Idle
        }
    }

    /** Stops a QR sign-in that's still going; [loginWithQr] then fails with "cancelled". */
    fun cancelQr() {
        runCatching { qrTask?.cancel() }
    }

    private suspend fun finish(c: Client): MatrixSession {
        val p = paths ?: error(Strings.str(R.string.no_session_folder))
        val stored = graph.factory.toStored(c.session(), p)
        graph.store.save(stored)
        // The login client and the real one share the same databases, so the first must be closed before the second opens.
        c.close()
        client = null
        paths = null
        return graph.restore(stored) ?: error(Strings.str(R.string.signed_in_but_the_session_couldn_t))
    }

    /** Throws away a half-finished sign-in and its empty databases. */
    fun reset() {
        runCatching { oauth?.close() }
        oauth = null
        sso = null
        runCatching { client?.close() }
        client = null
        paths?.deleteAll()
        paths = null
    }
}
