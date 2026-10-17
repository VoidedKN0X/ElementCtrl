package com.vknxworks.elementctrl.matrix

import org.matrix.rustcomponents.sdk.ClientBuildException
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.OAuthException

/** A short, readable explanation of what went wrong, for snackbars and error rows. */
fun Throwable.userMessage(): String {
    when (this) {
        is ClientBuildException.SlidingSyncVersion, is ClientBuildException.SlidingSync ->
            return "This server doesn't support Simplified Sliding Sync, which modern Matrix apps need. Update Synapse to 1.114 or newer (or Conduit/Tuwunel with sliding sync on)."
        is ClientBuildException.InvalidServerName -> return "That isn't a valid server address."
        is ClientBuildException.WellKnownLookupFailed, is ClientBuildException.WellKnownDeserializationException ->
            return "Couldn't find a Matrix server at that address. Try the full URL, like https://matrix.example.com."
        is ClientBuildException.ServerUnreachable -> return "Couldn't reach the server. Check the address and your connection."
        is OAuthException.Cancelled -> return "Sign-in was cancelled."
        else -> Unit
    }
    val raw = when (this) {
        is ClientException.MatrixApi -> msg.ifBlank { code }
        is ClientException.Generic -> msg
        is ClientException -> message
        is OAuthException -> message
        is ClientBuildException -> message
        else -> message
    }.orEmpty()
    val lower = raw.lowercase()
    return when {
        "sliding sync" in lower || "slidingsync" in lower || "simplified" in lower ->
            "This server doesn't support Simplified Sliding Sync, which modern Matrix apps need. Update Synapse to 1.114 or newer (or Conduit/Tuwunel with sliding sync on)."
        "m_forbidden" in lower || "invalid username or password" in lower || "invalid password" in lower ->
            "Wrong username or password."
        "m_user_deactivated" in lower -> "This account has been deactivated."
        "m_limit_exceeded" in lower -> "Too many attempts. Wait a moment and try again."
        "dns" in lower || "failed to lookup" in lower || "error sending request" in lower || "connection refused" in lower ->
            "Couldn't reach the server. Check the address and your connection."
        "well-known" in lower || "wellknown" in lower || "invalid server name" in lower ->
            "That doesn't look like a Matrix server. Check the address."
        "certificate" in lower || "invalidcertificate" in lower ->
            "The server's TLS certificate isn't trusted. If it uses your own CA, install that certificate on the phone."
        raw.isBlank() -> this::class.simpleName ?: "Something went wrong"
        else -> redactSecrets(raw).take(300)
    }
}

/**
 * Strips anything that looks like a credential from text that may be shown on screen: access tokens in URLs or
 * headers, Synapse/MAS token formats, and URL query strings and user info (which can carry tokens or passwords).
 */
fun redactSecrets(text: String): String = text
    .replace(Regex("""(?i)(access_token|refresh_token|token|password|passwd|secret|key)=[^&\s"']+"""), "$1=…")
    .replace(Regex("""(?i)bearer\s+[A-Za-z0-9._~+/=-]+"""), "Bearer …")
    .replace(Regex("""\b(syt|syr|mat|mar|mct|mcr)_[A-Za-z0-9_-]{8,}"""), "…")
    .replace(Regex("""(https?://)[^/@\s]+@"""), "$1")
    .replace(Regex("""(https?://[^\s?#"']+)\?[^\s#"']*"""), "$1?…")
