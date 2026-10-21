package com.vknxworks.elementctrl.call

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps an ongoing call alive while you use other apps, the screen is off or ElementCtrl is locked, with the
 * ongoing-call notification and its Hang up button. Android 14 and newer need the foreground service types to
 * match what's in use: phone call always, plus microphone and camera once you've allowed them for the call.
 */
class CallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        val notification = CallNotifications.ongoing(this, CallManager.active.value)
        val started = runCatching {
            ServiceCompat.startForeground(this, CallNotifications.ID_ONGOING, notification, types)
        }.recoverCatching {
            // A type that isn't allowed right now (e.g. the app went to the background first): phone call only.
            ServiceCompat.startForeground(this, CallNotifications.ID_ONGOING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        }.isSuccess
        if (!started || CallManager.active.value == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    companion object {
        /** Starts (or updates the types of) the ongoing-call service. Call while the call screen is visible. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, CallService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallService::class.java)) }
        }
    }
}
