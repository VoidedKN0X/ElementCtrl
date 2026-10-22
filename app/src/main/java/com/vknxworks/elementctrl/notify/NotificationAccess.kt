package com.vknxworks.elementctrl.notify

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Whether Android lets ElementCtrl show message notifications at all. On Android 13 and newer that needs the
 * notification permission, which apps have to ask for; the person can also turn notifications (or just the
 * Messages channel) off in Android's settings. When any of that is off, pushes still arrive but nothing is shown.
 */
object NotificationAccess {
    private fun prefs(context: Context) = context.getSharedPreferences("notification_access", Context.MODE_PRIVATE)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Permission granted, notifications on for the app, and the Messages channel not switched off. */
    fun allowed(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(Notifier.CHANNEL_MESSAGES)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** The permission prompt is shown automatically only once, right after sign-in; after that it's the person's call. */
    fun askedAutomatically(context: Context): Boolean = prefs(context).getBoolean("asked", false)
    fun markAskedAutomatically(context: Context) { prefs(context).edit().putBoolean("asked", true).apply() }

    /** ElementCtrl's notification settings in Android, for when the prompt won't show any more. */
    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn()
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
