package com.vknxworks.elementctrl.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.OAuthConfiguration
import org.matrix.rustcomponents.sdk.OAuthPrompt
import org.matrix.rustcomponents.sdk.SsoHandler
import uniffi.matrix_sdk.OAuthAuthorizationData

/** What a homeserver lets you do on the sign-in screen. */
data class ServerDetails(
    val address: String,
    val url: String,
    val supportsOAuth: Boolean,
    val supportsPassword: Boolean,
    val supportsSso: Boolean,
    val canRegister: Boolean,
)

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

    private val oauthConfig
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
        val c = client ?: error("Choose a server first")
        c.login(username.trim(), password, graph.factory.deviceName, null)
        finish(c)
    }

    /** The browser URL for OAuth sign-in (or account creation when [register] is true). */
    suspend fun oauthUrl(register: Boolean): String = withContext(Dispatchers.IO) {
        val c = client ?: error("Choose a server first")
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
        val c = client ?: error("Choose a server first")
        val handler = c.startSsoLogin(ssoRedirect, null)
        sso = handler
        handler.url()
    }

    fun isSsoCallback(url: String) = url.startsWith(ssoRedirect)

    /** Completes sign-in with the URL the browser redirected back to. */
    suspend fun completeFromRedirect(url: String): MatrixSession = withContext(Dispatchers.IO) {
        val c = client ?: error("Sign-in expired, start again")
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

    private suspend fun finish(c: Client): MatrixSession {
        val p = paths ?: error("No session folder")
        val stored = graph.factory.toStored(c.session(), p)
        graph.store.save(stored)
        // The login client and the real one share the same databases, so the first must be closed before the second opens.
        c.close()
        client = null
        paths = null
        return graph.restore(stored) ?: error("Signed in, but the session couldn't be opened")
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
