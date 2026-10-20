package com.vknxworks.elementctrl.notify

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Battery optimisation decides whether pushes turn into notifications once the app has been asleep for a while:
 * in deep sleep (Doze) an optimised app gets no network, so the message can't be fetched. Element X asks to be
 * left unrestricted for the same reason; this does the same, plus a pointer to OPPO/OnePlus/realme's own setting.
 */
object Battery {
    private fun prefs(context: Context) = context.getSharedPreferences("battery", Context.MODE_PRIVATE)

    private val _bannerTick = MutableStateFlow(0)
    /** Changes whenever the banner should be re-evaluated (dismissed, or a message had to fall back). */
    val bannerTick: StateFlow<Int> = _bannerTick.asStateFlow()

    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Battery usage set to "Restricted" in Android's settings: Android then holds back every background job,
     * including the one that fetches a pushed message, until the app is opened.
     */
    fun isBackgroundRestricted(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true

    /**
     * Whether ntfy may run unrestricted. If it is optimised, Android cuts its connection to your ntfy server in
     * deep sleep and pushes arrive late or not at all. Android answers this for other apps too.
     */
    fun isNtfyUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(UnifiedPush.NTFY) == true

    /** ntfy's App info page, where Battery › Unrestricted (and on ColorOS "Allow background activity") is set. */
    fun openNtfyAppInfo(context: Context) {
        if (!launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${UnifiedPush.NTFY}")))) {
            launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** Opens ntfy itself (it offers to turn off its own battery optimisation). */
    fun openNtfy(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(UnifiedPush.NTFY) ?: return false
        return launch(context, intent)
    }

    /** OPPO, OnePlus and realme (ColorOS) add their own background limits on top of Android's. */
    val hasVendorLimits: Boolean
        get() = Build.MANUFACTURER.lowercase() in setOf("oppo", "oneplus", "realme")

    /** Opens Android's "let this app run in the background?" prompt, or the battery settings list as a fallback. */
    @SuppressLint("BatteryLife")
    fun requestUnrestricted(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (!launch(context, direct)) launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** App info, where ColorOS keeps "Battery › Allow background activity" and "Auto launch". */
    fun openAppInfo(context: Context) {
        launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** A pushed message couldn't be fetched in time: bring the banner back even if it was dismissed before. */
    fun noteMissedFetch(context: Context) {
        prefs(context).edit().putLong("missed_at", System.currentTimeMillis()).apply()
        _bannerTick.value++
    }

    fun dismissBanner(context: Context) {
        prefs(context).edit().putLong("dismissed_at", System.currentTimeMillis()).apply()
        _bannerTick.value++
    }

    /**
     * Show the home-screen banner: notifications are on, ElementCtrl (or ntfy, with push on) is held back by battery
     * settings, and it wasn't dismissed since the last miss.
     */
    fun shouldShowBanner(context: Context, notificationsOn: Boolean): Boolean {
        if (!notificationsOn) return false
        val ntfyHeldBack = Push.isEnabled(context) && UnifiedPush.isNtfyInstalled(context) && !isNtfyUnrestricted(context)
        if (isUnrestricted(context) && !isBackgroundRestricted(context) && !ntfyHeldBack) return false
        val p = prefs(context)
        val dismissed = p.getLong("dismissed_at", 0)
        return dismissed == 0L || p.getLong("missed_at", 0) > dismissed
    }
}
