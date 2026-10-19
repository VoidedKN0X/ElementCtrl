package com.vknxworks.elementctrl

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import com.vknxworks.elementctrl.notify.Notifier

/**
 * Restarts the whole app process, the same thing a force stop and reopen does, for when the connection can't be
 * brought back any other way. A tiny activity in its own process kills the main one and opens the app again
 * (the approach of Jake Wharton's ProcessPhoenix).
 */
object AppRestart {
    private const val EXTRA_PID = "pid"
    private const val EXTRA_NEXT = "next"
    const val PROCESS_SUFFIX = ":restart"

    fun restart(context: Context, roomId: String? = null) {
        val next = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (roomId != null) next.putExtra(Notifier.EXTRA_ROOM_ID, roomId)
        context.startActivity(
            Intent(context, RestartActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_PID, Process.myPid())
                .putExtra(EXTRA_NEXT, next)
        )
    }

    /** True in the helper process, where the app itself must not start (no session, no databases). */
    fun isRestartProcess(): Boolean = Application.getProcessName().endsWith(PROCESS_SUFFIX)

    class RestartActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val pid = intent.getIntExtra(EXTRA_PID, -1)
            if (pid > 0 && pid != Process.myPid()) Process.killProcess(pid)
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>(EXTRA_NEXT)?.let { runCatching { startActivity(it) } }
            finish()
            Runtime.getRuntime().exit(0)
        }
    }
}
