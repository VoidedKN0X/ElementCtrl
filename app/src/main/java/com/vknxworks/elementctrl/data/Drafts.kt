package com.vknxworks.elementctrl.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Which chats have an unsent draft and its first line, so the chat list can show "Draft: …". The draft
 * itself is stored by the SDK in the account's encrypted database; this is only a local index.
 */
class Drafts(context: Context) {
    private val sp = context.getSharedPreferences("drafts", Context.MODE_PRIVATE)
    private val _all = MutableStateFlow(load())
    val all: StateFlow<Map<String, String>> = _all.asStateFlow()

    // The index holds the start of unsent messages, so it is encrypted with the Keystore key (SecureBox).
    private fun load(): Map<String, String> = runCatching {
        val raw = sp.getString(KEY_ENC, null)?.let { SecureBox.decrypt(it) }
            ?: sp.getString(KEY_PLAIN, null)
            ?: "{}"
        val o = JSONObject(raw)
        val map = o.keys().asSequence().associateWith { o.getString(it) }
        // Older versions stored the index in plain text: encrypt it once and drop the plain copy.
        if (sp.contains(KEY_PLAIN)) runCatching { save(map) }
        map
    }.getOrDefault(emptyMap())

    private fun save(map: Map<String, String>) {
        val json = JSONObject(map as Map<*, *>).toString()
        sp.edit().putString(KEY_ENC, SecureBox.encrypt(json)).remove(KEY_PLAIN).apply()
    }

    fun set(roomId: String, text: String?) {
        val next = _all.value.toMutableMap()
        if (text.isNullOrBlank()) next.remove(roomId) else next[roomId] = text.lineSequence().first().take(120)
        if (next == _all.value) return
        _all.value = next
        runCatching { save(next) }
    }

    fun clearAll() {
        _all.value = emptyMap()
        sp.edit().clear().apply()
    }

    private companion object {
        const val KEY_PLAIN = "index"
        const val KEY_ENC = "index_enc"
    }
}
