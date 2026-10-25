package com.vknxworks.elementctrl.matrix

/** A row in a room's timeline, already converted from the SDK's types. */
sealed interface TimelineEntry {
    val key: String

    data class Day(override val key: String, val timestamp: Long) : TimelineEntry
    data class ReadMarker(override val key: String) : TimelineEntry
    data class Start(override val key: String) : TimelineEntry
    /** An event that keeps its place in the list (the SDK's diffs count by position) but is never shown. */
    data class Hidden(override val key: String) : TimelineEntry
    data class Message(
        override val key: String,
        val eventId: String?,
        val transactionId: String?,
        val sender: String,
        val senderName: String,
        val senderAvatar: String?,
        val isOwn: Boolean,
        val timestamp: Long,
        val content: UiContent,
        val reactions: List<ReactionUi>,
        val reply: ReplyPreview?,
        val edited: Boolean,
        val status: SendStatus,
        val canReply: Boolean,
        val isEditable: Boolean,
        val threadReplies: Int,
        val threadLatest: String?,
        val readBy: List<String>,
        val shield: ShieldUi?,
        /** Who has read up to this message (their read receipt sits here), newest first. */
        val receipts: List<ReceiptUi> = emptyList(),
    ) : TimelineEntry {
        /** Small grey centred line (joins, renames…) rather than a chat bubble. */
        val isStateLike get() = content is UiContent.Event
    }
}

enum class SendStatus { SENT, SENDING, FAILED }

/** A grey or red padlock warning on a message from an unverified or untrusted device. */
data class ShieldUi(val red: Boolean, val text: String)

data class ReceiptUi(val userId: String, val timestamp: Long?)

data class ReactionUi(val key: String, val count: Int, val mine: Boolean, val senders: List<String>)

data class ReplyPreview(val eventId: String, val senderName: String?, val text: String?)

data class PollAnswerUi(val id: String, val text: String, val votes: Int, val mine: Boolean)

sealed interface UiContent {
    data class Text(val body: String, val html: String?, val kind: TextKind = TextKind.TEXT) : UiContent
    data class Image(
        val sourceJson: String,
        val thumbnailJson: String?,
        val width: Int,
        val height: Int,
        val caption: String?,
        val filename: String,
        val mime: String,
        val isSticker: Boolean = false,
    ) : UiContent
    data class Video(
        val sourceJson: String,
        val thumbnailJson: String?,
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val caption: String?,
        val filename: String,
        val mime: String,
    ) : UiContent
    data class Audio(
        val sourceJson: String,
        val filename: String,
        val mime: String,
        val durationMs: Long,
        val isVoice: Boolean,
        val caption: String?,
        /** Loudness bars 0–1 from the sender, for voice messages. */
        val waveform: List<Float> = emptyList(),
    ) : UiContent
    data class File(val sourceJson: String, val filename: String, val mime: String, val size: Long, val caption: String?) : UiContent
    data class Location(val body: String, val geoUri: String, val description: String? = null) : UiContent
    data class Poll(
        val question: String,
        val answers: List<PollAnswerUi>,
        val totalVotes: Int,
        val maxSelections: Int,
        val ended: Boolean,
        val disclosed: Boolean,
    ) : UiContent
    data object Redacted : UiContent
    /** [historical]: an older message whose key would come from the key backup. */
    data class Encrypted(val reason: String, val historical: Boolean = false) : UiContent
    /** Room events shown as a line of text: membership, profile and state changes. */
    data class Event(val text: String) : UiContent
    data class Unsupported(val text: String) : UiContent
}

enum class TextKind { TEXT, NOTICE, EMOTE }
