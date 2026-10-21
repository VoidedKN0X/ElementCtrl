package com.vknxworks.elementctrl.matrix

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.AudioInfo
import org.matrix.rustcomponents.sdk.DateDividerMode
import org.matrix.rustcomponents.sdk.EditedContent
import org.matrix.rustcomponents.sdk.EmbeddedEventDetails
import org.matrix.rustcomponents.sdk.EncryptedMessage
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.EventSendState
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.FileInfo
import org.matrix.rustcomponents.sdk.ImageInfo
import org.matrix.rustcomponents.sdk.Mentions
import org.matrix.rustcomponents.sdk.MessageFormat
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.PaginationStatusListener
import org.matrix.rustcomponents.sdk.PollKind
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.SendHandle
import org.matrix.rustcomponents.sdk.ShieldState
import org.matrix.rustcomponents.sdk.TaskHandle
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineConfiguration
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.RoomMessageEventMessageType
import org.matrix.rustcomponents.sdk.TimelineFilter
import org.matrix.rustcomponents.sdk.TimelineFocus
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.UploadParameters
import org.matrix.rustcomponents.sdk.UploadSource
import org.matrix.rustcomponents.sdk.VideoInfo
import org.matrix.rustcomponents.sdk.VirtualTimelineItem
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdownAsEmote
import uniffi.matrix_sdk.PaginationStatus
import uniffi.matrix_sdk_crypto.UtdCause
import uniffi.matrix_sdk_ui.TimelineEventShieldStateCode
import uniffi.matrix_sdk_ui.TimelineReadReceiptTracking
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

private const val TAG = "Timeline"

/** One room's live timeline (or one thread): items as UI models, back-pagination and every message action. */
class TimelineController(
    private val session: MatrixSession,
    private val room: Room,
    val threadRootId: String? = null,
    /** Open around this event (a search result or link) instead of at the newest messages. */
    val focusEventId: String? = null,
    /** Only photos, videos, files and audio, newest first in pages: for the media and files gallery. */
    val mediaOnly: Boolean = false,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var timeline: Timeline? = null
    private val handles = mutableListOf<TaskHandle>()
    private val diffs = Channel<List<TimelineDiff>>(Channel.UNLIMITED)
    private val items = ArrayList<TimelineEntry>()
    private val failedHandles = HashMap<String, SendHandle>()
    private val requestedReplies = HashSet<String>()

    private val _entries = MutableStateFlow<List<TimelineEntry>>(emptyList())
    /** Oldest first. */
    val entries: StateFlow<List<TimelineEntry>> = _entries.asStateFlow()

    private val _paginating = MutableStateFlow(false)
    val paginating: StateFlow<Boolean> = _paginating.asStateFlow()

    private val _atStart = MutableStateFlow(false)
    val atStart: StateFlow<Boolean> = _atStart.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start() {
        scope.launch {
            runCatching {
                val config = TimelineConfiguration(
                    focus = when {
                        threadRootId != null -> TimelineFocus.Thread(threadRootId)
                        focusEventId != null -> TimelineFocus.Event(
                            eventId = focusEventId,
                            numContextEvents = 40u,
                            threadMode = uniffi.matrix_sdk_ui.TimelineEventFocusThreadMode.Automatic(true),
                        )
                        else -> TimelineFocus.Live(hideThreadedEvents = !mediaOnly)
                    },
                    filter = if (mediaOnly) TimelineFilter.OnlyMessage(
                        listOf(
                            RoomMessageEventMessageType.IMAGE, RoomMessageEventMessageType.VIDEO,
                            RoomMessageEventMessageType.FILE, RoomMessageEventMessageType.AUDIO,
                        )
                    ) else TimelineFilter.All,
                    internalIdPrefix = when { threadRootId != null -> "thread"; focusEventId != null -> "focus"; mediaOnly -> "media"; else -> "live" },
                    dateDividerMode = if (mediaOnly) DateDividerMode.MONTHLY else DateDividerMode.DAILY,
                    trackReadReceipts = TimelineReadReceiptTracking.MESSAGE_LIKE_EVENTS,
                    reportUtds = false,
                )
                val t = room.timelineWithConfiguration(config)
                timeline = t
                handles += t.addListener(object : TimelineListener {
                    override fun onUpdate(diff: List<TimelineDiff>) { diffs.trySend(diff) }
                })
                scope.launch { for (batch in diffs) apply(batch) }
                // The pagination-status stream exists only for the live timeline; threads and event-focused
                // timelines throw here, so they rely on paginateBackwards()' return value instead.
                if (threadRootId == null && focusEventId == null) {
                    handles += t.subscribeToBackPaginationStatus(object : PaginationStatusListener {
                        override fun onUpdate(status: PaginationStatus) {
                            when (status) {
                                is PaginationStatus.Idle -> { _paginating.value = false; _atStart.value = status.hitTimelineStart }
                                is PaginationStatus.Paginating -> _paginating.value = true
                            }
                        }
                    })
                }
                scope.launch { runCatching { t.fetchMembers() } }
                if (_entries.value.size < 20) paginateBack()
            }.onFailure {
                Log.e(TAG, "timeline failed", it)
                _error.value = it.userMessage()
            }
        }
    }

    fun close() {
        handles.forEach { runCatching { it.cancel() } }
        handles.clear()
        diffs.close()
        val t = timeline
        if (t != null && seenLatest && threadRootId == null && focusEventId == null && !mediaOnly) {
            // Read to the end: move the "new messages" line before letting go of the timeline.
            com.vknxworks.elementctrl.ElementCtrlApp.graph.scope.launch {
                runCatching { t.markAsRead(ReceiptType.FULLY_READ) }
                runCatching { t.close() }
            }
        } else runCatching { t?.close() }
        scope.cancel()
    }

    // region diffs

    private fun apply(batch: List<TimelineDiff>) {
        for (d in batch) {
            when (d) {
                is TimelineDiff.Append -> items.addAll(d.values.map(::map))
                is TimelineDiff.PushBack -> items.add(map(d.value))
                is TimelineDiff.PushFront -> items.add(0, map(d.value))
                is TimelineDiff.Insert -> items.add(d.index.toInt().coerceAtMost(items.size), map(d.value))
                is TimelineDiff.Set -> if (d.index.toInt() < items.size) items[d.index.toInt()] = map(d.value)
                is TimelineDiff.Remove -> if (d.index.toInt() < items.size) items.removeAt(d.index.toInt())
                is TimelineDiff.Truncate -> while (items.size > d.length.toInt()) items.removeAt(items.lastIndex)
                is TimelineDiff.Reset -> { items.clear(); items.addAll(d.values.map(::map)) }
                TimelineDiff.Clear -> items.clear()
                TimelineDiff.PopBack -> items.removeLastOrNull()
                TimelineDiff.PopFront -> items.removeFirstOrNull()
            }
        }
        _entries.value = ArrayList(items)
    }

    private fun map(item: TimelineItem): TimelineEntry = item.use { ti ->
        val key = ti.uniqueId().id
        ti.asEvent()?.let { ev ->
            if (ev.content.isCallMembership()) return@use TimelineEntry.Hidden(key)
            return@use mapEvent(key, ev)
        }
        when (val v = ti.asVirtual()) {
            is VirtualTimelineItem.DateDivider -> TimelineEntry.Day(key, v.ts.toLong())
            is VirtualTimelineItem.ReadMarker -> TimelineEntry.ReadMarker(key)
            is VirtualTimelineItem.TimelineStart -> TimelineEntry.Start(key)
            null -> TimelineEntry.ReadMarker(key)
        }
    }

    private fun mapEvent(key: String, e: EventTimelineItem): TimelineEntry.Message {
        val eventId = (e.eventOrTransactionId as? EventOrTransactionId.EventId)?.eventId
        val txnId = (e.eventOrTransactionId as? EventOrTransactionId.TransactionId)?.transactionId
        val senderName = e.senderProfile.nameOr(e.sender)
        var reply: ReplyPreview? = null
        var edited = false
        var threadReplies = 0
        var threadLatest: String? = null

        val content: UiContent = when (val c = e.content) {
            is TimelineItemContent.MsgLike -> {
                val msgLike = c.content
                msgLike.inReplyTo?.let { r ->
                    val id = r.eventId()
                    reply = when (val ev = r.event()) {
                        is EmbeddedEventDetails.Ready -> ReplyPreview(id, ev.senderProfile.nameOr(ev.sender), ev.content.previewText())
                        else -> {
                            if (requestedReplies.add(id)) scope.launch { runCatching { timeline?.fetchDetailsForEvent(id) } }
                            ReplyPreview(id, null, null)
                        }
                    }
                }
                msgLike.threadSummary?.let { ts ->
                    threadReplies = ts.numReplies().toInt()
                    threadLatest = (ts.latestEvent() as? EmbeddedEventDetails.Ready)?.let { "${it.senderProfile.nameOr(it.sender)}: ${it.content.previewText()}" }
                }
                when (val kind = msgLike.kind) {
                    is MsgLikeKind.Message -> {
                        edited = kind.content.isEdited
                        mapMessage(kind.content.msgType, kind.content.body)
                    }
                    is MsgLikeKind.Sticker -> UiContent.Image(
                        sourceJson = kind.source.toJson(),
                        thumbnailJson = kind.info.thumbnailSource?.toJson(),
                        width = kind.info.width?.toInt() ?: 0,
                        height = kind.info.height?.toInt() ?: 0,
                        caption = null,
                        filename = kind.body,
                        mime = kind.info.mimetype ?: "image/png",
                        isSticker = true,
                    )
                    is MsgLikeKind.Poll -> {
                        val total = kind.votes.values.sumOf { it.size }
                        UiContent.Poll(
                            question = kind.question,
                            answers = kind.answers.map { a ->
                                val voters = kind.votes[a.id].orEmpty()
                                PollAnswerUi(a.id, a.text, voters.size, session.userId in voters)
                            },
                            totalVotes = total,
                            maxSelections = kind.maxSelections.toInt(),
                            ended = kind.endTime != null,
                            disclosed = kind.kind == PollKind.DISCLOSED,
                        )
                    }
                    is MsgLikeKind.Redacted -> UiContent.Redacted
                    is MsgLikeKind.UnableToDecrypt -> utdContent(kind.msg)
                    is MsgLikeKind.LiveLocation -> UiContent.Location(Strings.str(R.string.live_location_2), kind.content.locations.lastOrNull()?.geoUri ?: "")
                    is MsgLikeKind.Other -> UiContent.Unsupported(Strings.str(R.string.unsupported_event))
                }
            }
            is TimelineItemContent.RoomMembership -> UiContent.Event(
                membershipText(c.userDisplayName ?: c.userId.localpart(), c.change).let { base ->
                    if (c.change == org.matrix.rustcomponents.sdk.MembershipChange.KICKED || c.change == org.matrix.rustcomponents.sdk.MembershipChange.BANNED) {
                        c.reason?.let { Strings.str(R.string.by_with_reason, base, senderName, it) } ?: Strings.str(R.string.by, base, senderName)
                    } else base
                }
            )
            is TimelineItemContent.ProfileChange -> UiContent.Event(
                profileChangeText(senderName, c.displayName, c.prevDisplayName, c.avatarUrl, c.prevAvatarUrl)
            )
            is TimelineItemContent.State -> UiContent.Event(stateText(senderName, c.content))
            is TimelineItemContent.CallInvite, is TimelineItemContent.RtcNotification -> UiContent.Event(Strings.str(R.string.started_a_call, senderName))
            is TimelineItemContent.FailedToParseMessageLike -> UiContent.Unsupported(Strings.str(R.string.couldn_t_read_this_message, c.eventType))
            is TimelineItemContent.FailedToParseState -> UiContent.Event(Strings.str(R.string.couldn_t_read_a_room_event, c.eventType))
        }

        val status = when (val s = e.localSendState) {
            null, is EventSendState.Sent -> SendStatus.SENT
            is EventSendState.NotSentYet -> SendStatus.SENDING
            is EventSendState.SendingFailed -> {
                e.lazyProvider.getSendHandle()?.let { failedHandles[key] = it }
                SendStatus.FAILED
            }
        }

        val shield = runCatching { e.lazyProvider.getShields(false) }.getOrNull()?.let { s ->
            when (s) {
                is ShieldState.Red -> ShieldUi(true, shieldText(s.code))
                is ShieldState.Grey -> ShieldUi(false, shieldText(s.code))
                is ShieldState.None -> null
            }
        }

        return TimelineEntry.Message(
            key = key,
            eventId = eventId,
            transactionId = txnId,
            sender = e.sender,
            senderName = senderName,
            senderAvatar = e.senderProfile.avatarOrNull(),
            isOwn = e.isOwn,
            timestamp = e.timestamp.toLong(),
            content = content,
            reactions = e.reactions.map { r ->
                ReactionUi(r.key, r.senders.size, r.senders.any { it.senderId == session.userId }, r.senders.map { it.senderId })
            },
            reply = reply,
            edited = edited,
            status = status,
            canReply = e.canBeRepliedTo,
            isEditable = e.isEditable,
            threadReplies = threadReplies,
            threadLatest = threadLatest,
            readBy = e.readReceipts.keys.filter { it != session.userId && it != e.sender },
            shield = shield,
            receipts = e.readReceipts.entries
                .filter { it.key != session.userId && it.key != e.sender }
                .map { ReceiptUi(it.key, it.value.timestamp?.toLong()) }
                .sortedByDescending { it.timestamp ?: 0L },
        )
    }

    private fun mapMessage(type: MessageType, body: String): UiContent = when (type) {
        is MessageType.Text -> UiContent.Text(type.content.body, type.content.formatted.html())
        is MessageType.Notice -> UiContent.Text(type.content.body, type.content.formatted.html(), TextKind.NOTICE)
        is MessageType.Emote -> UiContent.Text(type.content.body, type.content.formatted.html(), TextKind.EMOTE)
        is MessageType.Image -> UiContent.Image(
            sourceJson = type.content.source.toJson(),
            thumbnailJson = type.content.info?.thumbnailSource?.toJson(),
            width = type.content.info?.width?.toInt() ?: 0,
            height = type.content.info?.height?.toInt() ?: 0,
            caption = type.content.caption,
            filename = type.content.filename,
            mime = type.content.info?.mimetype ?: "image/jpeg",
        )
        is MessageType.Video -> UiContent.Video(
            sourceJson = type.content.source.toJson(),
            thumbnailJson = type.content.info?.thumbnailSource?.toJson(),
            width = type.content.info?.width?.toInt() ?: 0,
            height = type.content.info?.height?.toInt() ?: 0,
            durationMs = type.content.info?.duration?.toMillis() ?: 0L,
            caption = type.content.caption,
            filename = type.content.filename,
            mime = type.content.info?.mimetype ?: "video/mp4",
        )
        is MessageType.Audio -> UiContent.Audio(
            sourceJson = type.content.source.toJson(),
            filename = type.content.filename,
            mime = type.content.info?.mimetype ?: "audio/ogg",
            durationMs = type.content.info?.duration?.toMillis() ?: type.content.audio?.duration?.toMillis() ?: 0L,
            isVoice = type.content.voice != null,
            caption = type.content.caption,
            waveform = type.content.audio?.waveform?.let { w ->
                // Senders scale the waveform to 0..1024; normalise to the loudest bar.
                val max = (w.maxOfOrNull { it.toInt() } ?: 0).coerceAtLeast(1)
                w.map { it.toInt().toFloat() / max }
            }.orEmpty(),
        )
        is MessageType.File -> UiContent.File(
            sourceJson = type.content.source.toJson(),
            filename = type.content.filename,
            mime = type.content.info?.mimetype ?: "application/octet-stream",
            size = type.content.info?.size?.toLong() ?: 0L,
            caption = type.content.caption,
        )
        is MessageType.Location -> UiContent.Location(type.content.body, type.content.geoUri)
        is MessageType.Gallery -> UiContent.Text(type.content.body.ifBlank { Strings.str(R.string.gallery) }, type.content.formatted.html())
        is MessageType.Other -> UiContent.Text(body, null)
    }

    private fun org.matrix.rustcomponents.sdk.FormattedBody?.html(): String? =
        this?.takeIf { it.format is MessageFormat.Html }?.body

    private fun utdContent(msg: EncryptedMessage): UiContent.Encrypted {
        val cause = (msg as? EncryptedMessage.MegolmV1AesSha2)?.cause
        val historical = cause == UtdCause.HISTORICAL_MESSAGE_AND_BACKUP_IS_DISABLED ||
            cause == UtdCause.HISTORICAL_MESSAGE_AND_DEVICE_IS_UNVERIFIED
        return UiContent.Encrypted(utdReason(cause), historical)
    }

    private fun utdReason(cause: UtdCause?): String {
        return when (cause) {
            UtdCause.SENT_BEFORE_WE_JOINED -> Strings.str(R.string.you_don_t_have_access_to_this)
            UtdCause.VERIFICATION_VIOLATION -> Strings.str(R.string.not_shown_the_sender_s_verified)
            UtdCause.UNSIGNED_DEVICE, UtdCause.UNKNOWN_DEVICE -> Strings.str(R.string.not_shown_sent_from_an_unverified)
            UtdCause.HISTORICAL_MESSAGE_AND_BACKUP_IS_DISABLED -> Strings.str(R.string.older_message_turn_on_key_storage_in)
            UtdCause.HISTORICAL_MESSAGE_AND_DEVICE_IS_UNVERIFIED -> Strings.str(R.string.older_message_verify_this_device_to)
            UtdCause.WITHHELD_FOR_UNVERIFIED_OR_INSECURE_DEVICE -> Strings.str(R.string.the_sender_doesn_t_share_keys_with)
            UtdCause.WITHHELD_BY_SENDER -> Strings.str(R.string.the_sender_didn_t_share_the_key_for)
            else -> Strings.str(R.string.waiting_for_this_message_it_may_take)
        }
    }

    private fun shieldText(code: TimelineEventShieldStateCode) = when (code) {
        TimelineEventShieldStateCode.AUTHENTICITY_NOT_GUARANTEED -> Strings.str(R.string.the_authenticity_of_this_encrypted)
        TimelineEventShieldStateCode.UNKNOWN_DEVICE -> Strings.str(R.string.sent_from_an_unknown_or_deleted)
        TimelineEventShieldStateCode.UNSIGNED_DEVICE -> Strings.str(R.string.sent_from_a_device_its_owner_hasn_t)
        TimelineEventShieldStateCode.UNVERIFIED_IDENTITY -> Strings.str(R.string.sent_by_an_unverified_user)
        TimelineEventShieldStateCode.VERIFICATION_VIOLATION -> Strings.str(R.string.the_sender_s_verified_identity_has)
        TimelineEventShieldStateCode.MISMATCHED_SENDER -> Strings.str(R.string.the_sender_of_this_event_doesn_t)
        TimelineEventShieldStateCode.SENT_IN_CLEAR -> Strings.str(R.string.not_encrypted)
    }

    // endregion

    // region actions

    private fun TimelineEntry.Message.id(): EventOrTransactionId? = when {
        eventId != null -> EventOrTransactionId.EventId(eventId)
        transactionId != null -> EventOrTransactionId.TransactionId(transactionId)
        else -> null
    }

    private fun launchAction(block: suspend (Timeline) -> Unit) {
        val t = timeline ?: return
        scope.launch { runCatching { block(t) }.onFailure { _error.value = it.userMessage() } }
    }

    fun clearError() { _error.value = null }

    fun paginateBack() {
        if (_paginating.value || _atStart.value) return
        val t = timeline ?: return
        _paginating.value = true
        scope.launch {
            runCatching { _atStart.value = t.paginateBackwards(30u) }
            _paginating.value = false
        }
    }

    private var paginatingForward = false

    /** In an event-focused timeline, loads newer messages when scrolled to the bottom. */
    fun paginateForward() {
        if (focusEventId == null || paginatingForward) return
        val t = timeline ?: return
        paginatingForward = true
        scope.launch {
            runCatching { t.paginateForwards(30u) }
            paginatingForward = false
        }
    }

    fun send(text: String, replyTo: String?, mentions: List<String>, emote: Boolean = false) = launchAction { t ->
        val base = if (emote) messageEventContentFromMarkdownAsEmote(text) else messageEventContentFromMarkdown(text)
        val content = base.withMentions(Mentions(userIds = mentions.distinct(), room = text.contains("@room")))
        if (replyTo != null) t.sendReply(content, replyTo) else t.send(content)
    }

    fun edit(item: TimelineEntry.Message, text: String, mentions: List<String>) = launchAction { t ->
        val id = item.id() ?: return@launchAction
        val content = messageEventContentFromMarkdown(text).withMentions(Mentions(mentions.distinct(), false))
        t.edit(id, EditedContent.RoomMessage(content))
    }

    fun editCaption(item: TimelineEntry.Message, caption: String) = launchAction { t ->
        val id = item.id() ?: return@launchAction
        t.edit(id, EditedContent.MediaCaption(caption.ifBlank { null }, null, null))
    }

    fun toggleReaction(item: TimelineEntry.Message, key: String) = launchAction { t ->
        item.id()?.let { t.toggleReaction(it, key) }
        // Remember what you react with, so it comes first in the quick reactions (synced with Element).
        if (item.reactions.none { it.key == key && it.mine }) runCatching { session.client.addRecentEmoji(key) }
    }

    /** Your recently used reaction emojis, most recent first (shared with other Matrix apps through account data). */
    suspend fun recentEmojis(): List<String> = runCatching {
        kotlinx.coroutines.withContext(Dispatchers.IO) { session.client.getRecentEmojis().map { it.emoji } }
    }.getOrDefault(emptyList())

    fun redact(item: TimelineEntry.Message, reason: String? = null) = launchAction { t ->
        item.id()?.let { t.redactEvent(it, reason) }
    }

    fun retry(item: TimelineEntry.Message) = launchAction { failedHandles.remove(item.key)?.tryResend() }

    fun cancelSend(item: TimelineEntry.Message) = launchAction { failedHandles.remove(item.key)?.abort(null) }

    fun vote(pollEventId: String, answerId: String) = launchAction { it.sendPollResponse(pollEventId, listOf(answerId)) }

    fun endPoll(pollEventId: String) = launchAction { it.endPoll(pollEventId, Strings.str(R.string.the_poll_has_ended)) }

    fun createPoll(question: String, answers: List<String>, disclosed: Boolean) = launchAction {
        it.createPoll(question, answers, 1u, if (disclosed) PollKind.DISCLOSED else PollKind.UNDISCLOSED)
    }

    fun pin(eventId: String, pin: Boolean) = launchAction { if (pin) it.pinEvent(eventId) else it.unpinEvent(eventId) }

    fun markAsRead() = launchAction { it.markAsRead(ReceiptType.READ) }

    /** Whether the newest message is on screen right now; then leaving the chat moves the "new messages" line. */
    @Volatile var seenLatest = false

    /** Moves the room's fully-read marker (the "new messages" line) to the newest message. */
    fun markFullyRead() {
        if (threadRootId != null || focusEventId != null || mediaOnly) return
        launchAction { it.markAsRead(ReceiptType.FULLY_READ) }
    }

    fun sendLocation(lat: Double, lon: Double) = launchAction {
        val geo = "geo:$lat,$lon"
        it.sendLocation(Strings.str(R.string.location_2, lat, lon), geo, null, 15u, null, null)
    }

    /** Sends a recorded voice message (shown with a waveform in Element and other clients). */
    fun sendVoice(file: java.io.File, durationMs: Long, waveform: List<Float>, replyTo: String?) = launchAction { t ->
        val params = UploadParameters(
            source = UploadSource.File(file.absolutePath),
            caption = null,
            formattedCaption = null,
            mentions = null,
            inReplyTo = replyTo,
        )
        try {
            t.sendVoiceMessage(
                params,
                AudioInfo(duration = java.time.Duration.ofMillis(durationMs), size = file.length().toULong(), mimetype = "audio/ogg"),
                waveform,
            ).join()
        } finally {
            // The SDK has its own (encrypted) copy once the send is queued; don't leave the recording lying around.
            file.delete()
        }
    }

    /** The event's raw JSON, for "View source". */
    suspend fun source(eventId: String): String? = withContext(Dispatchers.IO) {
        runCatching { timeline?.getEventTimelineItemByEventId(eventId)?.lazyProvider?.debugInfo()?.originalJson }.getOrNull()
    }

    /** Copies a picked file into the cache and sends it as an image, video, audio clip or file. */
    fun sendAttachment(context: Context, uri: Uri, caption: String?, replyTo: String?) = launchAction { t ->
        // Shared/picked files must come from another app, never from our own private storage.
        if (!com.vknxworks.elementctrl.ui.isForeignContentUri(context, uri)) error(Strings.str(R.string.this_file_can_t_be_sent))
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: ("upload." + (MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"))
        val dir = File(context.cacheDir, "upload").apply { mkdirs() }
        // The display name comes from another app: keep it to a plain file name inside the upload folder.
        val safeName = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: "upload"
        val file = File(dir, "${System.currentTimeMillis()}_$safeName")
        if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) error(Strings.str(R.string.bad_file_name))
        try {
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: error(Strings.str(R.string.couldn_t_read_the_file))
            sendPrepared(t, file, mime, caption, replyTo)
        } finally {
            // Plain copy of what was sent: removed once the SDK has queued the upload.
            file.delete()
        }
    }

    /** Sends a file that's already on disk (a downloaded GIF, for example). */
    fun sendLocalFile(file: File, mime: String, caption: String?, replyTo: String?) = launchAction { t ->
        sendPrepared(t, file, mime, caption, replyTo)
    }

    private suspend fun sendPrepared(t: Timeline, file: File, mime: String, caption: String?, replyTo: String?) {
        val params = UploadParameters(
            source = UploadSource.File(file.absolutePath),
            caption = caption?.takeIf { it.isNotBlank() },
            formattedCaption = null,
            mentions = null,
            inReplyTo = replyTo,
        )
        val handle = when {
            mime.startsWith("image/") -> {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                t.sendImage(
                    params, null,
                    ImageInfo(
                        height = opts.outHeight.takeIf { it > 0 }?.toULong(),
                        width = opts.outWidth.takeIf { it > 0 }?.toULong(),
                        mimetype = mime,
                        size = file.length().toULong(),
                        thumbnailInfo = null,
                        thumbnailSource = null,
                        blurhash = null,
                        isAnimated = mime == "image/gif",
                    )
                )
            }
            mime.startsWith("video/") -> {
                val (w, h, d) = videoMeta(file)
                t.sendVideo(
                    params, null,
                    VideoInfo(
                        duration = java.time.Duration.ofMillis(d),
                        height = h.toULong(),
                        width = w.toULong(),
                        mimetype = mime,
                        size = file.length().toULong(),
                        thumbnailInfo = null,
                        thumbnailSource = null,
                        blurhash = null,
                    )
                )
            }
            mime.startsWith("audio/") -> t.sendAudio(
                params,
                AudioInfo(duration = java.time.Duration.ofMillis(videoMeta(file).third), size = file.length().toULong(), mimetype = mime),
            )
            else -> t.sendFile(params, FileInfo(mimetype = mime, size = file.length().toULong(), thumbnailInfo = null, thumbnailSource = null))
        }
        handle.join()
    }

    private fun videoMeta(file: File): Triple<Int, Int, Long> = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(file.absolutePath)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Triple(w, h, d)
        }
    }.getOrDefault(Triple(0, 0, 0L))

    // endregion
}
