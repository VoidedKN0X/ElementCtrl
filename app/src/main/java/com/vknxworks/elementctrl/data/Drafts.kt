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

    private fun load(): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString("index", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun set(roomId: String, text: String?) {
        val next = _all.value.toMutableMap()
        if (text.isNullOrBlank()) next.remove(roomId) else next[roomId] = text.lineSequence().first().take(120)
        if (next == _all.value) return
        _all.value = next
        sp.edit().putString("index", JSONObject(next as Map<*, *>).toString()).apply()
    }

    fun clearAll() {
        _all.value = emptyMap()
        sp.edit().clear().apply()
    }
}
