package com.vknxworks.elementctrl.matrix

import org.matrix.rustcomponents.sdk.ClientBuildException
import org.matrix.rustcomponents.sdk.ClientException
import org.matrix.rustcomponents.sdk.OAuthException
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

/** A short, readable explanation of what went wrong, for snackbars and error rows. */
fun Throwable.userMessage(): String {
    when (this) {
        is ClientBuildException.SlidingSyncVersion, is ClientBuildException.SlidingSync ->
            return Strings.str(R.string.this_server_doesn_t_support)
        is ClientBuildException.InvalidServerName -> return Strings.str(R.string.that_isn_t_a_valid_server_address)
        is ClientBuildException.WellKnownLookupFailed, is ClientBuildException.WellKnownDeserializationException ->
            return Strings.str(R.string.couldn_t_find_a_matrix_server_at)
        is ClientBuildException.ServerUnreachable -> return Strings.str(R.string.couldn_t_reach_the_server_check_the)
        is OAuthException.Cancelled -> return Strings.str(R.string.sign_in_was_cancelled)
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
            Strings.str(R.string.this_server_doesn_t_support)
        "m_forbidden" in lower || "invalid username or password" in lower || "invalid password" in lower ->
            Strings.str(R.string.wrong_username_or_password)
        "m_user_deactivated" in lower -> Strings.str(R.string.this_account_has_been_deactivated)
        "m_limit_exceeded" in lower -> Strings.str(R.string.too_many_attempts_wait_a_moment_and)
        "dns" in lower || "failed to lookup" in lower || "error sending request" in lower || "connection refused" in lower ->
            Strings.str(R.string.couldn_t_reach_the_server_check_the)
        "well-known" in lower || "wellknown" in lower || "invalid server name" in lower ->
            Strings.str(R.string.that_doesn_t_look_like_a_matrix)
        "certificate" in lower || "invalidcertificate" in lower ->
            Strings.str(R.string.the_server_s_tls_certificate_isn_t)
        raw.isBlank() -> this::class.simpleName ?: Strings.str(R.string.something_went_wrong)
        else -> redactSecrets(raw).take(300)
    }
}

/**
 * Strips anything that looks like a credential from text that may be shown on screen: access tokens in URLs or
 * headers, Synapse/MAS token formats, and URL query strings and user info (which can carry tokens or passwords).
 */
fun redactSecrets(text: String): String = text
    // name=value (query strings, forms) and "name": "value" / name: value (JSON, debug output).
    .replace(
        Regex("""(?i)("?\b(?:access_token|refresh_token|id_token|login_token|token|password|passwd|passphrase|secret|recovery_key|key)"?\s*[=:]\s*"?)[^&\s"',;}]+"""),
        "$1…",
    )
    // JSON Web Tokens (OIDC id/access tokens).
    .replace(Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]*"""), "…")
    .replace(Regex("""(?i)bearer\s+[A-Za-z0-9._~+/=-]+"""), "Bearer …")
    .replace(Regex("""\b(syt|syr|mat|mar|mct|mcr)_[A-Za-z0-9_-]{8,}"""), "…")
    .replace(Regex("""(https?://)[^/@\s]+@"""), "$1")
    .replace(Regex("""(https?://[^\s?#"']+)\?[^\s#"']*"""), "$1?…")
