package com.vknxworks.elementctrl.matrix

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class LinkPreview(val url: String, val title: String?, val description: String?, val imageUrl: String?, val siteName: String?) {
    val isEmpty get() = title.isNullOrBlank() && description.isNullOrBlank() && imageUrl == null
}

/**
 * Link previews fetched by the homeserver (/preview_url), so the phone never contacts the linked site.
 * Results, including "no preview", are cached for the session.
 */
object LinkPreviews {
    private val cache = LruCache<String, LinkPreview>(200)
    private val urlRegex = Regex("""https?://[^\s<>"]+[^\s<>".,;:!?)\]}']""")

    /** The first link in a message worth previewing (not matrix.to, which opens in-app). */
    fun firstUrl(text: String): String? = urlRegex.findAll(text).map { it.value }.firstOrNull { !it.contains("matrix.to/") }

    fun cached(url: String): LinkPreview? = cache.get(url)

    suspend fun load(session: MatrixSession, url: String): LinkPreview? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it.takeUnless { p -> p.isEmpty } }
        val preview = runCatching {
            val json = session.client.getUrlPreview(url, null) ?: return@runCatching null
            val o = JSONObject(json)
            LinkPreview(
                url = url,
                title = o.optString("og:title").ifBlank { null },
                description = o.optString("og:description").ifBlank { null },
                imageUrl = o.optString("og:image").takeIf { it.startsWith("mxc://") },
                siteName = o.optString("og:site_name").ifBlank { null },
            )
        }.getOrNull() ?: LinkPreview(url, null, null, null, null)
        cache.put(url, preview)
        preview.takeUnless { it.isEmpty }
    }
}
