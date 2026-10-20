package com.vknxworks.elementctrl.notify

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.core.graphics.drawable.IconCompat
import com.vknxworks.elementctrl.matrix.MatrixSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.matrix.rustcomponents.sdk.MediaSource

/**
 * Profile and room pictures for notifications. They come through the Matrix SDK, which keeps a copy in its own
 * encrypted media cache, so after the first time they load from the phone even when the app has been asleep;
 * the round, decoded versions are also kept in memory while the app runs. Nothing extra is written to storage.
 */
object NotificationAvatars {
    private const val SIZE = 128
    private val memory = LruCache<String, Bitmap>(64)

    fun cached(url: String?): Bitmap? = url?.let { synchronized(memory) { memory.get(it) } }

    /** Loads (or returns the cached) round picture, giving up after a few seconds so a notification is never held up. */
    suspend fun load(session: MatrixSession, url: String?): Bitmap? {
        if (url == null || !url.startsWith("mxc://")) return null
        cached(url)?.let { return it }
        val bitmap = withTimeoutOrNull(4_000) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = MediaSource.fromUrl(url).use { src ->
                        runCatching { session.client.getMediaThumbnail(src, SIZE.toULong(), SIZE.toULong()) }
                            .getOrElse { session.client.getMediaContent(src) }
                    }
                    decodeRound(bytes)
                }.getOrNull()
            }
        } ?: return null
        synchronized(memory) { memory.put(url, bitmap) }
        return bitmap
    }

    fun icon(url: String?): IconCompat? = cached(url)?.let { IconCompat.createWithBitmap(it) }

    private fun decodeRound(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= SIZE && bounds.outHeight / (sample * 2) >= SIZE) sample *= 2
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // Centre-crop to a square and round it, the way avatars look everywhere else in the app.
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, paint)
        return out
    }

    fun clear() = synchronized(memory) { memory.evictAll() }
}
