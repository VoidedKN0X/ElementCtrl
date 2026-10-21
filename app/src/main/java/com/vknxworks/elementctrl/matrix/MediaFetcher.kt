package com.vknxworks.elementctrl.matrix

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import org.matrix.rustcomponents.sdk.MediaSource

/**
 * A picture stored on a Matrix server. Either a plain mxc:// URL (avatars) or the full JSON of an event's
 * media source, which carries the decryption keys for files in encrypted rooms.
 */
data class MxcImage(
    val url: String? = null,
    val sourceJson: String? = null,
    /** Ask the server for a thumbnail of about this size; 0 means the original file. */
    val size: Int = 0,
)

fun mxcAvatar(url: String?, size: Int = 96): MxcImage? = url?.takeIf { it.startsWith("mxc://") }?.let { MxcImage(url = it, size = size) }

/** Loads [MxcImage]s for Coil through the SDK, which handles authenticated media and decryption. */
class MediaFetcher(private val data: MxcImage, private val options: Options, private val session: MatrixSession) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val source = when {
            data.sourceJson != null -> MediaSource.fromJson(data.sourceJson)
            data.url != null -> MediaSource.fromUrl(data.url)
            else -> error("No media source")
        }
        val bytes = source.use { src ->
            val encrypted = data.sourceJson?.contains("\"file\"") == true
            if (data.size > 0 && !encrypted) {
                runCatching { session.client.getMediaThumbnail(src, data.size.toULong(), data.size.toULong()) }
                    .getOrElse { session.client.getMediaContent(src) }
            } else {
                session.client.getMediaContent(src)
            }
        }
        SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val session: () -> MatrixSession?) : Fetcher.Factory<MxcImage> {
        override fun create(data: MxcImage, options: Options, imageLoader: ImageLoader): Fetcher? =
            session()?.let { MediaFetcher(data, options, it) }
    }

    companion object {
        val keyer: Keyer<MxcImage> = object : Keyer<MxcImage> {
            // A full hash, so two different sources can never share a cache entry (and show each other's picture).
            override fun key(data: MxcImage, options: Options): String = "${data.url ?: data.sourceJson?.let(::sha256Hex)}@${data.size}"
        }
    }
}

internal fun sha256Hex(s: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** Downloading attachments so they can be opened in another app or saved to Downloads. */
object MediaFiles {
    private fun mediaDir(context: Context) = File(context.cacheDir, "media").apply { mkdirs() }

    /** Folders that hold decrypted copies of attachments and voice messages, and outgoing uploads. */
    private fun plainDirs(context: Context) = listOf(File(context.cacheDir, "media"), File(context.cacheDir, "upload"), File(context.cacheDir, "shared"))

    suspend fun download(context: Context, session: MatrixSession, sourceJson: String, filename: String, mime: String): File =
        withContext(Dispatchers.IO) {
            val dir = mediaDir(context)
            val safeName = filename.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(100)
                .takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: "file"
            // Named by a hash of the whole source (not a 32-bit hashCode), so different files can't collide.
            val target = File(dir, "${sha256Hex(sourceJson).take(32)}_$safeName")
            if (!target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) error("Bad file name")
            // Decrypted copies older than a day go now too, not only at start-up (the app can run for days).
            purgeOldThrottled(context)
            if (target.exists() && target.length() > 0) return@withContext target.also { it.setLastModified(System.currentTimeMillis()) }
            MediaSource.fromJson(sourceJson).use { src ->
                val bytes = session.client.getMediaContent(src)
                val tmp = File(dir, target.name + ".part")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(target)) { tmp.delete(); error("Couldn't store the file") }
            }
            target
        }

    /**
     * Decrypted attachments are only kept around long enough to be opened or shared: anything older than a day is
     * removed at start-up (and leftover uploads after an hour).
     */
    @Volatile private var lastPurge = 0L

    private fun purgeOldThrottled(context: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastPurge != 0L && now - lastPurge < 3_600_000L) return
        lastPurge = now
        runCatching { purgeOld(context) }
    }

    fun purgeOld(context: Context) {
        val now = System.currentTimeMillis()
        File(context.cacheDir, "media").listFiles()?.forEach { if (now - it.lastModified() > 24 * 3_600_000L) it.deleteRecursively() }
        File(context.cacheDir, "upload").listFiles()?.forEach { if (now - it.lastModified() > 3_600_000L) it.deleteRecursively() }
        File(context.cacheDir, "shared").listFiles()?.forEach { if (now - it.lastModified() > 24 * 3_600_000L) it.deleteRecursively() }
    }

    /** Removes every downloaded, decrypted or outgoing file (on sign-out). */
    fun wipeAll(context: Context) {
        plainDirs(context).forEach { it.deleteRecursively() }
    }

    fun open(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).setType(mime.ifBlank { "*/*" }).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Copies a downloaded file into the public Downloads folder. */
    suspend fun saveToDownloads(context: Context, file: File, filename: String, mime: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mime.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ElementCtrl")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            true
        }.getOrDefault(false)
    }
}
