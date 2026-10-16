package com.vknxworks.elementctrl.matrix

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Plain client-server API calls for the few endpoints the SDK doesn't wrap (devices, room state for sticker packs). */
object MatrixHttp {
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
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.readText().orEmpty()
            val json = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrDefault(JSONObject())
            if (code !in 200..299 && !returnErrors) error(json.optString("error").ifBlank { "HTTP $code" })
            code to json
        } finally {
            conn.disconnect()
        }
    }
}
