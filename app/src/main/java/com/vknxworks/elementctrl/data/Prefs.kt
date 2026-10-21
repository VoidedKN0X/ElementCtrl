package com.vknxworks.elementctrl.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

/** Stored by position: new choices go at the end. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

/** How messages are drawn in a chat. */
enum class BubbleStyle(val label: String, val description: String) {
    MODERN(Strings.str(R.string.modern), Strings.str(R.string.rounded_bubbles_with_a_small_tail)),
    ROUND(Strings.str(R.string.round), Strings.str(R.string.fully_rounded_bubbles)),
    SHARP(Strings.str(R.string.sharp), Strings.str(R.string.square_corners_compact)),
    OUTLINED(Strings.str(R.string.outlined), Strings.str(R.string.bubbles_with_an_outline_instead_of_a)),
    VIVID(Strings.str(R.string.vivid), Strings.str(R.string.your_messages_in_a_strong_accent)),
    FLAT(Strings.str(R.string.no_bubbles), Strings.str(R.string.everything_on_the_left_with_names)),
}

/** App-level settings that are not tied to the Matrix account. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // ElementCtrl 1.2.15 removed the optional anonymous stats. Delete the two settings they used (their keys
        // start with "usage_"), so nothing from them stays on the phone after the update.
        val leftover = sp.all.keys.filter { it.startsWith("usage_") }
        if (leftover.isNotEmpty()) sp.edit().apply { leftover.forEach { remove(it) } }.apply()
    }

    private val _theme = MutableStateFlow(ThemeMode.entries.getOrElse(sp.getInt("theme", 0)) { ThemeMode.SYSTEM })
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    fun setTheme(mode: ThemeMode) { sp.edit().putInt("theme", mode.ordinal).apply(); _theme.value = mode }

    private val _dynamicColor = MutableStateFlow(sp.getBoolean("dynamic_color", false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()
    fun setDynamicColor(on: Boolean) { sp.edit().putBoolean("dynamic_color", on).apply(); _dynamicColor.value = on }

    private val _notifications = MutableStateFlow(sp.getBoolean("notifications", true))
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()
    fun setNotifications(on: Boolean) { sp.edit().putBoolean("notifications", on).apply(); _notifications.value = on }

    private val _bubbles = MutableStateFlow(BubbleStyle.entries.getOrElse(sp.getInt("bubble_style", 0)) { BubbleStyle.MODERN })
    val bubbleStyle: StateFlow<BubbleStyle> = _bubbles.asStateFlow()
    fun setBubbleStyle(style: BubbleStyle) { sp.edit().putInt("bubble_style", style.ordinal).apply(); _bubbles.value = style }

    private val _chatAvatars = MutableStateFlow(sp.getBoolean("chat_avatars", true))
    /** Profile pictures next to messages, in DMs and for your own messages too. */
    val chatAvatars: StateFlow<Boolean> = _chatAvatars.asStateFlow()
    fun setChatAvatars(on: Boolean) { sp.edit().putBoolean("chat_avatars", on).apply(); _chatAvatars.value = on }

    private val _doubleTap = MutableStateFlow(sp.getString("double_tap_reaction", "❤️").orEmpty())
    /** Emoji a double-tap on a message reacts with; blank turns double-tap off. */
    val doubleTapReaction: StateFlow<String> = _doubleTap.asStateFlow()
    fun setDoubleTapReaction(emoji: String) { sp.edit().putString("double_tap_reaction", emoji).apply(); _doubleTap.value = emoji }

    private val _readReceipts = MutableStateFlow(sp.getBoolean("show_read_receipts", true))
    /** Small pictures under messages showing who has read up to there. */
    val showReadReceipts: StateFlow<Boolean> = _readReceipts.asStateFlow()
    fun setShowReadReceipts(on: Boolean) { sp.edit().putBoolean("show_read_receipts", on).apply(); _readReceipts.value = on }

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
