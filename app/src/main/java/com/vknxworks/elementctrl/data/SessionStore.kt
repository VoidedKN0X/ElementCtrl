package com.vknxworks.elementctrl.data

import android.content.Context
import org.json.JSONObject

/** Everything needed to restore a signed-in account after the app restarts. */
data class StoredSession(
    val userId: String,
    val deviceId: String,
    val homeserverUrl: String,
    val accessToken: String,
    val refreshToken: String?,
    val oauthData: String?,
    /** Base64 key that encrypts the SDK's local databases (messages, keys, cache). */
    val databaseKey: String,
    /** Folder name under files/ and cache/ that holds this account's databases. */
    val directory: String,
) {
    fun toJson(): String = JSONObject().apply {
        put("userId", userId)
        put("deviceId", deviceId)
        put("homeserverUrl", homeserverUrl)
        put("accessToken", accessToken)
        put("refreshToken", refreshToken ?: JSONObject.NULL)
        put("oauthData", oauthData ?: JSONObject.NULL)
        put("databaseKey", databaseKey)
        put("directory", directory)
    }.toString()

    companion object {
        fun fromJson(s: String): StoredSession? = runCatching {
            val o = JSONObject(s)
            StoredSession(
                userId = o.getString("userId"),
                deviceId = o.getString("deviceId"),
                homeserverUrl = o.getString("homeserverUrl"),
                accessToken = o.getString("accessToken"),
                refreshToken = o.optString("refreshToken").takeUnless { o.isNull("refreshToken") || it.isEmpty() },
                oauthData = o.optString("oauthData").takeUnless { o.isNull("oauthData") || it.isEmpty() },
                databaseKey = o.getString("databaseKey"),
                directory = o.getString("directory"),
            )
        }.getOrNull()
    }
}

/** Keeps the signed-in account, encrypted with a Keystore key. One account at a time. */
class SessionStore(context: Context) {
    private val sp = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): StoredSession? = sp.getString("current", null)?.let(SecureBox::decrypt)?.let(StoredSession::fromJson)

    @Synchronized
    fun save(session: StoredSession) {
        sp.edit().putString("current", SecureBox.encrypt(session.toJson())).commit()
    }

    @Synchronized
    fun clear() {
        sp.edit().remove("current").commit()
    }
}
