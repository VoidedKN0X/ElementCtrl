package com.vknxworks.elementctrl.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How messages are drawn in a chat. */
enum class BubbleStyle(val label: String, val description: String) {
    MODERN("Modern", "Rounded bubbles with a small tail"),
    ROUND("Round", "Fully rounded bubbles"),
    SHARP("Sharp", "Square corners, compact"),
    OUTLINED("Outlined", "Bubbles with an outline instead of a fill"),
    VIVID("Vivid", "Your messages in a strong accent colour"),
    FLAT("No bubbles", "Everything on the left with names, like Element Web"),
}

/** App-level settings that are not tied to the Matrix account. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(ThemeMode.entries.getOrElse(sp.getInt("theme", 0)) { ThemeMode.SYSTEM })
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    fun setTheme(mode: ThemeMode) { sp.edit().putInt("theme", mode.ordinal).apply(); _theme.value = mode }

    private val _dynamicColor = MutableStateFlow(sp.getBoolean("dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()
    fun setDynamicColor(on: Boolean) { sp.edit().putBoolean("dynamic_color", on).apply(); _dynamicColor.value = on }

    private val _notifications = MutableStateFlow(sp.getBoolean("notifications", true))
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()
    fun setNotifications(on: Boolean) { sp.edit().putBoolean("notifications", on).apply(); _notifications.value = on }

    private val _bubbles = MutableStateFlow(BubbleStyle.entries.getOrElse(sp.getInt("bubble_style", 0)) { BubbleStyle.MODERN })
    val bubbleStyle: StateFlow<BubbleStyle> = _bubbles.asStateFlow()
    fun setBubbleStyle(style: BubbleStyle) { sp.edit().putInt("bubble_style", style.ordinal).apply(); _bubbles.value = style }

    private val _linkPreviews = MutableStateFlow(sp.getBoolean("link_previews", true))
    /** Show previews for links in messages (fetched by your homeserver). */
    val linkPreviews: StateFlow<Boolean> = _linkPreviews.asStateFlow()
    fun setLinkPreviews(on: Boolean) { sp.edit().putBoolean("link_previews", on).apply(); _linkPreviews.value = on }

    private val _linkPreviewsEncrypted = MutableStateFlow(sp.getBoolean("link_previews_e2ee", false))
    /** Also in encrypted chats; off by default because the homeserver then learns which links were shared. */
    val linkPreviewsEncrypted: StateFlow<Boolean> = _linkPreviewsEncrypted.asStateFlow()
    fun setLinkPreviewsEncrypted(on: Boolean) { sp.edit().putBoolean("link_previews_e2ee", on).apply(); _linkPreviewsEncrypted.value = on }

    init {
        // GIF search was removed: drop any Tenor key older versions stored.
        if (sp.contains("tenor_key") || sp.contains("tenor_key_enc")) sp.edit().remove("tenor_key").remove("tenor_key_enc").apply()
    }

    private val _sendOnEnter = MutableStateFlow(sp.getBoolean("send_on_enter", false))
    val sendOnEnter: StateFlow<Boolean> = _sendOnEnter.asStateFlow()
    fun setSendOnEnter(on: Boolean) { sp.edit().putBoolean("send_on_enter", on).apply(); _sendOnEnter.value = on }

    private val _showHiddenEvents = MutableStateFlow(sp.getBoolean("show_state_events", true))
    /** Show joins, leaves, name changes and other room events in the timeline. */
    val showStateEvents: StateFlow<Boolean> = _showHiddenEvents.asStateFlow()
    fun setShowStateEvents(on: Boolean) { sp.edit().putBoolean("show_state_events", on).apply(); _showHiddenEvents.value = on }

    /** Custom servers signed in to before, newest first, offered again on the server picker. */
    var recentCustomServers: List<String>
        get() = sp.getString("recent_servers", "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(value) { sp.edit().putString("recent_servers", value.take(5).joinToString("\n")).apply() }

    fun rememberCustomServer(address: String) {
        if (Servers.standard.any { it.address.equals(address, true) }) return
        recentCustomServers = listOf(address) + recentCustomServers.filterNot { it.equals(address, true) }
    }
}
