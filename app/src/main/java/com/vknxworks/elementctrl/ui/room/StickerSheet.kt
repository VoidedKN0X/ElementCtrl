package com.vknxworks.elementctrl.ui.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.matrix.Gif
import com.vknxworks.elementctrl.matrix.GifSearch
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.MxcImage
import com.vknxworks.elementctrl.matrix.Sticker
import com.vknxworks.elementctrl.matrix.StickerPack
import com.vknxworks.elementctrl.matrix.Stickers
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bottom sheet with your sticker packs and, with a Tenor key set, GIF search. */
@Composable
fun StickerGifSheet(
    session: MatrixSession,
    roomId: String,
    onDismiss: () -> Unit,
    onSticker: (Sticker) -> Unit,
    onGif: (File) -> Unit,
) {
    val tenorKey by ElementCtrlApp.graph.prefs.tenorKey.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Stickers") }, icon = { Icon(Icons.Default.EmojiEmotions, null) })
            Tab(tab == 1, { tab = 1 }, text = { Text("GIFs") }, icon = { Icon(Icons.Default.Gif, null) })
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 560.dp)) {
            if (tab == 0) StickerTab(session, roomId, onSticker) else GifTab(tenorKey, onGif)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StickerTab(session: MatrixSession, roomId: String, onSticker: (Sticker) -> Unit) {
    var packs by remember { mutableStateOf<List<StickerPack>?>(null) }
    LaunchedEffect(roomId) { packs = runCatching { Stickers.packs(session, roomId) }.getOrDefault(emptyList()) }
    val p = packs
    when {
        p == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        p.isEmpty() -> Text(
            "No sticker packs yet. Long-press any picture in a chat and choose \"Add to my stickers\", or enable sticker packs from rooms in another Matrix app.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
        else -> LazyVerticalGrid(columns = GridCells.Adaptive(84.dp), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.forEach { pack ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h:${pack.id}") {
                    Text(pack.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
                items(pack.stickers, key = { "${pack.id}:${it.shortcode}" }) { st ->
                    AsyncImage(
                        model = remember(st.url) { MxcImage(url = st.url) },
                        contentDescription = st.body,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)).clickable { onSticker(st) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GifTab(apiKey: String, onGif: (File) -> Unit) {
    if (apiKey.isBlank()) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Gif, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("GIF search uses Tenor", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add your free Tenor API key in Settings › Chats to search GIFs here. You can always send GIFs from your gallery with the + button.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Gif>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        delay(350)
        loading = true
        runCatching { GifSearch.search(apiKey, query) }
            .onSuccess { results = it; error = null }
            .onFailure { error = it.message }
        loading = false
    }
    Column {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Search Tenor") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        if (loading && results.isEmpty()) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.id }) { g ->
                Box(contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = g.previewUrl,
                        contentDescription = g.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (g.width > 0 && g.height > 0) g.width.toFloat() / g.height else 1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = sending == null) {
                                sending = g.id
                                scope.launch {
                                    runCatching { GifSearch.download(context.cacheDir, g) }
                                        .onSuccess(onGif)
                                        .onFailure { error = "Couldn't download the GIF" }
                                    sending = null
                                }
                            },
                    )
                    if (sending == g.id) CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Powered by Tenor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
            }
        }
    }
}
