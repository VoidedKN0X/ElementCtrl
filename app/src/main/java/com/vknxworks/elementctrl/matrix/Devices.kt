package com.vknxworks.elementctrl.matrix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.AccountManagementAction
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

data class DeviceInfo(
    val deviceId: String,
    val displayName: String?,
    val lastSeenIp: String?,
    val lastSeenTs: Long?,
    val isCurrent: Boolean,
)

/** Signing out other devices needs proof it's you; this says what the server wants. */
sealed interface DeleteResult {
    data object Done : DeleteResult
    /** The server asks for the account password (classic Synapse). */
    data class NeedsPassword(val session: String) : DeleteResult
    /** The account is managed by an OAuth server (MAS): finish on its web page. */
    data class OpenWebPage(val url: String) : DeleteResult
}

/** Your signed-in sessions, through the Matrix client-server API (the SDK doesn't expose this list). */
class DeviceService(private val session: MatrixSession) {
    suspend fun list(): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val o = request("GET", "/_matrix/client/v3/devices").second
        val arr = o.optJSONArray("devices") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            DeviceInfo(
                deviceId = d.getString("device_id"),
                displayName = d.optString("display_name").ifBlank { null },
                lastSeenIp = d.optString("last_seen_ip").ifBlank { null },
                lastSeenTs = d.optLong("last_seen_ts").takeIf { it > 0 },
                isCurrent = d.getString("device_id") == session.deviceId,
            )
        }.sortedWith(compareByDescending<DeviceInfo> { it.isCurrent }.thenByDescending { it.lastSeenTs ?: 0 })
    }

    suspend fun rename(deviceId: String, name: String) = withContext(Dispatchers.IO) {
        request("PUT", "/_matrix/client/v3/devices/${enc(deviceId)}", JSONObject().put("display_name", name))
        Unit
    }

    /** Signs a device out, asking for a password or the account page if the server requires it. */
    suspend fun delete(deviceIds: List<String>, password: String? = null, authSession: String? = null): DeleteResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("devices", JSONArray(deviceIds))
        if (password != null && authSession != null) {
            body.put(
                "auth",
                JSONObject()
                    .put("type", "m.login.password")
                    .put("session", authSession)
                    .put("password", password)
                    .put("identifier", JSONObject().put("type", "m.id.user").put("user", session.userId)),
            )
        }
        val (code, res) = request("POST", "/_matrix/client/v3/delete_devices", body, allowAuthChallenge = true)
        when {
            code in 200..299 -> DeleteResult.Done
            code == 401 && res.has("flows") -> {
                val stages = res.getJSONArray("flows").let { f -> (0 until f.length()).flatMap { i -> f.getJSONObject(i).getJSONArray("stages").let { s -> (0 until s.length()).map { s.getString(it) } } } }
                if ("m.login.password" in stages && password == null) {
                    DeleteResult.NeedsPassword(res.getString("session"))
                } else if (password != null) {
                    error(res.optString("error").ifBlank { Strings.str(R.string.wrong_password) })
                } else {
                    webPage(deviceIds.first()) ?: error(Strings.str(R.string.this_server_needs_a_sign_in_method))
                }
            }
            else -> webPage(deviceIds.first()) ?: error(res.optString("error").ifBlank { Strings.str(R.string.the_server_refused_http, code) })
        }
    }

    /** On OAuth (MAS) accounts devices are managed on the account web page. */
    suspend fun webPage(deviceId: String?): DeleteResult.OpenWebPage? = runCatching {
        session.client.accountUrl(if (deviceId != null) AccountManagementAction.DeviceDelete(deviceId) else AccountManagementAction.DevicesList)
    }.getOrNull()?.let { DeleteResult.OpenWebPage(it) }

    private fun enc(s: String) = MatrixHttp.enc(s)

    private suspend fun request(method: String, path: String, body: JSONObject? = null, allowAuthChallenge: Boolean = false) =
        MatrixHttp.request(session, method, path, body, returnErrors = allowAuthChallenge)
}
