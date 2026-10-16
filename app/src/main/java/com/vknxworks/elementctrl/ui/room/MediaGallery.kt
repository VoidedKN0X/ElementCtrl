package com.vknxworks.elementctrl.ui.room

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.MxcImage
import com.vknxworks.elementctrl.matrix.TimelineController
import com.vknxworks.elementctrl.matrix.TimelineEntry
import com.vknxworks.elementctrl.matrix.UiContent
import com.vknxworks.elementctrl.ui.EmptyState
import com.vknxworks.elementctrl.ui.formatDuration
import com.vknxworks.elementctrl.ui.formatSize
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** A media-only timeline of one room, kept while the gallery is open. */
class MediaGalleryViewModel(val session: MatrixSession, roomId: String) : ViewModel() {
    private val sdkRoom = session.getRoom(roomId)
    val timeline: TimelineController? = sdkRoom?.let { TimelineController(session, it, mediaOnly = true) }

    init { timeline?.start() }

    override fun onCleared() {
        timeline?.close()
        session.scope.launch {
            kotlinx.coroutines.delay(1000)
            runCatching { sdkRoom?.close() }
        }
    }
}

private sealed interface GalleryRow {
    val key: String
    data class Header(override val key: String, val label: String) : GalleryRow
    data class Item(override val key: String, val message: TimelineEntry.Message) : GalleryRow
}

private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

private fun monthKey(ts: Long): Int = Calendar.getInstance().apply { timeInMillis = ts }.let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }

/** Newest first, with a heading for every month. */
private fun groupByMonth(items: List<TimelineEntry.Message>): List<GalleryRow> {
    val out = ArrayList<GalleryRow>()
    var last = Int.MIN_VALUE
    for (m in items) {
        val k = monthKey(m.timestamp)
        if (k != last) {
            out += GalleryRow.Header("h$k", monthFormat.format(Date(m.timestamp)))
            last = k
        }
        out += GalleryRow.Item(m.key, m)
    }
    return out
}

/** Every photo, video, file and voice message shared in a room or direct chat, by month. */
@Composable
fun MediaGalleryScreen(
    session: MatrixSession,
    roomId: String,
    onBack: () -> Unit,
    onOpenImage: (MediaRequest) -> Unit,
) {
    val vm: MediaGalleryViewModel = viewModel(key = "gallery:$roomId") { MediaGalleryViewModel(session, roomId) }
    val timeline = vm.timeline
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Media and files") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Media") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Files") })
                }
            }
        },
    ) { pad ->
        if (timeline == null) {
            EmptyState(Icons.Default.PhotoLibrary, "Not available yet", "This chat may still be syncing.", Modifier.fillMaxSize().padding(pad))
            return@Scaffold
        }
        val entries by timeline.entries.collectAsStateWithLifecycle()
        val paginating by timeline.paginating.collectAsStateWithLifecycle()
        val atStart by timeline.atStart.collectAsStateWithLifecycle()

        val messages = remember(entries) {
            entries.asReversed().filterIsInstance<TimelineEntry.Message>().filter { it.content !is UiContent.Redacted }
        }
        val media = remember(messages) { groupByMonth(messages.filter { it.content is UiContent.Image || it.content is UiContent.Video }) }
        val files = remember(messages) { groupByMonth(messages.filter { it.content is UiContent.File || it.content is UiContent.Audio }) }
        val shown = if (tab == 0) media else files

        // The server pages through every event, not just media, so keep loading until there's a screenful.
        LaunchedEffect(tab, shown.size, paginating, atStart) {
            if (!atStart && !paginating && shown.size < 40) timeline.paginateBack()
        }

        val open: (TimelineEntry.Message) -> Unit = { openMedia(context, session, it, onOpenImage, scope, snackbar) }
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                shown.isEmpty() && atStart -> EmptyState(
                    if (tab == 0) Icons.Default.Image else Icons.Default.FolderOpen,
                    if (tab == 0) "No photos or videos yet" else "No files yet",
                    if (tab == 0) "Photos and videos shared in this chat show up here." else "Files, audio and voice messages shared in this chat show up here.",
                )
                shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                tab == 0 -> MediaGrid(media, rememberLazyGridState(), loadingMore = !atStart, onEnd = { timeline.paginateBack() }, onOpen = open)
                else -> FileList(files, rememberLazyListState(), loadingMore = !atStart, onEnd = { timeline.paginateBack() }, onOpen = open)
            }
        }
    }
}

@Composable
private fun MediaGrid(rows: List<GalleryRow>, state: LazyGridState, loadingMore: Boolean, onEnd: () -> Unit, onOpen: (TimelineEntry.Message) -> Unit) {
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 9 } }
    LaunchedEffect(nearEnd, rows.size) { if (nearEnd && loadingMore) onEnd() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        state = state,
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        rows.forEach { row ->
            when (row) {
                is GalleryRow.Header -> item(key = row.key, span = { GridItemSpan(maxLineSpan) }) { MonthHeader(row.label) }
                is GalleryRow.Item -> item(key = row.key) { MediaTile(row.message, onOpen) }
            }
        }
        if (loadingMore) item(key = "more", span = { GridItemSpan(maxLineSpan) }) { LoadingMore() }
    }
}

@Composable
private fun MediaTile(m: TimelineEntry.Message, onOpen: (TimelineEntry.Message) -> Unit) {
    val (source, duration) = when (val c = m.content) {
        is UiContent.Image -> (c.thumbnailJson ?: c.sourceJson) to null
        is UiContent.Video -> c.thumbnailJson to c.durationMs
        else -> null to null
    }
    Box(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { onOpen(m) },
    ) {
        if (source != null) {
            AsyncImage(
                model = remember(source) { MxcImage(sourceJson = source, size = 320) },
                contentDescription = (m.content as? UiContent.Image)?.caption ?: "Photo from ${m.senderName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (m.content is UiContent.Video) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(5.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, "Video", Modifier.size(14.dp), tint = Color.White)
                if (duration != null && duration > 0) {
                    Spacer(Modifier.width(2.dp))
                    Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FileList(rows: List<GalleryRow>, state: LazyListState, loadingMore: Boolean, onEnd: () -> Unit, onOpen: (TimelineEntry.Message) -> Unit) {
    val nearEnd by remember { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.layoutInfo.totalItemsCount - 4 } }
    LaunchedEffect(nearEnd, rows.size) { if (nearEnd && loadingMore) onEnd() }
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
        rows.forEach { row ->
            when (row) {
                is GalleryRow.Header -> item(key = row.key) { MonthHeader(row.label) }
                is GalleryRow.Item -> item(key = row.key) { FileRow(row.message, onOpen) }
            }
        }
        if (loadingMore) item(key = "more") { LoadingMore() }
    }
}

@Composable
private fun FileRow(m: TimelineEntry.Message, onOpen: (TimelineEntry.Message) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (icon, title, detail) = when (val c = m.content) {
        is UiContent.Audio -> Triple(
            if (c.isVoice) Icons.Default.Mic else Icons.Default.AudioFile,
            if (c.isVoice) "Voice message" else c.filename,
            formatDuration(c.durationMs),
        )
        is UiContent.File -> Triple(Icons.AutoMirrored.Filled.InsertDriveFile, c.filename, if (c.size > 0) formatSize(c.size) else null)
        else -> Triple(Icons.AutoMirrored.Filled.InsertDriveFile, "File", null)
    }
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(m) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(cs.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.onSecondaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(detail, m.senderName, dayFormat.format(Date(m.timestamp))).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MonthHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun LoadingMore() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
