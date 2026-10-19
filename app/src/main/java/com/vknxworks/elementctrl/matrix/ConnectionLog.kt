package com.vknxworks.elementctrl.matrix

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small on-device log of what the connection did: sync states, the app coming and going, network changes and
 * every start, stop and restart with how long it took. Kept so a "lost connection" can be explained afterwards
 * (Settings › About › Connection log). Holds no message content, room or user names, servers or tokens.
 */
object ConnectionLog {
    private const val MAX_BYTES = 96 * 1024
    private val time = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()
    @Volatile private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, "connection.log")
    }

    fun log(message: String) {
        val f = file ?: return
        val line = "${synchronized(time) { time.format(Date()) }} $message\n"
        synchronized(lock) {
            runCatching {
                f.appendText(line)
                if (f.length() > MAX_BYTES) {
                    // Keep the newer half.
                    val text = f.readText()
                    f.writeText(text.substring(text.length / 2).substringAfter('\n'))
                }
            }
        }
    }

    fun read(): String = synchronized(lock) { runCatching { file?.readText() }.getOrNull().orEmpty() }

    fun clear() = synchronized(lock) { runCatching { file?.delete() }; Unit }
}
