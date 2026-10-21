package com.vknxworks.elementctrl.data

import android.content.Context
import com.vknxworks.elementctrl.data.lock.LockVault
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

/**
 * Keeps the signed-in account (access and refresh token, and the key to the SDK's encrypted databases),
 * encrypted with a Keystore key. One account at a time. With the app lock on and notifications off,
 * it's also sealed with the app lock's key ([sealing]), so it can only be opened while ElementCtrl is unlocked.
 */
class SessionStore(context: Context, private val vault: LockVault, private val sealing: () -> Boolean) {
    private val sp = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    /** The saved session, or null when there's none or it's sealed and the app is locked. */
    @Synchronized
    fun load(): StoredSession? {
        val plain = sp.getString("current", null)?.let(SecureBox::decrypt) ?: return null
        val json = if (LockVault.isSealed(plain)) vault.open(plain) ?: return null else plain
        return StoredSession.fromJson(json)
    }

    /** A session is saved but sealed by the app lock, and the app is locked: it opens after unlocking. */
    @Synchronized
    fun isSealedNow(): Boolean {
        val plain = sp.getString("current", null)?.let(SecureBox::decrypt) ?: return false
        return LockVault.isSealed(plain) && !vault.isOpen
    }

    /** Whether a session is saved at all, even one that can't be decrypted right now. */
    @Synchronized
    fun hasSaved(): Boolean = sp.contains("current")

    @Synchronized
    fun save(session: StoredSession) {
        val json = session.toJson()
        // A token refresh while the app is locked can't be sealed (the key isn't in memory). It's kept with
        // the Keystore only rather than lost, and sealed again at the next unlock (see reseal).
        val inner = if (sealing()) vault.seal(json) ?: json else json
        sp.edit().putString("current", SecureBox.encrypt(inner)).commit()
    }

    /** Seals or unseals the saved session to match [seal] (only while unlocked, so it can be read first). */
    @Synchronized
    fun reseal(seal: Boolean = sealing()) {
        val plain = sp.getString("current", null)?.let(SecureBox::decrypt) ?: return
        if (LockVault.isSealed(plain) == seal) return
        val json = if (LockVault.isSealed(plain)) vault.open(plain) ?: return else plain
        val inner = if (seal) vault.seal(json) ?: return else json
        sp.edit().putString("current", SecureBox.encrypt(inner)).commit()
    }

    @Synchronized
    fun clear() {
        sp.edit().remove("current").commit()
    }
}
