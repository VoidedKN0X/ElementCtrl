package com.vknxworks.elementctrl.ui.room

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxHeight
import com.vknxworks.elementctrl.ui.formatDuration
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.DisposableEffect
import com.vknxworks.elementctrl.notify.OpenRoom
import com.vknxworks.elementctrl.notify.PushNotifications
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.vknxworks.elementctrl.ui.glassIsland
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.MediaFiles
import com.vknxworks.elementctrl.matrix.MemberInfo
import com.vknxworks.elementctrl.matrix.SendStatus
import com.vknxworks.elementctrl.matrix.TimelineEntry
import com.vknxworks.elementctrl.matrix.UiContent
import com.vknxworks.elementctrl.matrix.previewText
import com.vknxworks.elementctrl.ui.Avatar
import com.vknxworks.elementctrl.ui.CenteredLoading
import com.vknxworks.elementctrl.ui.ConfirmDialog
import com.vknxworks.elementctrl.ui.DeepLinks
import com.vknxworks.elementctrl.ui.SettingRow
import com.vknxworks.elementctrl.ui.TextInputDialog
import com.vknxworks.elementctrl.ui.copyText
import com.vknxworks.elementctrl.ui.shareText
import kotlinx.coroutines.launch
import org.matrix.rustcomponents.sdk.Membership

private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "🎉", "👀")

/** What the composer is doing besides a plain new message. */
private sealed interface ComposerMode {
    data object Normal : ComposerMode
    data class Reply(val item: TimelineEntry.Message) : ComposerMode
    data class Edit(val item: TimelineEntry.Message) : ComposerMode
}

@Composable
fun RoomScreen(
    session: MatrixSession,
    roomId: String,
    threadRootId: String?,
    onBack: () -> Unit,
    onDetails: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenImage: (MediaRequest) -> Unit,
    onOpenRoom: (String) -> Unit,
    onSecurity: () -> Unit,
    onSearch: () -> Unit = {},
    focusEventId: String? = null,
    onJumpToLatest: () -> Unit = {},
) {
    val vm: RoomViewModel = viewModel(key = "room:$roomId:${threadRootId.orEmpty()}:${focusEventId.orEmpty()}") {
        RoomViewModel(session, roomId, threadRootId, focusEventId = focusEventId)
    }
    val room = vm.room
    val timeline = vm.timeline
    if (room == null || timeline == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Room") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("This room isn't available yet. It may still be syncing.", modifier = Modifier.padding(32.dp))
            }
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val header by room.header.collectAsStateWithLifecycle()
    val typing by room.typing.collectAsStateWithLifecycle()
    val permissions by room.permissions.collectAsStateWithLifecycle()
    val members by room.members.collectAsStateWithLifecycle()
    val entries by timeline.entries.collectAsStateWithLifecycle()
    val paginating by timeline.paginating.collectAsStateWithLifecycle()
    val atStart by timeline.atStart.collectAsStateWithLifecycle()
    val timelineError by timeline.error.collectAsStateWithLifecycle()
    val roomError by room.error.collectAsStateWithLifecycle()
    val sendOnEnter by ElementCtrlApp.graph.prefs.sendOnEnter.collectAsStateWithLifecycle()
    val showState by ElementCtrlApp.graph.prefs.showStateEvents.collectAsStateWithLifecycle()
    val verification by session.verification.collectAsStateWithLifecycle()
    val recovery by session.recovery.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val bubbleStyle by ElementCtrlApp.graph.prefs.bubbleStyle.collectAsStateWithLifecycle()
    val haze = remember { HazeState() }
    val density = LocalDensity.current
    var islandHeight by remember { mutableStateOf(88.dp) }
    var composer by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var mode by remember { mutableStateOf<ComposerMode>(ComposerMode.Normal) }
    val mentions = remember { mutableStateListOf<String>() }
    var sheetFor by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var deleting by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var reporting by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    var creatingPoll by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<Uri?>(null) }
    var suspiciousLink by remember { mutableStateOf<Pair<String, String>?>(null) }
    var highlighted by remember { mutableStateOf<String?>(null) }
    var emojiPickerFor by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    var stickerSheet by remember { mutableStateOf(false) }
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    val levels by recorder.levels.collectAsStateWithLifecycle()
    val elapsed by recorder.elapsed.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { recorder.cancel(); AudioPlayer.stop() } }
    val linkPreviews by ElementCtrlApp.graph.prefs.linkPreviews.collectAsStateWithLifecycle()
    val linkPreviewsE2ee by ElementCtrlApp.graph.prefs.linkPreviewsEncrypted.collectAsStateWithLifecycle()
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recording = recorder.start()
        else scope.launch { snackbar.showSnackbar("Allow the microphone to record voice messages") }
    }

    LaunchedEffect(timelineError) { timelineError?.let { snackbar.showSnackbar(it); timeline.clearError() } }
    LaunchedEffect(roomError) { roomError?.let { snackbar.showSnackbar(it); room.clearError() } }
    LaunchedEffect(Unit) { room.loadMembers() }

    // Push notifications for this chat stay quiet while it's on screen.
    DisposableEffect(roomId) {
        OpenRoom.id = roomId
        PushNotifications.forget(roomId)
        onDispose { if (OpenRoom.id == roomId) OpenRoom.id = null }
    }

    // Drafts: restore what was typed last time, keep saving while typing, and save once more on leaving.
    var draftLoaded by remember { mutableStateOf(false) }
    var pendingReplyId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (focusEventId == null && composer.text.isEmpty() && DeepLinks.share.value == null) {
            room.loadDraft(threadRootId)?.let { d ->
                composer = TextFieldValue(d.plainText, TextRange(d.plainText.length))
                (d.draftType as? org.matrix.rustcomponents.sdk.ComposerDraftType.Reply)?.let { pendingReplyId = it.eventId }
            }
        }
        draftLoaded = true
    }
    LaunchedEffect(pendingReplyId, entries) {
        val id = pendingReplyId ?: return@LaunchedEffect
        (entries.firstOrNull { it is TimelineEntry.Message && it.eventId == id } as? TimelineEntry.Message)?.let {
            mode = ComposerMode.Reply(it); pendingReplyId = null
        }
    }
    LaunchedEffect(composer.text, mode) {
        if (!draftLoaded || focusEventId != null) return@LaunchedEffect
        kotlinx.coroutines.delay(800)
        room.saveDraft(composer.text, (mode as? ComposerMode.Reply)?.item?.eventId, (mode as? ComposerMode.Edit)?.item?.eventId, threadRootId)
    }
    val latestComposer by androidx.compose.runtime.rememberUpdatedState(composer.text)
    val latestMode by androidx.compose.runtime.rememberUpdatedState(mode)
    DisposableEffect(Unit) {
        onDispose {
            if (draftLoaded && focusEventId == null) {
                room.saveDraft(latestComposer, (latestMode as? ComposerMode.Reply)?.item?.eventId, (latestMode as? ComposerMode.Edit)?.item?.eventId, threadRootId)
            }
        }
    }

    // Something shared from another app waits here for the user to send it.
    LaunchedEffect(Unit) {
        DeepLinks.share.value?.let { shared ->
            DeepLinks.share.value = null
            shared.text?.let { composer = TextFieldValue(it, TextRange(it.length)) }
            shared.uri?.takeIf { com.vknxworks.elementctrl.ui.isForeignContentUri(context, it) }?.let { pendingAttachment = it }
        }
    }

    val visible = remember(entries, showState) {
        entries.filter { e -> showState || !(e is TimelineEntry.Message && e.isStateLike) }.asReversed()
    }
    val listState = rememberLazyListState()

    // Mark read when the newest message is on screen.
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }

    // Follow new messages while the newest one is in view; stop following once the user scrolls up.
    // Only scrolling changes this, so a message arriving (which shifts the list) doesn't count as scrolling away.
    var followLatest by remember { mutableStateOf(true) }
    var unseenBelow by remember { mutableStateOf(0) }
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { (scrolling, index, offset) ->
                if (scrolling) followLatest = index == 0 && offset < 120
                if (index == 0 && offset < 120) unseenBelow = 0
            }
    }
    val newestKey = visible.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (newestKey == null) return@LaunchedEffect
        if (followLatest) listState.animateScrollToItem(0) else unseenBelow++
    }
    LaunchedEffect(atBottom, entries.size) { if (atBottom && entries.isNotEmpty()) timeline.markAsRead() }

    // Load older messages when scrolled near the top.
    // In a timeline opened at a search result: load newer messages at the bottom, and start on that message.
    LaunchedEffect(atBottom, entries.size) { if (focusEventId != null && atBottom) timeline.paginateForward() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(visible.size) {
        if (focusEventId != null && !focusedOnce) {
            val i = visible.indexOfFirst { it is TimelineEntry.Message && it.eventId == focusEventId }
            if (i >= 0) {
                focusedOnce = true
                followLatest = false
                listState.scrollToItem(i)
                highlighted = focusEventId
                kotlinx.coroutines.delay(2000)
                highlighted = null
            }
        }
    }

    val nearTop by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= visible.size - 8 } }
    LaunchedEffect(nearTop, visible.size) { if (nearTop && !atStart) timeline.paginateBack() }

    // Jump to a replied-to message if it's loaded.
    fun jumpTo(eventId: String) {
        val index = visible.indexOfFirst { it is TimelineEntry.Message && it.eventId == eventId }
        if (index >= 0) {
            scope.launch { listState.animateScrollToItem(index); highlighted = eventId; kotlinx.coroutines.delay(1500); highlighted = null }
        } else {
            scope.launch { snackbar.showSnackbar("That message isn't loaded. Scroll up to find it.") }
        }
    }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { pendingAttachment = it } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { pendingAttachment = it } }

    fun send() {
        val text = composer.text.trim()
        if (text.isEmpty()) return
        val usedMentions = mentions.filter { id -> composer.text.contains(id) }
        when (val m = mode) {
            is ComposerMode.Edit -> timeline.edit(m.item, text, usedMentions)
            is ComposerMode.Reply -> timeline.send(text, m.item.eventId, usedMentions)
            ComposerMode.Normal -> if (text.startsWith("/me ")) timeline.send(text.removePrefix("/me "), null, usedMentions, emote = true)
            else timeline.send(text, null, usedMentions)
        }
        composer = TextFieldValue("")
        mentions.clear()
        mode = ComposerMode.Normal
        room.onComposerChanged("")
        followLatest = true
        scope.launch { listState.animateScrollToItem(0) }
    }

    val actions = run {
        MessageActions(
            onLongPress = { sheetFor = it },
            onReact = { item, key -> timeline.toggleReaction(item, key) },
            onOpenMedia = { item -> openMedia(context, session, item, onOpenImage, scope, snackbar) },
            onOpenUser = onOpenUser,
            onOpenThread = { item -> item.eventId?.let(onOpenThread) },
            onJumpToReply = { jumpTo(it) },
            onVote = { item, answer -> item.eventId?.let { timeline.vote(it, answer) } },
            onRetry = { timeline.retry(it) },
            deviceUnverified = verification == org.matrix.rustcomponents.sdk.VerificationState.UNVERIFIED,
            recoveryIncomplete = recovery == org.matrix.rustcomponents.sdk.RecoveryState.INCOMPLETE,
            onFixDecryption = onSecurity,
            onToggleAudio = { item ->
                (item.content as? UiContent.Audio)?.let { a -> AudioPlayer.toggle(context, session, item.key, a.sourceJson, a.filename, a.mime) }
            },
            loadPreview = if (linkPreviews && (header?.isEncrypted != true || linkPreviewsE2ee)) {
                { url -> com.vknxworks.elementctrl.matrix.LinkPreviews.load(session, url) }
            } else null,
            onOpenLink = { com.vknxworks.elementctrl.ui.openUrl(context, it) },
            onSuspiciousLink = { url, shown -> suspiciousLink = url to shown },
        )
    }

    val isDm = header?.isDm == true
    val membership = header?.membership

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = threadRootId == null, onClick = onDetails),
                    ) {
                        Avatar(header?.name ?: "", header?.avatarUrl, header?.dmUserId ?: roomId, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (threadRootId != null) "Thread" else header?.name ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = when {
                                typing.isNotEmpty() -> typing.joinToString(limit = 2, truncated = "others") + if (typing.size == 1) " is typing…" else " are typing…"
                                threadRootId != null -> header?.name
                                isDm -> if (header?.isEncrypted == true) "Encrypted chat" else null
                                else -> header?.joinedMembers?.let { "$it members" }
                            }
                            subtitle?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (typing.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search messages") }
                    if (threadRootId == null) IconButton(onClick = onDetails) { Icon(Icons.Default.Info, "Room info") }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (entries.isEmpty() && !atStart) {
                CenteredLoading()
            }
            CompositionLocalProvider(LocalBubbleStyle provides bubbleStyle) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize().hazeSource(haze),
                    // Messages scroll on under the floating composer, so leave room for it at the bottom.
                    contentPadding = PaddingValues(top = 8.dp, bottom = islandHeight + 8.dp),
                ) {
                    itemsIndexed(visible, key = { _, e -> e.key }, contentType = { _, e -> e::class.simpleName }) { index, entry ->
                        when (entry) {
                            is TimelineEntry.Day -> DayDivider(entry.timestamp)
                            is TimelineEntry.ReadMarker -> if (index > 0) ReadMarkerLine()
                            is TimelineEntry.Start -> RoomStart(header?.name, isDm, header?.isEncrypted == true)
                            is TimelineEntry.Message -> {
                                val older = visible.getOrNull(index + 1)
                                val showSender = older !is TimelineEntry.Message || older.sender != entry.sender || older.isStateLike ||
                                    entry.timestamp - older.timestamp > 5 * 60_000
                                MessageRow(entry, showSender, isDm, actions, highlighted = highlighted != null && highlighted == entry.eventId)
                            }
                        }
                    }
                    if (paginating && !atStart) {
                        item("paginating") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
            header?.successorRoomId?.let { successor ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopCenter),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("This room has been upgraded.", Modifier.weight(1f))
                        TextButton(onClick = { onOpenRoom(successor) }) { Text("Go to new room") }
                    }
                }
            }
            if (focusEventId != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Viewing earlier messages", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onJumpToLatest) { Text("Jump to latest") }
                    }
                }
            }
            if (!atBottom || focusEventId != null) {
                BadgedBox(
                    badge = { if (unseenBelow > 0) Badge { Text(if (unseenBelow > 99) "99+" else "$unseenBelow") } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = islandHeight + 12.dp),
                ) {
                    SmallFloatingActionButton(onClick = {
                        if (focusEventId != null) {
                            onJumpToLatest()
                        } else {
                            followLatest = true
                            unseenBelow = 0
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    }) { Icon(Icons.Default.KeyboardArrowDown, "Jump to latest") }
                }
            }
            // The composer island floats above the messages, the navigation bar and the keyboard.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { islandHeight = with(density) { it.height.toDp() } }
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp, top = 6.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    membership == Membership.INVITED -> InviteBar(header?.inviterName, haze, onAccept = { room.join() }, onDecline = { room.leave(onBack) })
                    membership == Membership.LEFT || membership == Membership.BANNED -> {
                        IslandNote(if (membership == Membership.BANNED) "You were banned from this room." else "You left this room.", haze)
                    }
                    !permissions.canSend -> IslandNote("You don't have permission to post here.", haze)
                    else -> Composer(
                        haze = haze,
                        value = composer,
                        onValueChange = {
                            composer = it
                            room.onComposerChanged(it.text)
                        },
                        mode = mode,
                        onCancelMode = {
                            if (mode is ComposerMode.Edit) composer = TextFieldValue("")
                            mode = ComposerMode.Normal
                        },
                        members = members,
                        onMention = { member ->
                            val text = composer.text
                            val cursor = composer.selection.start
                            val at = text.lastIndexOf('@', (cursor - 1).coerceAtLeast(0))
                            if (at >= 0) {
                                val link = "[${member.name}](https://matrix.to/#/${member.userId}) "
                                val newText = text.substring(0, at) + link + text.substring(cursor)
                                composer = TextFieldValue(newText, TextRange(at + link.length))
                                mentions += member.userId
                            }
                        },
                        sendOnEnter = sendOnEnter,
                        onSend = { send() },
                        onAttach = { attachMenu = true },
                        attachMenu = attachMenu,
                        onDismissAttach = { attachMenu = false },
                        onPickMedia = { attachMenu = false; pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        onPickFile = { attachMenu = false; pickFile.launch(arrayOf("*/*")) },
                        onPoll = { attachMenu = false; creatingPoll = true },
                        recording = recording,
                        levels = levels,
                        elapsed = elapsed,
                        onMic = {
                            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                recording = recorder.start()
                                if (!recording) scope.launch { snackbar.showSnackbar("Couldn't start the microphone") }
                            } else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                        },
                        onCancelRecording = { recorder.cancel(); recording = false },
                        onSendRecording = {
                            recording = false
                            val clip = recorder.stop()
                            if (clip == null) scope.launch { snackbar.showSnackbar("Too short — hold on a little longer") }
                            else {
                                timeline.sendVoice(clip.file, clip.durationMs, clip.waveform, (mode as? ComposerMode.Reply)?.item?.eventId)
                                mode = ComposerMode.Normal
                                followLatest = true
                            }
                        },
                        onStickers = { stickerSheet = true },
                    )
                }
            }
            }
        }
    }

    // Long-press actions.
    sheetFor?.let { item ->
        ModalBottomSheet(onDismissRequest = { sheetFor = null }) {
            if (item.eventId != null && item.content !is UiContent.Redacted) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(QuickReactions) { emoji ->
                        val mine = item.reactions.any { it.key == emoji && it.mine }
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(if (mine) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { timeline.toggleReaction(item, emoji); sheetFor = null },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 26.sp) }
                    }
                    item {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).clickable { emojiPickerFor = item; sheetFor = null },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.AddReaction, "More reactions") }
                    }
                }
                HorizontalDivider()
            }
            if (item.status == SendStatus.FAILED) {
                SettingRow(Icons.Default.Refresh, "Retry sending") { timeline.retry(item); sheetFor = null }
                SettingRow(Icons.Default.Close, "Cancel sending") { timeline.cancelSend(item); sheetFor = null }
            }
            if (item.canReply && item.eventId != null) {
                SettingRow(Icons.AutoMirrored.Filled.Reply, "Reply") { mode = ComposerMode.Reply(item); sheetFor = null }
                if (threadRootId == null) {
                    SettingRow(Icons.Default.Forum, "Reply in thread") { onOpenThread(item.eventId); sheetFor = null }
                }
            }
            if (item.isOwn && item.isEditable && item.content is UiContent.Text) {
                SettingRow(Icons.Default.Edit, "Edit") {
                    val body = (item.content as UiContent.Text).body
                    composer = TextFieldValue(body, TextRange(body.length))
                    mode = ComposerMode.Edit(item)
                    sheetFor = null
                }
            }
            if (item.isOwn && item.isEditable && (item.content is UiContent.Image || item.content is UiContent.Video || item.content is UiContent.File)) {
                var editingCaption by remember { mutableStateOf(false) }
                SettingRow(Icons.Default.Edit, "Edit caption") { editingCaption = true }
                if (editingCaption) {
                    TextInputDialog(
                        "Caption",
                        initial = when (val c = item.content) { is UiContent.Image -> c.caption; is UiContent.Video -> c.caption; is UiContent.File -> c.caption; else -> null }.orEmpty(),
                        onDismiss = { editingCaption = false },
                        onConfirm = { timeline.editCaption(item, it); sheetFor = null },
                    )
                }
            }
            val copyable = when (val c = item.content) {
                is UiContent.Text -> c.body
                is UiContent.Image -> c.caption
                is UiContent.File -> c.caption
                else -> null
            }
            copyable?.takeIf { it.isNotBlank() }?.let { text ->
                SettingRow(Icons.Default.ContentCopy, "Copy text") { copyText(context, "Message", text); sheetFor = null }
            }
            if (item.content is UiContent.Image || item.content is UiContent.Video || item.content is UiContent.File || item.content is UiContent.Audio) {
                (item.content as? UiContent.Image)?.let { img ->
                    SettingRow(Icons.Default.EmojiEmotions, "Add to my stickers") {
                        sheetFor = null
                        scope.launch {
                            runCatching { com.vknxworks.elementctrl.matrix.Stickers.saveToMyPack(session, img.sourceJson, img.filename, img.mime, img.width, img.height) }
                                .onSuccess { snackbar.showSnackbar("Added to My stickers") }
                                .onFailure { snackbar.showSnackbar("Couldn't add: ${it.message}") }
                        }
                    }
                }
                SettingRow(Icons.Default.Download, "Save to Downloads") {
                    sheetFor = null
                    scope.launch { saveMedia(context, session, item, snackbar) }
                }
                SettingRow(Icons.Default.Share, "Share") {
                    sheetFor = null
                    scope.launch { shareMedia(context, session, item, snackbar) }
                }
            }
            if (item.eventId != null) {
                SettingRow(Icons.Default.Link, "Copy link") {
                    sheetFor = null
                    scope.launch { room.eventPermalink(item.eventId)?.let { copyText(context, "Link", it); snackbar.showSnackbar("Link copied") } }
                }
                if (permissions.canPin) {
                    val pinned = header?.pinnedEventIds?.contains(item.eventId) == true
                    SettingRow(Icons.Default.PushPin, if (pinned) "Unpin" else "Pin") { timeline.pin(item.eventId, !pinned); sheetFor = null }
                }
                if (item.content is UiContent.Poll && item.isOwn && !(item.content as UiContent.Poll).ended) {
                    SettingRow(Icons.Default.Stop, "End poll") { timeline.endPoll(item.eventId); sheetFor = null }
                }
                SettingRow(Icons.Default.Code, "View source") {
                    sheetFor = null
                    scope.launch { source = timeline.source(item.eventId) ?: "Source isn't available for this event." }
                }
                if (!item.isOwn) {
                    SettingRow(Icons.Default.Flag, "Report") { reporting = item; sheetFor = null }
                }
            }
            if (item.content !is UiContent.Redacted && item.eventId != null && (item.isOwn || permissions.canRedactOthers)) {
                SettingRow(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
                    deleting = item; sheetFor = null
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    emojiPickerFor?.let { item ->
        EmojiPicker(onDismiss = { emojiPickerFor = null }, onPick = { timeline.toggleReaction(item, it); emojiPickerFor = null })
    }

    deleting?.let { item ->
        ConfirmDialog(
            "Remove message?",
            "It will be removed for everyone in the room. This can't be undone.",
            "Remove",
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = { timeline.redact(item) },
        )
    }

    reporting?.let { item ->
        TextInputDialog(
            "Report message",
            label = "Reason (optional)",
            confirm = "Report",
            onDismiss = { reporting = null },
            onConfirm = { reason -> item.eventId?.let { room.report(it, reason.ifBlank { null }) }; scope.launch { snackbar.showSnackbar("Reported to your server's admins") } },
        )
    }

    source?.let { json ->
        AlertDialog(
            onDismissRequest = { source = null },
            title = { Text("Event source") },
            text = {
                Text(
                    json,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { copyText(context, "Event source", json); source = null }) { Text("Copy") } },
            dismissButton = { TextButton(onClick = { source = null }) { Text("Close") } },
        )
    }

    suspiciousLink?.let { (url, shown) ->
        com.vknxworks.elementctrl.ui.ConfirmDialog(
            title = "Open this link?",
            text = "The link says \"$shown\" but really goes to:\n\n$url",
            confirm = "Open",
            destructive = true,
            onDismiss = { suspiciousLink = null },
            onConfirm = { com.vknxworks.elementctrl.ui.openUrl(context, url) },
        )
    }

    pendingAttachment?.let { uri ->
        var caption by remember(uri) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingAttachment = null },
            title = { Text("Send attachment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    if (mime.startsWith("image/")) {
                        coil3.compose.AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)))
                    } else {
                        Text(uri.lastPathSegment ?: "File", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedTextField(caption, { caption = it }, label = { Text("Caption (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val replyTo = (mode as? ComposerMode.Reply)?.item?.eventId
                    timeline.sendAttachment(context, uri, caption, replyTo)
                    mode = ComposerMode.Normal
                    pendingAttachment = null
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { pendingAttachment = null }) { Text("Cancel") } },
        )
    }

    if (stickerSheet) {
        StickerGifSheet(
            session = session,
            roomId = roomId,
            onDismiss = { stickerSheet = false },
            onSticker = { st ->
                stickerSheet = false
                followLatest = true
                scope.launch { runCatching { com.vknxworks.elementctrl.matrix.Stickers.send(session, roomId, st) }.onFailure { snackbar.showSnackbar("Couldn't send: ${it.message}") } }
            },
            onGif = { file ->
                stickerSheet = false
                followLatest = true
                timeline.sendLocalFile(file, "image/gif", null, (mode as? ComposerMode.Reply)?.item?.eventId)
                mode = ComposerMode.Normal
            },
        )
    }

    if (creatingPoll) {
        PollDialog(onDismiss = { creatingPoll = false }, onCreate = { q, answers, disclosed -> timeline.createPoll(q, answers, disclosed); creatingPoll = false })
    }
}

@Composable
private fun RoomStart(name: String?, isDm: Boolean, encrypted: Boolean) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (isDm) "This is the beginning of your chat" else "This is the beginning of ${name ?: "this room"}", style = MaterialTheme.typography.titleSmall)
        if (encrypted) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text("Messages are end-to-end encrypted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IslandNote(text: String, haze: HazeState) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().glassIsland(haze, RoundedCornerShape(28.dp)).padding(16.dp),
    )
}

@Composable
private fun InviteBar(inviter: String?, haze: HazeState, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().glassIsland(haze, RoundedCornerShape(28.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${inviter ?: "Someone"} invited you to join.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text("Decline") }
            Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
        }
    }
}

@Composable
private fun Composer(
    haze: HazeState,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    mode: ComposerMode,
    onCancelMode: () -> Unit,
    members: List<MemberInfo>,
    onMention: (MemberInfo) -> Unit,
    sendOnEnter: Boolean,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    attachMenu: Boolean,
    onDismissAttach: () -> Unit,
    onPickMedia: () -> Unit,
    onPickFile: () -> Unit,
    onPoll: () -> Unit,
    recording: Boolean = false,
    levels: List<Float> = emptyList(),
    elapsed: Long = 0,
    onMic: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onSendRecording: () -> Unit = {},
    onStickers: () -> Unit = {},
) {
    // "@na" before the cursor offers matching members.
    val mentionQuery = remember(value) {
        val before = value.text.substring(0, value.selection.start.coerceIn(0, value.text.length))
        Regex("(^|\\s)@([^\\s@]*)$").find(before)?.groupValues?.get(2)
    }
    val suggestions = remember(mentionQuery, members) {
        mentionQuery?.let { q ->
            members.filter { it.membership == "join" && (it.name.contains(q, true) || it.userId.contains(q, true)) }.take(5)
        }.orEmpty()
    }
    val cs = MaterialTheme.colorScheme
    val canSend = value.text.isNotBlank()

    Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Mention suggestions and the reply/edit preview sit in their own small island above the input.
        if (suggestions.isNotEmpty() || mode != ComposerMode.Normal) {
            Column(Modifier.fillMaxWidth().glassIsland(haze, RoundedCornerShape(22.dp), elevation = 8)) {
                suggestions.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onMention(m) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(m.name, m.avatarUrl, m.userId, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(m.name, style = MaterialTheme.typography.bodyMedium)
                            Text(m.userId, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
                if (suggestions.isNotEmpty() && mode != ComposerMode.Normal) HorizontalDivider()
                when (mode) {
                    is ComposerMode.Reply, is ComposerMode.Edit -> {
                        val item = if (mode is ComposerMode.Reply) mode.item else (mode as ComposerMode.Edit).item
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (mode is ComposerMode.Reply) Icons.AutoMirrored.Filled.Reply else Icons.Default.Edit, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (mode is ComposerMode.Reply) "Replying to ${item.senderName}" else "Editing", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                                Text(previewOf(item), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onCancelMode) { Icon(Icons.Default.Close, "Cancel") }
                        }
                    }
                    ComposerMode.Normal -> Unit
                }
            }
        }

        if (recording) {
            RecordingIsland(haze, levels, elapsed, onCancelRecording, onSendRecording)
            return@Column
        }
        // The input island.
        Row(
            Modifier.fillMaxWidth().glassIsland(haze, RoundedCornerShape(28.dp)).heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box {
                IconButton(onClick = onAttach) { Icon(Icons.Default.Add, "Attach", tint = cs.onSurfaceVariant) }
                DropdownMenu(expanded = attachMenu, onDismissRequest = onDismissAttach) {
                    DropdownMenuItem(text = { Text("Photo or video") }, leadingIcon = { Icon(Icons.Default.Image, null) }, onClick = onPickMedia)
                    DropdownMenuItem(text = { Text("File") }, leadingIcon = { Icon(Icons.Default.AttachFile, null) }, onClick = onPickFile)
                    DropdownMenuItem(text = { Text("Poll") }, leadingIcon = { Icon(Icons.Default.BarChart, null) }, onClick = onPoll)
                }
            }
            TextField(
                value = value,
                onValueChange = { v ->
                    if (sendOnEnter && v.text.endsWith("\n") && !value.text.endsWith("\n")) {
                        onValueChange(v.copy(text = v.text.dropLast(1)))
                        onSend()
                    } else onValueChange(v)
                },
                placeholder = { Text("Message") },
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            if (!canSend) {
                IconButton(onClick = onStickers) { Icon(Icons.Default.EmojiEmotions, "Stickers and GIFs", tint = cs.onSurfaceVariant) }
            }
            // With nothing typed the button records a voice message instead.
            val voiceMode = !canSend && mode !is ComposerMode.Edit
            val sendColor by animateColorAsState(if (canSend || voiceMode) cs.primary else cs.surfaceContainerHighest, label = "send")
            Box(
                Modifier
                    .padding(4.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(sendColor)
                    .clickable(enabled = canSend || voiceMode, onClick = if (voiceMode) onMic else onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (voiceMode) Icons.Default.Mic else Icons.AutoMirrored.Filled.Send,
                    if (voiceMode) "Record voice message" else "Send",
                    tint = cs.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Shown in place of the input while recording: live level bars, the timer, cancel and send. */
@Composable
private fun RecordingIsland(haze: HazeState, levels: List<Float>, elapsed: Long, onCancel: () -> Unit, onSend: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().glassIsland(haze, RoundedCornerShape(28.dp)).height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) { Icon(Icons.Default.Delete, "Discard recording", tint = cs.error) }
        Box(Modifier.size(10.dp).clip(CircleShape).background(cs.error))
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(elapsed), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f).height(28.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            val bars = levels.takeLast(32)
            repeat(32 - bars.size) { Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(cs.onSurfaceVariant.copy(alpha = 0.3f))) }
            bars.forEach { v ->
                Box(Modifier.weight(1f).fillMaxHeight((0.12f + 0.88f * v).coerceIn(0.12f, 1f)).clip(RoundedCornerShape(2.dp)).background(cs.primary))
            }
        }
        Box(
            Modifier.padding(4.dp).size(44.dp).clip(CircleShape).background(cs.primary).clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send voice message", tint = cs.onPrimary, modifier = Modifier.size(20.dp)) }
    }
}

private fun previewOf(item: TimelineEntry.Message): String = when (val c = item.content) {
    is UiContent.Text -> c.body
    is UiContent.Image -> "🖼 " + (c.caption ?: "Image")
    is UiContent.Video -> "🎬 " + (c.caption ?: "Video")
    is UiContent.Audio -> "🎵 Audio"
    is UiContent.File -> "📎 " + c.filename
    is UiContent.Poll -> "📊 " + c.question
    is UiContent.Location -> "📍 Location"
    else -> ""
}

@Composable
private fun PollDialog(onDismiss: () -> Unit, onCreate: (String, List<String>, Boolean) -> Unit) {
    var question by remember { mutableStateOf("") }
    val answers = remember { mutableStateListOf("", "") }
    var disclosed by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create poll") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(question, { question = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
                answers.forEachIndexed { i, a ->
                    OutlinedTextField(a, { answers[i] = it }, label = { Text("Option ${i + 1}") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (answers.size < 20) TextButton(onClick = { answers.add("") }) { Text("Add option") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = disclosed, onCheckedChange = { disclosed = it })
                    Text("Show results while voting")
                }
            }
        },
        confirmButton = {
            val valid = question.isNotBlank() && answers.count { it.isNotBlank() } >= 2
            TextButton(enabled = valid, onClick = { onCreate(question.trim(), answers.filter { it.isNotBlank() }.map { it.trim() }, disclosed) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val EmojiSet = listOf(
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😋", "😛", "😜", "🤪",
    "🤔", "🤨", "😐", "😑", "😶", "🙄", "😏", "😬", "😌", "😔", "😴", "🤒", "🤕", "🤢", "🤯", "🥳", "😎", "🤓", "🧐", "😕",
    "😟", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫",
    "😤", "😡", "😠", "🤬", "😈", "💀", "💩", "🤡", "👻", "👽", "🤖", "😺", "🙈", "🙉", "🙊", "💯", "💢", "💥", "💫", "💬",
    "👋", "🤚", "✋", "🖖", "👌", "🤌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "👍", "👎", "✊", "👊",
    "👏", "🙌", "👐", "🤲", "🤝", "🙏", "💪", "🧠", "👀", "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "❣️", "💕",
    "🔥", "✨", "⭐", "🌟", "⚡", "🌈", "☀️", "🌙", "☁️", "❄️", "🎉", "🎊", "🎁", "🏆", "🥇", "⚽", "🎮", "🎵", "🎶", "📌",
    "✅", "❌", "⚠️", "❓", "❗", "➕", "➖", "🆗", "🆒", "🆕", "☕", "🍺", "🍕", "🍔", "🍟", "🍿", "🍩", "🍰", "🍎", "🥑",
)

@Composable
private fun EmojiPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(48.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            items(EmojiSet.size) { i ->
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { onPick(EmojiSet[i]) }, contentAlignment = Alignment.Center) {
                    Text(EmojiSet[i], fontSize = 26.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Opens a picture in the viewer, or downloads other attachments and hands them to a suitable app. */
private fun openMedia(
    context: android.content.Context,
    session: MatrixSession,
    item: TimelineEntry.Message,
    onOpenImage: (MediaRequest) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: SnackbarHostState,
) {
    when (val c = item.content) {
        is UiContent.Image -> onOpenImage(MediaRequest(c.sourceJson, c.filename, c.mime, c.caption))
        is UiContent.Location -> {
            // The URI comes from someone else's event: only ever open it as a map location.
            val geo = runCatching { Uri.parse(c.geoUri.trim()) }.getOrNull()?.takeIf { it.scheme.equals("geo", ignoreCase = true) }
            if (geo == null) {
                scope.launch { snackbar.showSnackbar("This location can't be opened") }
            } else runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { scope.launch { snackbar.showSnackbar("No maps app to open this location") } }
        }
        else -> {
            val info = mediaInfo(c) ?: return
            scope.launch {
                snackbar.showSnackbar("Downloading ${info.second}…", duration = androidx.compose.material3.SnackbarDuration.Short)
            }
            scope.launch {
                runCatching {
                    val file = MediaFiles.download(context, session, info.first, info.second, info.third)
                    MediaFiles.open(context, file, info.third)
                }.onFailure { snackbar.showSnackbar("Couldn't open: ${it.message}") }
            }
        }
    }
}

private fun mediaInfo(c: UiContent): Triple<String, String, String>? = when (c) {
    is UiContent.Image -> Triple(c.sourceJson, c.filename, c.mime)
    is UiContent.Video -> Triple(c.sourceJson, c.filename, c.mime)
    is UiContent.Audio -> Triple(c.sourceJson, c.filename, c.mime)
    is UiContent.File -> Triple(c.sourceJson, c.filename, c.mime)
    else -> null
}

private suspend fun saveMedia(context: android.content.Context, session: MatrixSession, item: TimelineEntry.Message, snackbar: SnackbarHostState) {
    val info = mediaInfo(item.content) ?: return
    runCatching {
        val file = MediaFiles.download(context, session, info.first, info.second, info.third)
        MediaFiles.saveToDownloads(context, file, info.second, info.third)
    }.onSuccess { ok -> snackbar.showSnackbar(if (ok) "Saved to Downloads/ElementCtrl" else "Couldn't save the file") }
        .onFailure { snackbar.showSnackbar("Couldn't save: ${it.message}") }
}

private suspend fun shareMedia(context: android.content.Context, session: MatrixSession, item: TimelineEntry.Message, snackbar: SnackbarHostState) {
    val info = mediaInfo(item.content) ?: return
    runCatching {
        val file = MediaFiles.download(context, session, info.first, info.second, info.third)
        MediaFiles.share(context, file, info.third)
    }.onFailure { snackbar.showSnackbar("Couldn't share: ${it.message}") }
}

/** A picture to show full screen. */
data class MediaRequest(val sourceJson: String, val filename: String, val mime: String, val caption: String?)
