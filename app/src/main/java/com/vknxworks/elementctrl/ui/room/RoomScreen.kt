package com.vknxworks.elementctrl.ui.room

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxHeight
import com.vknxworks.elementctrl.ui.formatDuration
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
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
import com.vknxworks.elementctrl.ui.CtrlColors
import com.vknxworks.elementctrl.ui.CtrlShapes
import com.vknxworks.elementctrl.ui.CtrlType
import com.vknxworks.elementctrl.ui.components.CtrlButton
import com.vknxworks.elementctrl.ui.components.CtrlCard
import com.vknxworks.elementctrl.ui.components.CtrlDialog
import com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.elementctrl.ui.components.CtrlSheet
import com.vknxworks.elementctrl.ui.components.CtrlSubBar
import com.vknxworks.elementctrl.ui.components.CtrlTextField
import androidx.compose.foundation.border
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
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
import com.vknxworks.elementctrl.matrix.userMessage
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
import androidx.compose.ui.res.stringResource
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.ui.ScreenTopBar

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
        Scaffold(topBar = { ScreenTopBar(stringResource(R.string.room_2), onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.this_room_isn_t_available_yet_it_may), modifier = Modifier.padding(32.dp))
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
    val chatAvatars by ElementCtrlApp.graph.prefs.chatAvatars.collectAsStateWithLifecycle()
    val doubleTapReaction by ElementCtrlApp.graph.prefs.doubleTapReaction.collectAsStateWithLifecycle()
    val showReadReceipts by ElementCtrlApp.graph.prefs.showReadReceipts.collectAsStateWithLifecycle()
    var receiptsFor by remember { mutableStateOf<TimelineEntry.Message?>(null) }
    val haze = remember { HazeState() }
    val density = LocalDensity.current
    var islandHeight by remember { mutableStateOf(88.dp) }
    var composer by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var mode by remember { mutableStateOf<ComposerMode>(ComposerMode.Normal) }
    // Replying (by swipe or from the menu) puts the cursor in the message box and opens the keyboard.
    val composerFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(mode) { if (mode is ComposerMode.Reply) runCatching { composerFocus.requestFocus() } }
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
    // Quick reactions: what you used recently first, then the defaults.
    var recentEmojis by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(sheetFor != null) { if (sheetFor != null) recentEmojis = timeline.recentEmojis() }
    val quickReactions = remember(recentEmojis) { (recentEmojis + QuickReactions).distinct().take(QuickReactions.size) }
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    val levels by recorder.levels.collectAsStateWithLifecycle()
    val elapsed by recorder.elapsed.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { recorder.cancel(); AudioPlayer.stop() } }
    val linkPreviews by ElementCtrlApp.graph.prefs.linkPreviews.collectAsStateWithLifecycle()
    val linkPreviewsE2ee by ElementCtrlApp.graph.prefs.linkPreviewsEncrypted.collectAsStateWithLifecycle()
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recording = recorder.start()
        else scope.launch { snackbar.showSnackbar(Strings.str(R.string.allow_the_microphone_to_record_voice)) }
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
        entries.filter { e -> e !is TimelineEntry.Hidden && (showState || !(e is TimelineEntry.Message && e.isStateLike)) }.asReversed()
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
    LaunchedEffect(atBottom, entries.size) {
        timeline.seenLatest = atBottom && entries.isNotEmpty()
        if (atBottom && entries.isNotEmpty()) timeline.markAsRead()
    }
    // Read to the end and leaving the app: move the "new messages" line now (leaving the chat does it too).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, timeline) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP && timeline.seenLatest) timeline.markFullyRead()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

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
            scope.launch { snackbar.showSnackbar(Strings.str(R.string.that_message_isn_t_loaded_scroll_up)) }
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
            doubleTapReaction = doubleTapReaction.ifBlank { null },
            showReceipts = showReadReceipts,
            member = { id -> members.firstOrNull { it.userId == id } },
            onShowReceipts = { receiptsFor = it },
            onSwipeReply = if (permissions.canSend && header?.membership == Membership.JOINED) { item -> mode = ComposerMode.Reply(item) } else null,
        )
    }

    val isDm = header?.isDm == true
    val membership = header?.membership
    val startCall = com.vknxworks.elementctrl.call.rememberCallStarter(session)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CtrlSubBar(
                onBack = onBack,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = threadRootId == null, onClick = onDetails),
                    ) {
                        Avatar(header?.name ?: "", header?.avatarUrl, header?.dmUserId ?: roomId, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (threadRootId != null) stringResource(R.string.thread) else header?.name ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = when {
                                typing.isNotEmpty() -> typing.joinToString(limit = 2, truncated = Strings.str(R.string.typing_others)).let { if (typing.size == 1) Strings.str(R.string.typing_one, it) else Strings.str(R.string.typing_many, it) }
                                threadRootId != null -> header?.name
                                isDm -> if (header?.isEncrypted == true) Strings.str(R.string.encrypted_chat) else null
                                else -> header?.joinedMembers?.let { Strings.plural(R.plurals.members_count, it.toInt(), it) }
                            }
                            subtitle?.let {
                                Text(
                                    it,
                                    style = CtrlType.Meta,
                                    color = if (typing.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (threadRootId == null && membership == org.matrix.rustcomponents.sdk.Membership.JOINED) {
                        com.vknxworks.elementctrl.call.CallButtons(isDm) { audio -> startCall(roomId, audio) }
                    }
                    IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.search_messages), Modifier.size(22.dp)) }
                    if (threadRootId == null) IconButton(onClick = onDetails) { Icon(Icons.Outlined.Info, stringResource(R.string.room_info), Modifier.size(22.dp)) }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (entries.isEmpty() && !atStart) {
                CenteredLoading()
            }
            CompositionLocalProvider(LocalBubbleStyle provides bubbleStyle, LocalChatAvatars provides chatAvatars) {
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
                            is TimelineEntry.Hidden -> Unit
                            is TimelineEntry.Message -> {
                                val older = visible.getOrNull(index + 1)
                                val showSender = older !is TimelineEntry.Message || older.sender != entry.sender || older.isStateLike ||
                                    entry.timestamp - older.timestamp > 5 * 60_000
                                // The list is reversed, so the next (newer) message sits at index - 1.
                                val newer = visible.getOrNull(index - 1)
                                val lastInGroup = newer !is TimelineEntry.Message || newer.sender != entry.sender || newer.isStateLike ||
                                    newer.timestamp - entry.timestamp > 5 * 60_000
                                MessageRow(
                                    entry, showSender, isDm, actions,
                                    highlighted = highlighted != null && highlighted == entry.eventId,
                                    lastInGroup = lastInGroup,
                                )
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
            if (threadRootId == null && header?.hasCall == true && membership == org.matrix.rustcomponents.sdk.Membership.JOINED) {
                com.vknxworks.elementctrl.call.CallBanner(
                    roomId, header?.callMembers ?: 0, isDm,
                    Modifier.align(Alignment.TopCenter),
                ) { audio -> startCall(roomId, audio) }
            }
            header?.successorRoomId?.let { successor ->
                CtrlCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).align(Alignment.TopCenter),
                    padding = PaddingValues(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.this_room_has_been_upgraded), Modifier.weight(1f))
                        TextButton(onClick = { onOpenRoom(successor) }) { Text(stringResource(R.string.go_to_new_room)) }
                    }
                }
            }
            if (focusEventId != null) {
                CtrlCard(
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                    padding = PaddingValues(start = 14.dp, end = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.viewing_earlier_messages), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onJumpToLatest) { Text(stringResource(R.string.jump_to_latest)) }
                    }
                }
            }
            if (!atBottom || focusEventId != null) {
                BadgedBox(
                    badge = { if (unseenBelow > 0) Badge { Text(if (unseenBelow > 99) "99+" else "$unseenBelow") } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = islandHeight + 12.dp),
                ) {
                    SmallFloatingActionButton(
                        shape = CtrlShapes.Square,
                        containerColor = CtrlColors.Surface,
                        contentColor = CtrlColors.Text,
                        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        modifier = Modifier.border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Square),
                        onClick = {
                        if (focusEventId != null) {
                            onJumpToLatest()
                        } else {
                            followLatest = true
                            unseenBelow = 0
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    }) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.jump_to_latest)) }
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
                        IslandNote(if (membership == Membership.BANNED) stringResource(R.string.you_were_banned_from_this_room) else stringResource(R.string.you_left_this_room), haze)
                    }
                    !permissions.canSend -> IslandNote(stringResource(R.string.you_don_t_have_permission_to_post), haze)
                    else -> Composer(
                        haze = haze,
                        focusRequester = composerFocus,
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
                        onPickMedia = { attachMenu = false; com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        onPickFile = { attachMenu = false; com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); pickFile.launch(arrayOf("*/*")) },
                        onPoll = { attachMenu = false; creatingPoll = true },
                        recording = recording,
                        levels = levels,
                        elapsed = elapsed,
                        onMic = {
                            if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                recording = recorder.start()
                                if (!recording) scope.launch { snackbar.showSnackbar(Strings.str(R.string.couldn_t_start_the_microphone)) }
                            } else { com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); micPermission.launch(android.Manifest.permission.RECORD_AUDIO) }
                        },
                        onCancelRecording = { recorder.cancel(); recording = false },
                        onSendRecording = {
                            recording = false
                            val clip = recorder.stop()
                            if (clip == null) scope.launch { snackbar.showSnackbar(Strings.str(R.string.too_short_hold_on_a_little_longer)) }
                            else {
                                timeline.sendVoice(clip.file, clip.durationMs, clip.waveform, (mode as? ComposerMode.Reply)?.item?.eventId)
                                mode = ComposerMode.Normal
                                followLatest = true
                            }
                        },
                    )
                }
            }
            }
        }
    }

    // Long-press actions.
    sheetFor?.let { item ->
        CtrlSheet(onDismissRequest = { sheetFor = null }) {
            if (item.eventId != null && item.content !is UiContent.Redacted) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(quickReactions) { emoji ->
                        val mine = item.reactions.any { it.key == emoji && it.mine }
                        Box(
                            Modifier.size(48.dp).clip(CtrlShapes.Control)
                                .background(if (mine) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { timeline.toggleReaction(item, emoji); sheetFor = null },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, fontSize = 26.sp) }
                    }
                    item {
                        Box(
                            Modifier.size(48.dp).clip(CtrlShapes.Control).clickable { emojiPickerFor = item; sheetFor = null },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.AddReaction, stringResource(R.string.more_reactions)) }
                    }
                }
                HorizontalDivider()
            }
            if (item.status == SendStatus.FAILED) {
                SettingRow(Icons.Outlined.Refresh, stringResource(R.string.retry_sending)) { timeline.retry(item); sheetFor = null }
                SettingRow(Icons.Outlined.Close, stringResource(R.string.cancel_sending)) { timeline.cancelSend(item); sheetFor = null }
            }
            if (item.canReply && item.eventId != null) {
                SettingRow(Icons.AutoMirrored.Outlined.Reply, stringResource(R.string.reply)) { mode = ComposerMode.Reply(item); sheetFor = null }
                if (threadRootId == null) {
                    SettingRow(Icons.Outlined.Forum, stringResource(R.string.reply_in_thread)) { onOpenThread(item.eventId); sheetFor = null }
                }
            }
            if (item.isOwn && item.isEditable && item.content is UiContent.Text) {
                SettingRow(Icons.Outlined.Edit, stringResource(R.string.edit)) {
                    val body = (item.content as UiContent.Text).body
                    composer = TextFieldValue(body, TextRange(body.length))
                    mode = ComposerMode.Edit(item)
                    sheetFor = null
                }
            }
            if (item.isOwn && item.isEditable && (item.content is UiContent.Image || item.content is UiContent.Video || item.content is UiContent.File)) {
                var editingCaption by remember { mutableStateOf(false) }
                SettingRow(Icons.Outlined.Edit, stringResource(R.string.edit_caption)) { editingCaption = true }
                if (editingCaption) {
                    TextInputDialog(
                        stringResource(R.string.caption),
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
                SettingRow(Icons.Outlined.ContentCopy, stringResource(R.string.copy_text)) { copyText(context, Strings.str(R.string.message), text); sheetFor = null }
            }
            if (item.content is UiContent.Image || item.content is UiContent.Video || item.content is UiContent.File || item.content is UiContent.Audio) {
                SettingRow(Icons.Outlined.Download, stringResource(R.string.save_to_downloads)) {
                    sheetFor = null
                    scope.launch { saveMedia(context, session, item, snackbar) }
                }
                SettingRow(Icons.Outlined.Share, stringResource(R.string.share)) {
                    sheetFor = null
                    scope.launch { shareMedia(context, session, item, snackbar) }
                }
            }
            if (item.eventId != null) {
                SettingRow(Icons.Outlined.Link, stringResource(R.string.copy_link)) {
                    sheetFor = null
                    scope.launch { room.eventPermalink(item.eventId)?.let { copyText(context, Strings.str(R.string.link), it); snackbar.showSnackbar(Strings.str(R.string.link_copied)) } }
                }
                if (permissions.canPin) {
                    val pinned = header?.pinnedEventIds?.contains(item.eventId) == true
                    SettingRow(Icons.Outlined.PushPin, if (pinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) { timeline.pin(item.eventId, !pinned); sheetFor = null }
                }
                if (item.content is UiContent.Poll && item.isOwn && !(item.content as UiContent.Poll).ended) {
                    SettingRow(Icons.Outlined.Stop, stringResource(R.string.end_poll)) { timeline.endPoll(item.eventId); sheetFor = null }
                }
                SettingRow(Icons.Outlined.Code, stringResource(R.string.view_source)) {
                    sheetFor = null
                    scope.launch { source = timeline.source(item.eventId) ?: Strings.str(R.string.source_isn_t_available_for_this) }
                }
                if (!item.isOwn) {
                    SettingRow(Icons.Outlined.Flag, stringResource(R.string.report)) { reporting = item; sheetFor = null }
                }
            }
            if (item.content !is UiContent.Redacted && item.eventId != null && (item.isOwn || permissions.canRedactOthers)) {
                SettingRow(Icons.Outlined.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
                    deleting = item; sheetFor = null
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    receiptsFor?.let { item ->
        CtrlSheet(onDismissRequest = { receiptsFor = null }) {
            Text(
                stringResource(R.string.read_receipts),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(item.receipts, key = { it.userId }) { r ->
                    val m = members.firstOrNull { it.userId == r.userId }
                    Row(
                        Modifier.fillMaxWidth().clickable { receiptsFor = null; onOpenUser(r.userId) }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(m?.name ?: r.userId, m?.avatarUrl, r.userId, size = 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m?.name ?: r.userId, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.userId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        r.timestamp?.let { ts ->
                            Text(
                                com.vknxworks.elementctrl.ui.dayLabel(ts) + ", " + com.vknxworks.elementctrl.ui.timeOfDay(ts),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    emojiPickerFor?.let { item ->
        EmojiPicker(onDismiss = { emojiPickerFor = null }, onPick = { timeline.toggleReaction(item, it); emojiPickerFor = null })
    }

    deleting?.let { item ->
        ConfirmDialog(
            stringResource(R.string.remove_message),
            stringResource(R.string.it_will_be_removed_for_everyone_in),
            stringResource(R.string.remove),
            destructive = true,
            onDismiss = { deleting = null },
            onConfirm = { timeline.redact(item) },
        )
    }

    reporting?.let { item ->
        TextInputDialog(
            stringResource(R.string.report_message),
            label = stringResource(R.string.reason_optional),
            confirm = stringResource(R.string.report),
            onDismiss = { reporting = null },
            onConfirm = { reason -> item.eventId?.let { room.report(it, reason.ifBlank { null }) }; scope.launch { snackbar.showSnackbar(Strings.str(R.string.reported_to_your_server_s_admins)) } },
        )
    }

    source?.let { json ->
        CtrlDialog(
            onDismissRequest = { source = null },
            title = { Text(stringResource(R.string.event_source)) },
            text = {
                Text(
                    json,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { copyText(context, Strings.str(R.string.event_source), json); source = null }) { Text(stringResource(R.string.copy)) } },
            dismissButton = { TextButton(onClick = { source = null }) { Text(stringResource(R.string.lock_close)) } },
        )
    }

    suspiciousLink?.let { (url, shown) ->
        com.vknxworks.elementctrl.ui.ConfirmDialog(
            title = stringResource(R.string.open_this_link),
            text = stringResource(R.string.the_link_says_but_really_goes_to_n_n, shown, url),
            confirm = stringResource(R.string.open),
            destructive = true,
            onDismiss = { suspiciousLink = null },
            onConfirm = { com.vknxworks.elementctrl.ui.openUrl(context, url) },
        )
    }

    pendingAttachment?.let { uri ->
        var caption by remember(uri) { mutableStateOf("") }
        CtrlDialog(
            onDismissRequest = { pendingAttachment = null },
            title = { Text(stringResource(R.string.send_attachment)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val mime = context.contentResolver.getType(uri).orEmpty()
                    if (mime.startsWith("image/")) {
                        coil3.compose.AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clip(RoundedCornerShape(12.dp)))
                    } else {
                        Text(uri.lastPathSegment ?: stringResource(R.string.file_2), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    CtrlTextField(caption, { caption = it }, label = { Text(stringResource(R.string.caption_optional)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val replyTo = (mode as? ComposerMode.Reply)?.item?.eventId
                    timeline.sendAttachment(context, uri, caption, replyTo)
                    mode = ComposerMode.Normal
                    pendingAttachment = null
                }) { Text(stringResource(R.string.send)) }
            },
            dismissButton = { TextButton(onClick = { pendingAttachment = null }) { Text(stringResource(R.string.lock_cancel)) } },
        )
    }

    if (creatingPoll) {
        PollDialog(onDismiss = { creatingPoll = false }, onCreate = { q, answers, disclosed -> timeline.createPoll(q, answers, disclosed); creatingPoll = false })
    }
}

@Composable
private fun RoomStart(name: String?, isDm: Boolean, encrypted: Boolean) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (isDm) stringResource(R.string.this_is_the_beginning_of_your_chat) else (name?.let { stringResource(R.string.this_is_the_beginning_of, it) } ?: stringResource(R.string.this_is_the_beginning_of_this_room)), style = MaterialTheme.typography.titleSmall)
        if (encrypted) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.messages_are_end_to_end_encrypted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IslandNote(text: String, haze: HazeState) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().glassIsland(haze, CtrlShapes.Card).padding(16.dp),
    )
}

@Composable
private fun InviteBar(inviter: String?, haze: HazeState, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().glassIsland(haze, CtrlShapes.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.invited_you_to_join, inviter ?: stringResource(R.string.invite_someone)), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CtrlSecondaryButton(onClick = onDecline, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.invite_decline)) }
            CtrlButton(onClick = onAccept, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.invite_accept)) }
        }
    }
}

@Composable
private fun Composer(
    haze: HazeState,
    focusRequester: androidx.compose.ui.focus.FocusRequester,
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
            Column(Modifier.fillMaxWidth().glassIsland(haze, CtrlShapes.Card, elevation = 8)) {
                suggestions.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onMention(m) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(m.name, m.avatarUrl, m.userId, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(m.name, style = MaterialTheme.typography.bodyMedium)
                            Text(m.userId, style = CtrlType.Meta.copy(fontSize = 11.sp), color = cs.onSurfaceVariant)
                        }
                    }
                }
                if (suggestions.isNotEmpty() && mode != ComposerMode.Normal) HorizontalDivider(color = CtrlColors.Border)
                when (mode) {
                    is ComposerMode.Reply, is ComposerMode.Edit -> {
                        val item = if (mode is ComposerMode.Reply) mode.item else (mode as ComposerMode.Edit).item
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (mode is ComposerMode.Reply) Icons.AutoMirrored.Outlined.Reply else Icons.Outlined.Edit, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (mode is ComposerMode.Reply) stringResource(R.string.replying_to, item.senderName) else stringResource(R.string.editing), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                                Text(previewOf(item), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onCancelMode) { Icon(Icons.Outlined.Close, stringResource(R.string.lock_cancel)) }
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
        // Every slot is 56 dp tall (the text field's single-line height), so the buttons line up with the text and
        // sit the same distance from the island's edges; with more lines the buttons stay at the bottom.
        Row(
            Modifier.fillMaxWidth().glassIsland(haze, CtrlShapes.Card).padding(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.height(56.dp).padding(start = 2.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onAttach) { Icon(Icons.Outlined.Add, stringResource(R.string.attach), Modifier.size(22.dp), tint = cs.onSurfaceVariant) }
                DropdownMenu(
                    expanded = attachMenu,
                    onDismissRequest = onDismissAttach,
                    shape = CtrlShapes.Control,
                    containerColor = CtrlColors.Surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CtrlColors.Border),
                ) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.photo_or_video)) }, leadingIcon = { Icon(Icons.Outlined.Image, null) }, onClick = onPickMedia)
                    DropdownMenuItem(text = { Text(stringResource(R.string.file_2)) }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null) }, onClick = onPickFile)
                    DropdownMenuItem(text = { Text(stringResource(R.string.poll)) }, leadingIcon = { Icon(Icons.Outlined.BarChart, null) }, onClick = onPoll)
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
                placeholder = { Text(stringResource(R.string.message)) },
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = CtrlColors.AccentText,
                    unfocusedPlaceholderColor = CtrlColors.Muted,
                    focusedPlaceholderColor = CtrlColors.Muted,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            // With nothing typed the button records a voice message instead.
            val voiceMode = !canSend && mode !is ComposerMode.Edit
            val sendColor by animateColorAsState(if (canSend || voiceMode) CtrlColors.Accent else cs.surfaceContainerHighest, label = "send")
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CtrlShapes.Control)
                        .background(sendColor)
                        .clickable(enabled = canSend || voiceMode, onClick = if (voiceMode) onMic else onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (voiceMode) Icons.Outlined.Mic else Icons.AutoMirrored.Outlined.Send,
                        if (voiceMode) stringResource(R.string.record_voice_message) else stringResource(R.string.send),
                        tint = CtrlColors.OnAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Shown in place of the input while recording: live level bars, the timer, cancel and send. */
@Composable
private fun RecordingIsland(haze: HazeState, levels: List<Float>, elapsed: Long, onCancel: () -> Unit, onSend: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().glassIsland(haze, CtrlShapes.Card).padding(4.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = onCancel) { Icon(Icons.Outlined.Delete, stringResource(R.string.discard_recording), tint = cs.error) }
        }
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(cs.error))
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(elapsed), style = CtrlType.Meta.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
        Spacer(Modifier.width(10.dp))
        Row(Modifier.weight(1f).height(28.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            val bars = levels.takeLast(32)
            repeat(32 - bars.size) { Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(cs.onSurfaceVariant.copy(alpha = 0.3f))) }
            bars.forEach { v ->
                Box(Modifier.weight(1f).fillMaxHeight((0.12f + 0.88f * v).coerceIn(0.12f, 1f)).clip(RoundedCornerShape(2.dp)).background(cs.primary))
            }
        }
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(48.dp).clip(CtrlShapes.Control).background(CtrlColors.Accent).clickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.send_voice_message), tint = CtrlColors.OnAccent, modifier = Modifier.size(20.dp)) }
        }
    }
}

private fun previewOf(item: TimelineEntry.Message): String = when (val c = item.content) {
    is UiContent.Text -> c.body
    is UiContent.Image -> Strings.str(R.string.preview_image, c.caption ?: Strings.str(R.string.image))
    is UiContent.Video -> Strings.str(R.string.preview_video, c.caption ?: Strings.str(R.string.video))
    is UiContent.Audio -> Strings.str(R.string.audio)
    is UiContent.File -> Strings.str(R.string.preview_file, c.filename)
    is UiContent.Poll -> Strings.str(R.string.preview_poll, c.question)
    is UiContent.Location -> Strings.str(R.string.location)
    else -> ""
}

@Composable
private fun PollDialog(onDismiss: () -> Unit, onCreate: (String, List<String>, Boolean) -> Unit) {
    var question by remember { mutableStateOf("") }
    val answers = remember { mutableStateListOf("", "") }
    var disclosed by remember { mutableStateOf(true) }
    CtrlDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_poll)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CtrlTextField(question, { question = it }, label = { Text(stringResource(R.string.question)) }, modifier = Modifier.fillMaxWidth())
                answers.forEachIndexed { i, a ->
                    CtrlTextField(a, { answers[i] = it }, label = { Text(stringResource(R.string.option, i + 1)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (answers.size < 20) TextButton(onClick = { answers.add("") }) { Text(stringResource(R.string.add_option)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = disclosed, onCheckedChange = { disclosed = it })
                    Text(stringResource(R.string.show_results_while_voting))
                }
            }
        },
        confirmButton = {
            val valid = question.isNotBlank() && answers.count { it.isNotBlank() } >= 2
            TextButton(enabled = valid, onClick = { onCreate(question.trim(), answers.filter { it.isNotBlank() }.map { it.trim() }, disclosed) }) { Text(stringResource(R.string.create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_cancel)) } },
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
    CtrlSheet(onDismissRequest = onDismiss) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(48.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            items(EmojiSet.size) { i ->
                Box(Modifier.size(48.dp).clip(CtrlShapes.Control).clickable { onPick(EmojiSet[i]) }, contentAlignment = Alignment.Center) {
                    Text(EmojiSet[i], fontSize = 26.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Opens a picture in the viewer, or downloads other attachments and hands them to a suitable app. */
internal fun openMedia(
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
                scope.launch { snackbar.showSnackbar(Strings.str(R.string.this_location_can_t_be_opened)) }
            } else runCatching {
                com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent(Intent.ACTION_VIEW, geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { scope.launch { snackbar.showSnackbar(Strings.str(R.string.no_maps_app_to_open_this_location)) } }
        }
        else -> {
            val info = mediaInfo(c) ?: return
            scope.launch {
                snackbar.showSnackbar(Strings.str(R.string.downloading, info.second), duration = androidx.compose.material3.SnackbarDuration.Short)
            }
            scope.launch {
                runCatching {
                    val file = MediaFiles.download(context, session, info.first, info.second, info.third)
                    MediaFiles.open(context, file, info.third)
                }.onFailure { snackbar.showSnackbar(Strings.str(R.string.couldn_t_open, it.userMessage())) }
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
    }.onSuccess { ok -> snackbar.showSnackbar(if (ok) Strings.str(R.string.saved_to_downloads_elementctrl) else Strings.str(R.string.couldn_t_save_the_file)) }
        .onFailure { snackbar.showSnackbar(Strings.str(R.string.couldn_t_save_2, it.userMessage())) }
}

private suspend fun shareMedia(context: android.content.Context, session: MatrixSession, item: TimelineEntry.Message, snackbar: SnackbarHostState) {
    val info = mediaInfo(item.content) ?: return
    runCatching {
        val file = MediaFiles.download(context, session, info.first, info.second, info.third)
        MediaFiles.share(context, file, info.third)
    }.onFailure { snackbar.showSnackbar(Strings.str(R.string.couldn_t_share, it.userMessage())) }
}

/** A picture to show full screen. */
data class MediaRequest(val sourceJson: String, val filename: String, val mime: String, val caption: String?)
