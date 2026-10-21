package com.vknxworks.elementctrl.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.elementctrl.R
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** How long the app can be in the background before the app lock asks again. -1: only after a restart. */
enum class LockTimeout(@StringRes val label: Int, val millis: Long) {
    IMMEDIATELY(R.string.lock_again_immediately, 0L),
    MINUTE_1(R.string.lock_again_1m, 60_000L),
    MINUTES_5(R.string.lock_again_5m, 5 * 60_000L),
    MINUTES_15(R.string.lock_again_15m, 15 * 60_000L),
    HOUR_1(R.string.lock_again_1h, 60 * 60_000L),
    RESTART(R.string.lock_again_restart, -1L),
}

/**
 * App lock preferences. The PIN, fingerprint key and sealing key live in LockVault (the shared Ctrl
 * series lock); [lockOn] says whether it's set up.
 *
 * Also reads what ElementCtrl's older PIN lock (before 1.4.0) left behind, only so people updating can
 * unlock once with their old PIN before choosing the new app PIN. That old PIN was stored as a salted
 * PBKDF2 hash, never in plain text, and the hash is deleted as soon as the new PIN is set.
 */
class LockSettings(context: Context, private val lockOn: () -> Boolean) {
    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- settings

    private var _timeout by mutableStateOf(readTimeout())

    /** When the app lock asks again after you leave the app. */
    var lockTimeout: LockTimeout
        get() = _timeout
        set(v) { _timeout = v; prefs.edit().putString(KEY_TIMEOUT, v.name).apply() }

    /** The old "Lock again after leaving" (seconds) becomes the closest of the new choices. */
    private fun readTimeout(): LockTimeout {
        prefs.getString(KEY_TIMEOUT, null)?.let { n -> LockTimeout.entries.firstOrNull { it.name == n } }?.let { return it }
        if (!prefs.contains(KEY_LOCK_AFTER)) return LockTimeout.IMMEDIATELY
        return when (prefs.getInt(KEY_LOCK_AFTER, 0)) {
            0 -> LockTimeout.IMMEDIATELY
            in 1..60 -> LockTimeout.MINUTE_1
            else -> LockTimeout.MINUTES_5
        }
    }

    /**
     * Hide in screenshots (FLAG_SECURE) and Hide in recent apps (a blank card there), both off by default.
     * The older single switch ("Hide from recents & screenshots", or 1.4.0's "Hide in screenshots and recent
     * apps") carries over to both when someone had explicitly turned it on.
     */
    private val hideBefore: Boolean =
        prefs.getBoolean(KEY_HIDE_SCREENS, false) || (prefs.contains(KEY_HIDE) && prefs.getBoolean(KEY_HIDE, false))

    private var _hideShots by mutableStateOf(prefs.getBoolean(KEY_HIDE_SCREENSHOTS, hideBefore))
    var hideScreenshots: Boolean
        get() = _hideShots
        set(v) { _hideShots = v; prefs.edit().putBoolean(KEY_HIDE_SCREENSHOTS, v).apply() }

    private var _hideRecents by mutableStateOf(prefs.getBoolean(KEY_HIDE_RECENTS, hideBefore))
    var hideRecents: Boolean
        get() = _hideRecents
        set(v) { _hideRecents = v; prefs.edit().putBoolean(KEY_HIDE_RECENTS, v).apply() }

    /**
     * Show who wrote what in message notifications. Until the user picks, it follows the app lock: shown without
     * it, hidden with it (otherwise the lock screen would leak what the app lock protects).
     */
    private var _showContent by mutableStateOf(if (prefs.contains(KEY_NOTIF_CONTENT)) prefs.getBoolean(KEY_NOTIF_CONTENT, true) else null)
    var showNotificationContent: Boolean
        get() = _showContent ?: !(lockOn() || _legacy)
        set(v) { _showContent = v; prefs.edit().putBoolean(KEY_NOTIF_CONTENT, v).apply() }

    /** Whether the user picked [showNotificationContent] themselves. */
    val notificationContentChosen: Boolean get() = _showContent != null

    // ---------------------------------------------------------------- the old PIN lock (before 1.4.0)

    private var _legacy by mutableStateOf(prefs.contains(KEY_PIN_HASH))

    /** The old PIN lock is still set: unlock with it once, then choose an app PIN. */
    val legacyPinSet: Boolean get() = _legacy

    /** The old lock also unlocked with a fingerprint. */
    val legacyBiometric: Boolean get() = _legacy && prefs.getBoolean(KEY_BIOMETRIC, false)

    /** Lets the old-PIN screen submit as soon as enough digits are entered. */
    val legacyPinLength: Int get() = prefs.getInt(KEY_PIN_LENGTH, 0)

    fun checkLegacyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.let(::unb64) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null)?.let(::unb64) ?: return false
        return MessageDigest.isEqual(expected, legacyHash(pin, salt))
    }

    /** The new app PIN is set (or the lock was reset): forget the old PIN, its fingerprint key and counters. */
    fun clearLegacyPin() {
        prefs.edit()
            // What the old settings carried over to stays as it is.
            .putString(KEY_TIMEOUT, _timeout.name)
            .putBoolean(KEY_HIDE_SCREENSHOTS, _hideShots)
            .putBoolean(KEY_HIDE_RECENTS, _hideRecents)
            .remove(KEY_HIDE_SCREENS)
            .remove(KEY_PIN_LENGTH).remove(KEY_PIN_HASH).remove(KEY_PIN_SALT)
            .remove(KEY_BIOMETRIC).remove(KEY_FAILED).remove(KEY_LOCKOUT_UNTIL)
            .remove(KEY_LOCKOUT_REMAINING).remove(KEY_LOCKOUT_BASE).remove(KEY_LOCKOUT_BOOT)
            .remove(KEY_LOCK_AFTER).remove(KEY_HIDE)
            .apply()
        _legacy = false
        com.vknxworks.elementctrl.ui.lock.LegacyBiometric.delete()
    }

    /** Wrong old-PIN counter that survives restarts, so killing the app doesn't reset the lockout. */
    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)
        set(v) { prefs.edit().putInt(KEY_FAILED, v).commit() }

    /**
     * Wrong old-PIN lockout, measured with the monotonic clock so changing the date/time can't end it. After a
     * reboot (boot count changed, or elapsedRealtime went backwards) the remaining time continues.
     */
    fun startLockout(durationMs: Long) {
        prefs.edit()
            .putLong(KEY_LOCKOUT_REMAINING, durationMs)
            .putLong(KEY_LOCKOUT_BASE, SystemClock.elapsedRealtime())
            .putInt(KEY_LOCKOUT_BOOT, bootCount())
            .remove(KEY_LOCKOUT_UNTIL)
            .commit()
    }

    /** Milliseconds of old-PIN lockout left (0 when none); also checkpoints the remaining time for reboots. */
    fun lockoutRemaining(): Long {
        migrateWallClockLockout()
        val remaining = prefs.getLong(KEY_LOCKOUT_REMAINING, 0L)
        if (remaining <= 0L) return 0L
        val now = SystemClock.elapsedRealtime()
        val base = prefs.getLong(KEY_LOCKOUT_BASE, now)
        val rebooted = prefs.getInt(KEY_LOCKOUT_BOOT, -1) != bootCount() || now < base
        val left = if (rebooted) remaining else (remaining - (now - base)).coerceAtLeast(0L)
        val edit = prefs.edit()
        if (left <= 0L) edit.remove(KEY_LOCKOUT_REMAINING).remove(KEY_LOCKOUT_BASE).remove(KEY_LOCKOUT_BOOT)
        else edit.putLong(KEY_LOCKOUT_REMAINING, left).putLong(KEY_LOCKOUT_BASE, now).putInt(KEY_LOCKOUT_BOOT, bootCount())
        edit.apply()
        return left
    }

    /** Even older versions stored the lockout end as wall-clock time; carry over what's left of it. */
    private fun migrateWallClockLockout() {
        if (!prefs.contains(KEY_LOCKOUT_UNTIL)) return
        val left = (prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) - System.currentTimeMillis()).coerceIn(0L, 900_000L)
        prefs.edit().remove(KEY_LOCKOUT_UNTIL).apply()
        if (left > 0L) startLockout(left)
    }

    private fun bootCount(): Int =
        runCatching { Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

    private fun legacyHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    private companion object {
        const val KEY_TIMEOUT = "lock_timeout"
        const val KEY_HIDE_SCREENS = "hide_screens"
        const val KEY_HIDE_SCREENSHOTS = "hide_screenshots"
        const val KEY_HIDE_RECENTS = "hide_recents"
        const val KEY_NOTIF_CONTENT = "notification_content"
        // The old PIN lock
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_LOCK_AFTER = "lock_after"
        const val KEY_HIDE = "hide_content"
        const val KEY_FAILED = "pin_failed"
        const val KEY_LOCKOUT_UNTIL = "pin_lockout_until"
        const val KEY_LOCKOUT_REMAINING = "pin_lockout_remaining"
        const val KEY_LOCKOUT_BASE = "pin_lockout_base_elapsed"
        const val KEY_LOCKOUT_BOOT = "pin_lockout_boot"
    }
}
