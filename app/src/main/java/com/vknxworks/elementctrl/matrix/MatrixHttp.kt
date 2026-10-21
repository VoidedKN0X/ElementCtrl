package com.vknxworks.elementctrl.matrix

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

/** Plain client-server API calls for the few endpoints the SDK doesn't wrap (devices, room state for sticker packs). */
object MatrixHttp {
    /** Largest response read into memory; these endpoints return small JSON documents. */
    private const val MAX_BODY = 4 * 1024 * 1024

    /** Reads at most [limit] bytes as UTF-8, so a hostile or broken server can't exhaust memory. */
    fun readCapped(input: java.io.InputStream, limit: Int = MAX_BODY): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > limit) error(Strings.str(R.string.the_server_s_response_was_too_large))
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /**
     * Sends an authenticated JSON request. Throws on errors unless [returnErrors] is set, in which case
     * the status code and error body come back for the caller to handle (e.g. 401 auth challenges).
     */
    suspend fun request(
        session: MatrixSession,
        method: String,
        path: String,
        body: JSONObject? = null,
        returnErrors: Boolean = false,
    ): Pair<Int, JSONObject> = withContext(Dispatchers.IO) {
        val conn = (URL(session.homeserver.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            // Never follow a redirect with the access token attached: it could hand the token to another host.
            instanceFollowRedirects = false
            useCaches = false
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer ${session.client.session().accessToken}")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            body?.let { b -> conn.outputStream.use { it.write(b.toString().toByteArray()) } }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { readCapped(it) }.orEmpty()
            val json = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            if (code !in 200..299 && !returnErrors) error(json.optString("error").ifBlank { Strings.str(R.string.http, code) })
            code to json
        } finally {
            conn.disconnect()
        }
    }
}
