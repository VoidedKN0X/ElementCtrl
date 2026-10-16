package com.vknxworks.elementctrl.matrix

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.MediaSource

data class Sticker(val shortcode: String, val url: String, val body: String, val width: Int, val height: Int, val mime: String)
data class StickerPack(val id: String, val name: String, val stickers: List<Sticker>)

/**
 * Sticker packs in the shared Matrix "image pack" format (MSC2545, used by Element, Cinny, FluffyChat…):
 * your personal pack in account data, packs you've enabled from rooms, and the open room's own packs.
 */
object Stickers {
    private const val USER_PACK = "im.ponies.user_emotes"
    private const val EMOTE_ROOMS = "im.ponies.emote_rooms"
    private const val ROOM_PACK = "im.ponies.room_emotes"

    suspend fun packs(session: MatrixSession, roomId: String?): List<StickerPack> = withContext(Dispatchers.IO) {
        val out = ArrayList<StickerPack>()
        runCatching { session.client.accountData(USER_PACK) }.getOrNull()?.let { json ->
            parsePack("me", "My stickers", JSONObject(json))?.let(out::add)
        }
        // Room packs: the ones you've enabled everywhere, plus this room's.
        val refs = LinkedHashSet<Pair<String, String>>()
        runCatching { session.client.accountData(EMOTE_ROOMS) }.getOrNull()?.let { json ->
            val rooms = JSONObject(json).optJSONObject("rooms") ?: JSONObject()
            rooms.keys().forEach { rid -> rooms.optJSONObject(rid)?.keys()?.forEach { key -> refs += rid to key } }
        }
        // The room's default pack has an empty state key.
        if (roomId != null) refs += roomId to ""

        for ((rid, key) in refs) {
            runCatching {
                val path = "/_matrix/client/v3/rooms/${MatrixHttp.enc(rid)}/state/$ROOM_PACK/${MatrixHttp.enc(key)}"
                val (code, json) = MatrixHttp.request(session, "GET", path, returnErrors = true)
                if (code == 200) {
                    val name = json.optJSONObject("pack")?.optString("display_name")?.ifBlank { null }
                        ?: session.rooms.value.firstOrNull { it.roomId == rid }?.name ?: "Room stickers"
                    parsePack("$rid/$key", name, json)?.let(out::add)
                }
            }
        }
        out.distinctBy { it.id }
    }

    private fun parsePack(id: String, fallbackName: String, o: JSONObject): StickerPack? {
        val images = o.optJSONObject("images") ?: return null
        val packUsage = o.optJSONObject("pack")?.optJSONArray("usage")?.toStrings()
        val stickers = images.keys().asSequence().mapNotNull { code ->
            val img = images.optJSONObject(code) ?: return@mapNotNull null
            val usage = img.optJSONArray("usage")?.toStrings() ?: packUsage
            // Packs marked emoticon-only aren't stickers.
            if (usage != null && "sticker" !in usage) return@mapNotNull null
            val url = img.optString("url").takeIf { it.startsWith("mxc://") } ?: return@mapNotNull null
            val info = img.optJSONObject("info")
            Sticker(
                shortcode = code,
                url = url,
                body = img.optString("body").ifBlank { code },
                width = info?.optInt("w") ?: 0,
                height = info?.optInt("h") ?: 0,
                mime = info?.optString("mimetype")?.ifBlank { null } ?: "image/png",
            )
        }.toList()
        if (stickers.isEmpty()) return null
        return StickerPack(id, o.optJSONObject("pack")?.optString("display_name")?.ifBlank { null } ?: fallbackName, stickers)
    }

    private fun JSONArray.toStrings() = (0 until length()).map { getString(it) }

    /** Sends an m.sticker event; the SDK encrypts it in encrypted rooms. */
    suspend fun send(session: MatrixSession, roomId: String, sticker: Sticker) = withContext(Dispatchers.IO) {
        val room = session.getRoom(roomId) ?: error("Room not available")
        val content = JSONObject()
            .put("body", sticker.body)
            .put("url", sticker.url)
            .put(
                "info",
                JSONObject().put("mimetype", sticker.mime).apply {
                    if (sticker.width > 0) put("w", sticker.width)
                    if (sticker.height > 0) put("h", sticker.height)
                },
            )
        room.use { it.sendRaw("m.sticker", content.toString()) }
    }

    /**
     * Adds a picture from a chat to your personal sticker pack. Pictures from encrypted rooms are stored
     * encrypted on the server, so they're uploaded again as a plain sticker image first.
     */
    suspend fun saveToMyPack(session: MatrixSession, sourceJson: String, name: String, mime: String, width: Int, height: Int) = withContext(Dispatchers.IO) {
        val src = JSONObject(sourceJson)
        val plainUrl = src.optString("url").takeIf { it.startsWith("mxc://") && !src.has("file") }
        val url = plainUrl ?: run {
            val bytes = MediaSource.fromJson(sourceJson).use { session.client.getMediaContent(it) }
            session.client.uploadMedia(mime, bytes, null)
        }
        val current = runCatching { session.client.accountData(USER_PACK) }.getOrNull()?.let { JSONObject(it) } ?: JSONObject()
        val images = current.optJSONObject("images") ?: JSONObject().also { current.put("images", it) }
        var code = name.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "sticker" }
        var n = 2
        val base = code
        while (images.has(code)) code = "${base}_${n++}"
        images.put(
            code,
            JSONObject()
                .put("url", url)
                .put("body", name)
                .put("usage", JSONArray().put("sticker"))
                .put("info", JSONObject().put("mimetype", mime).apply { if (width > 0) put("w", width); if (height > 0) put("h", height) }),
        )
        if (!current.has("pack")) current.put("pack", JSONObject().put("display_name", "My stickers"))
        session.client.setAccountData(USER_PACK, current.toString())
    }

    suspend fun removeFromMyPack(session: MatrixSession, shortcode: String) = withContext(Dispatchers.IO) {
        val current = session.client.accountData(USER_PACK)?.let { JSONObject(it) } ?: return@withContext
        current.optJSONObject("images")?.remove(shortcode)
        session.client.setAccountData(USER_PACK, current.toString())
    }
}

data class Gif(val id: String, val previewUrl: String, val url: String, val width: Int, val height: Int, val title: String)

/** GIF search through Tenor, with your own free API key (set in Settings › Chats). */
object GifSearch {
    suspend fun search(apiKey: String, query: String): List<Gif> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val endpoint = if (q.isEmpty()) "featured?" else "search?q=${URLEncoder.encode(q, "UTF-8")}&"
        val url = URL("https://tenor.googleapis.com/v2/${endpoint}key=${URLEncoder.encode(apiKey, "UTF-8")}&client_key=elementctrl&limit=40&media_filter=gif,tinygif&contentfilter=medium")
        val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 15_000 }
        try {
            if (conn.responseCode != 200) error(if (conn.responseCode == 400 || conn.responseCode == 403) "Tenor rejected the API key" else "Tenor error ${conn.responseCode}")
            val o = JSONObject(conn.inputStream.bufferedReader().readText())
            val results = o.optJSONArray("results") ?: JSONArray()
            (0 until results.length()).mapNotNull { i ->
                val r = results.getJSONObject(i)
                val formats = r.optJSONObject("media_formats") ?: return@mapNotNull null
                val gif = formats.optJSONObject("gif") ?: return@mapNotNull null
                val tiny = formats.optJSONObject("tinygif") ?: gif
                val dims = gif.optJSONArray("dims")
                Gif(
                    id = r.optString("id"),
                    previewUrl = tiny.optString("url"),
                    url = gif.optString("url"),
                    width = dims?.optInt(0) ?: 0,
                    height = dims?.optInt(1) ?: 0,
                    title = r.optString("content_description").ifBlank { "GIF" },
                )
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads the full GIF so it can be sent like any picture. */
    suspend fun download(cacheDir: File, gif: Gif): File = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "upload").apply { mkdirs() }
        val out = File(dir, "gif-${gif.id}.gif")
        if (!out.exists()) {
            val conn = URL(gif.url).openConnection() as HttpURLConnection
            try { conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } } } finally { conn.disconnect() }
        }
        out
    }
}
