package com.vknxworks.elementctrl.ui.room

import androidx.compose.foundation.background
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vknxworks.elementctrl.data.BubbleStyle
import com.vknxworks.elementctrl.matrix.MxcImage
import com.vknxworks.elementctrl.matrix.ReactionUi
import com.vknxworks.elementctrl.matrix.SendStatus
import com.vknxworks.elementctrl.matrix.TextKind
import com.vknxworks.elementctrl.matrix.TimelineEntry
import com.vknxworks.elementctrl.matrix.UiContent
import com.vknxworks.elementctrl.ui.Avatar
import com.vknxworks.elementctrl.ui.formatDuration
import com.vknxworks.elementctrl.ui.formatSize
import com.vknxworks.elementctrl.ui.senderColor
import com.vknxworks.elementctrl.ui.timeOfDay

/** Callbacks a message row can trigger. */
class MessageActions(
    val onLongPress: (TimelineEntry.Message) -> Unit,
    val onReact: (TimelineEntry.Message, String) -> Unit,
    val onOpenMedia: (TimelineEntry.Message) -> Unit,
    val onOpenUser: (String) -> Unit,
    val onOpenThread: (TimelineEntry.Message) -> Unit,
    val onJumpToReply: (String) -> Unit,
    val onVote: (TimelineEntry.Message, String) -> Unit,
    val onRetry: (TimelineEntry.Message) -> Unit,
    /** This device isn't verified, so it can't get keys from your other devices or the backup. */
    val deviceUnverified: Boolean = false,
    /** Key storage is set up but this device doesn't have the recovery key yet. */
    val recoveryIncomplete: Boolean = false,
    val onFixDecryption: () -> Unit = {},
    /** Play or pause an audio clip or voice message inline. */
    val onToggleAudio: (TimelineEntry.Message) -> Unit = {},
    /** Loads a link preview through the homeserver; null when previews are off for this chat. */
    val loadPreview: (suspend (String) -> com.vknxworks.elementctrl.matrix.LinkPreview?)? = null,
    val onOpenLink: (String) -> Unit = {},
    /** A formatted link whose text shows a different address than where it really goes: (real URL, shown text). */
    val onSuspiciousLink: (String, String) -> Unit = { _, _ -> },
    /** Swipe right on a message to reply; null where you can't post. */
    val onSwipeReply: ((TimelineEntry.Message) -> Unit)? = null,
    /** Double-tapping a message reacts with this emoji; null turns it off. */
    val doubleTapReaction: String? = null,
    /** Show small pictures of who has read up to each message. */
    val showReceipts: Boolean = false,
    /** Name and picture for a user ID, from the room's member list. */
    val member: (String) -> com.vknxworks.elementctrl.matrix.MemberInfo? = { null },
    val onShowReceipts: (TimelineEntry.Message) -> Unit = {},
)

/** The chat bubble design picked in Settings › Appearance; provided by the room screen. */
val LocalBubbleStyle = androidx.compose.runtime.staticCompositionLocalOf { BubbleStyle.MODERN }
/** Profile pictures next to every message group, in DMs and next to your own messages too. */
val LocalChatAvatars = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun MessageRow(
    item: TimelineEntry.Message,
    showSender: Boolean,
    isDm: Boolean,
    actions: MessageActions,
    highlighted: Boolean = false,
    /** Last message of a run from the same sender: it carries the profile picture and the bubble tail. */
    lastInGroup: Boolean = true,
) {
    if (item.isStateLike) {
        Text(
            (item.content as UiContent.Event).text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 4.dp),
        )
        return
    }
    val style = LocalBubbleStyle.current
    val cs = MaterialTheme.colorScheme
    val flat = style == BubbleStyle.FLAT
    // In the flat layout everything sits on the left, so your own messages need a name and avatar too.
    val own = item.isOwn && !flat
    val avatars = LocalChatAvatars.current
    val withAvatar = flat || avatars || (!own && !isDm)
    // Names are only needed where several people talk; a DM's avatar says enough.
    val withName = flat || (!own && !isDm)
    val bubbleColor = when {
        highlighted -> cs.tertiaryContainer
        flat || style == BubbleStyle.OUTLINED -> Color.Transparent
        style == BubbleStyle.VIVID && own -> cs.primary
        own -> cs.primaryContainer
        else -> cs.surfaceContainerHigh
    }
    val onBubble = when {
        highlighted -> cs.onTertiaryContainer
        flat || style == BubbleStyle.OUTLINED -> cs.onSurface
        style == BubbleStyle.VIVID && own -> cs.onPrimary
        own -> cs.onPrimaryContainer
        else -> cs.onSurface
    }
    val shape = when (style) {
        BubbleStyle.MODERN, BubbleStyle.OUTLINED, BubbleStyle.VIVID -> RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            // Only the last bubble of a run keeps the tail, next to the profile picture.
            bottomStart = if (own || !lastInGroup) 18.dp else 6.dp,
            bottomEnd = if (!own || !lastInGroup) 18.dp else 6.dp,
        )
        BubbleStyle.ROUND -> RoundedCornerShape(22.dp)
        BubbleStyle.SHARP -> RoundedCornerShape(4.dp)
        BubbleStyle.FLAT -> RoundedCornerShape(8.dp)
    }
    val border = if (style == BubbleStyle.OUTLINED && !highlighted) {
        androidx.compose.foundation.BorderStroke(1.dp, if (own) cs.primary.copy(alpha = 0.7f) else cs.outlineVariant)
    } else null
    val isMedia = item.content is UiContent.Image || item.content is UiContent.Video
    val bigEmoji = (item.content as? UiContent.Text)?.let { it.html == null && isOnlyEmoji(it.body) } == true

    // Swipe a message to the right to reply to it, like other chat apps.
    val swipeReply = actions.onSwipeReply?.takeIf { item.canReply && item.eventId != null }
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val threshold = with(density) { 64.dp.toPx() }
    var swipe by remember(item.key) { mutableFloatStateOf(0f) }
    var armed by remember(item.key) { mutableStateOf(false) }
    val swipeScope = rememberCoroutineScope()
    val currentSwipeReply by androidx.compose.runtime.rememberUpdatedState(swipeReply)
    fun settle() {
        armed = false
        swipeScope.launch { animate(swipe, 0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f)) { v, _ -> swipe = v } }
    }
    Box(
        Modifier.fillMaxWidth().then(
            if (swipeReply == null) Modifier
            else Modifier.pointerInput(item.key) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (swipe >= threshold) currentSwipeReply?.invoke(item); settle() },
                    onDragCancel = { settle() },
                ) { change, drag ->
                    // Past the trigger point it pulls harder, so it's clear the reply is armed.
                    val resistance = if (swipe >= threshold && drag > 0) 0.25f else 1f
                    swipe = (swipe + drag * resistance).coerceIn(0f, threshold * 1.5f)
                    if (!armed && swipe >= threshold) {
                        armed = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (armed && swipe < threshold) armed = false
                    change.consume()
                }
            }
        ),
    ) {
        if (swipe > 0f) {
            val progress = (swipe / threshold).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(34.dp)
                    .graphicsLayer { alpha = progress; scaleX = 0.5f + 0.5f * progress; scaleY = scaleX }
                    .background(if (armed) cs.primary else cs.surfaceContainerHighest, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(18.dp), tint = if (armed) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
        Row(
            Modifier.offset { IntOffset(swipe.roundToInt(), 0) }.fillMaxWidth().padding(
                start = 8.dp, end = 8.dp,
                top = if (showSender) (if (flat) 10.dp else 8.dp) else (if (style == BubbleStyle.SHARP) 0.dp else 1.dp),
                bottom = 1.dp,
            ),
            horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
            // Bubble layouts put the picture level with the last message of a run; the flat layout keeps it by the name at the top.
            verticalAlignment = if (flat) Alignment.Top else Alignment.Bottom,
        ) {
            val avatarSize = if (flat) 36.dp else 32.dp
            // One picture per group of messages from the same person: by the last bubble, or by the name in the flat layout.
            val avatarHere = if (flat) showSender else lastInGroup
            @Composable
            fun SenderAvatar() {
                if (avatarHere) {
                    Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).clickable { actions.onOpenUser(item.sender) }) {
                        Avatar(item.senderName, item.senderAvatar, item.sender, size = avatarSize)
                    }
                } else {
                    Spacer(Modifier.width(avatarSize))
                }
            }
            if (withAvatar && !own) {
                SenderAvatar()
                Spacer(Modifier.width(if (flat) 10.dp else 6.dp))
            }
            Column(
                horizontalAlignment = if (own) Alignment.End else Alignment.Start,
                modifier = if (flat) Modifier.weight(1f) else Modifier.widthIn(max = 320.dp),
            ) {
                if (showSender && withName) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = if (flat) 0.dp else 12.dp, bottom = 2.dp)) {
                        Text(
                            if (flat && item.isOwn) "You" else item.senderName,
                            style = MaterialTheme.typography.labelLarge,
                            color = senderColor(item.sender),
                            modifier = Modifier.clickable { actions.onOpenUser(item.sender) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (flat) {
                            Spacer(Modifier.width(8.dp))
                            Text(timeOfDay(item.timestamp), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
                // Double-tap to react: the emoji pops up over the bubble.
                val tapReaction = actions.doubleTapReaction?.takeIf {
                    item.eventId != null && item.content !is UiContent.Redacted && item.status == SendStatus.SENT
                }
                val pop = remember(item.key) { androidx.compose.animation.core.Animatable(0f) }
                val popScope = rememberCoroutineScope()
                Box(contentAlignment = Alignment.Center) {
                Surface(
                    color = if (bigEmoji) Color.Transparent else bubbleColor,
                    contentColor = onBubble,
                    shape = shape,
                    border = if (bigEmoji) null else border,
                    modifier = Modifier.clip(shape).combinedClickable(
                        onClick = {
                            when (item.content) {
                                is UiContent.Image, is UiContent.Video, is UiContent.Audio, is UiContent.File, is UiContent.Location -> actions.onOpenMedia(item)
                                else -> if (item.status == SendStatus.FAILED) actions.onRetry(item)
                            }
                        },
                        onLongClick = { actions.onLongPress(item) },
                        onDoubleClick = tapReaction?.let { emoji ->
                            {
                                // Only add: a second double-tap shouldn't quietly take the reaction away.
                                if (item.reactions.none { it.key == emoji && it.mine }) actions.onReact(item, emoji)
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                popScope.launch {
                                    pop.snapTo(0.01f)
                                    pop.animateTo(1f, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.LinearEasing))
                                    pop.snapTo(0f)
                                }
                            }
                        },
                    ),
                ) {
                    Column(Modifier.padding(if (isMedia && !flat) 4.dp else 0.dp)) {
                        item.reply?.let { r ->
                            ReplyQuote(
                                r.senderName, r.text, onBubble,
                                Modifier.padding(start = if (flat) 0.dp else 8.dp, end = 8.dp, top = if (flat) 2.dp else 8.dp).clickable { actions.onJumpToReply(r.eventId) },
                            )
                        }
                        MessageBody(item, onBubble, bigEmoji, actions, flat)
                        Row(
                            Modifier.align(if (flat) Alignment.Start else Alignment.End).padding(end = 10.dp, bottom = if (flat) 2.dp else 6.dp, start = if (flat) 0.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item.shield?.let {
                                Icon(
                                    Icons.Default.Shield, it.text, Modifier.size(12.dp),
                                    tint = if (it.red) cs.error else onBubble.copy(alpha = 0.6f),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            if (item.edited) {
                                Text(if (flat) "(edited)" else "edited · ", style = MaterialTheme.typography.labelSmall, color = onBubble.copy(alpha = 0.6f))
                            }
                            if (!flat) Text(timeOfDay(item.timestamp), style = MaterialTheme.typography.labelSmall, color = onBubble.copy(alpha = 0.6f))
                            if (item.isOwn) {
                                Spacer(Modifier.width(4.dp))
                                when (item.status) {
                                    SendStatus.SENDING -> Icon(Icons.Default.AccessTime, "Sending", Modifier.size(12.dp), tint = onBubble.copy(alpha = 0.6f))
                                    SendStatus.FAILED -> Icon(Icons.Default.ErrorOutline, "Not sent, tap to retry", Modifier.size(14.dp), tint = cs.error)
                                    SendStatus.SENT -> if (!flat || item.readBy.isNotEmpty()) Icon(
                                        Icons.Default.Done, if (item.readBy.isNotEmpty()) "Read" else "Sent", Modifier.size(12.dp),
                                        tint = if (item.readBy.isNotEmpty()) (if (style == BubbleStyle.VIVID && own) onBubble else cs.primary) else onBubble.copy(alpha = 0.6f),
                                    )
                                }
                            }
                        }
                    }
                }
                if (pop.value > 0f && tapReaction != null) {
                    val t = pop.value
                    // Grow in with a little overshoot, hold, then float up and fade.
                    val scale = when {
                        t < 0.25f -> 0.4f + 0.9f * (t / 0.25f)
                        t < 0.4f -> 1.3f - 0.3f * ((t - 0.25f) / 0.15f)
                        else -> 1f
                    }
                    Text(
                        tapReaction,
                        fontSize = 44.sp,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale; scaleY = scale
                            alpha = if (t < 0.7f) 1f else 1f - (t - 0.7f) / 0.3f
                            translationY = if (t < 0.6f) 0f else -60f * (t - 0.6f) / 0.4f
                        },
                    )
                }
                }
                if (item.status == SendStatus.FAILED) {
                    Text("Not sent. Tap to retry, hold for options.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(4.dp))
                }
                if (item.reactions.isNotEmpty()) {
                    ReactionsRow(item.reactions, own) { actions.onReact(item, it) }
                }
                if (item.threadReplies > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).clickable { actions.onOpenThread(item) },
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${item.threadReplies} ${if (item.threadReplies == 1) "reply" else "replies"}" + (item.threadLatest?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (actions.showReceipts && item.receipts.isNotEmpty()) {
                    ReadReceipts(
                        item.receipts, actions.member,
                        Modifier.align(Alignment.End).padding(top = 3.dp, end = 2.dp)
                            .clip(RoundedCornerShape(10.dp)).clickable { actions.onShowReceipts(item) },
                    )
                }
            }
            if (withAvatar && own) {
                Spacer(Modifier.width(6.dp))
                SenderAvatar()
            }
        }
    }
}

/** Overlapping pictures of who has read up to here, as in Element X; "+3" when there are more. */
@Composable
private fun ReadReceipts(
    receipts: List<com.vknxworks.elementctrl.matrix.ReceiptUi>,
    member: (String) -> com.vknxworks.elementctrl.matrix.MemberInfo?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val shown = receipts.take(4)
    Row(modifier.padding(horizontal = 2.dp, vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
            shown.forEach { r ->
                val m = member(r.userId)
                Box(Modifier.size(19.dp).background(cs.surface, androidx.compose.foundation.shape.CircleShape).padding(1.5.dp)) {
                    Avatar(m?.name ?: r.userId, m?.avatarUrl, r.userId, size = 16.dp)
                }
            }
        }
        if (receipts.size > shown.size) {
            Spacer(Modifier.width(3.dp))
            Text("+${receipts.size - shown.size}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageBody(item: TimelineEntry.Message, onBubble: Color, bigEmoji: Boolean, actions: MessageActions, flat: Boolean = false) {
    // Without a bubble there's nothing to inset from, so text lines up with the sender's name.
    val pad = if (flat) Modifier.padding(end = 12.dp, top = 1.dp, bottom = 2.dp)
    else Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp)
    when (val c = item.content) {
        is UiContent.Text -> {
            val text = remember(c.body, c.html) { formatText(c.body, c.html, actions.onSuspiciousLink) }
            when {
                bigEmoji -> Text(c.body, fontSize = 40.sp, lineHeight = 48.sp, modifier = Modifier.padding(4.dp))
                c.kind == TextKind.EMOTE -> Text(
                    buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("* ${item.senderName} ") }; append(text) },
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), modifier = pad,
                )
                c.kind == TextKind.NOTICE -> Text(text, style = MaterialTheme.typography.bodyLarge, color = onBubble.copy(alpha = 0.8f), modifier = pad)
                else -> Column {
                    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = pad)
                    val loader = actions.loadPreview
                    val link = remember(c.body) { com.vknxworks.elementctrl.matrix.LinkPreviews.firstUrl(c.body) }
                    if (loader != null && link != null) LinkPreviewCard(link, onBubble, loader, actions.onOpenLink)
                }
            }
        }
        is UiContent.Image -> {
            val ratio = if (c.width > 0 && c.height > 0) (c.width.toFloat() / c.height).coerceIn(0.5f, 2.2f) else 1.3f
            Column {
                AsyncImage(
                    // GIFs load the original so they animate; other pictures use a thumbnail.
                    model = remember(c.sourceJson) {
                        if (c.mime == "image/gif" || c.isSticker) MxcImage(sourceJson = c.sourceJson)
                        else MxcImage(sourceJson = c.thumbnailJson ?: c.sourceJson, size = if (c.thumbnailJson != null) 0 else 800)
                    },
                    contentDescription = c.caption ?: c.filename,
                    contentScale = if (c.isSticker) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier
                        .width(if (c.isSticker) 160.dp else 260.dp)
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (c.isSticker) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant),
                )
                c.caption?.takeIf { it.isNotBlank() }?.let { Text(formatText(it, null), style = MaterialTheme.typography.bodyLarge, modifier = pad) }
            }
        }
        is UiContent.Video -> {
            val ratio = if (c.width > 0 && c.height > 0) (c.width.toFloat() / c.height).coerceIn(0.5f, 2.2f) else 16f / 9f
            Column {
                Box(
                    Modifier.width(260.dp).aspectRatio(ratio).clip(RoundedCornerShape(14.dp)).background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    c.thumbnailJson?.let {
                        AsyncImage(model = remember(it) { MxcImage(sourceJson = it) }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSizeSafe())
                    }
                    Icon(Icons.Default.PlayCircle, "Play", Modifier.size(56.dp), tint = Color.White)
                    if (c.durationMs > 0) {
                        Text(
                            formatDuration(c.durationMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                c.caption?.takeIf { it.isNotBlank() }?.let { Text(formatText(it, null), style = MaterialTheme.typography.bodyLarge, modifier = pad) }
            }
        }
        is UiContent.Audio -> AudioRow(item.key, c, onBubble) { actions.onToggleAudio(item) }
        is UiContent.File -> AttachmentRow(
            icon = Icons.Default.AttachFile,
            title = c.filename,
            subtitle = listOf(formatSize(c.size), c.mime.substringAfter('/').uppercase()).filter { it.isNotBlank() }.joinToString(" · "),
            onBubble = onBubble,
            caption = c.caption,
        )
        is UiContent.Location -> AttachmentRow(Icons.Default.Place, "Location", c.geoUri.removePrefix("geo:").substringBefore(';'), onBubble, null)
        is UiContent.Poll -> PollView(c, onBubble) { answer -> actions.onVote(item, answer) }
        is UiContent.Redacted -> Row(pad, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Block, null, Modifier.size(16.dp), tint = onBubble.copy(alpha = 0.6f))
            Spacer(Modifier.width(6.dp))
            Text("Message deleted", style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = onBubble.copy(alpha = 0.7f))
        }
        is UiContent.Encrypted -> {
            // Pick the wording from the live state, so it changes as soon as this device is verified.
            val (text, action) = when {
                actions.deviceUnverified -> "Verify this device to read this message." to "Verify"
                actions.recoveryIncomplete || c.historical -> "Enter your recovery key to read older messages." to "Enter key"
                else -> c.reason to null
            }
            Column(pad) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = onBubble.copy(alpha = 0.6f))
                    Spacer(Modifier.width(6.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = onBubble.copy(alpha = 0.7f))
                }
                if (action != null) TextButton(onClick = actions.onFixDecryption) { Text(action) }
            }
        }
        is UiContent.Unsupported -> Text(c.text, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = onBubble.copy(alpha = 0.7f), modifier = pad)
        is UiContent.Event -> Text(c.text, modifier = pad)
    }
}

/** Fills the parent Box without depending on BoxScope at the call site. */
private fun Modifier.matchParentSizeSafe(): Modifier = this.then(Modifier.fillMaxWidth().fillMaxHeight())

@Composable
private fun AttachmentRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onBubble: Color, caption: String?) {
    Column {
        Row(Modifier.padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 4.dp).widthIn(min = 200.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(onBubble.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = onBubble)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = onBubble.copy(alpha = 0.7f))
            }
        }
        caption?.takeIf { it.isNotBlank() }?.let { Text(formatText(it, null), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) }
    }
}

@Composable
private fun PollView(poll: UiContent.Poll, onBubble: Color, onVote: (String) -> Unit) {
    val voted = poll.answers.any { it.mine }
    val showResults = poll.ended || (poll.disclosed && voted)
    Column(Modifier.padding(12.dp).widthIn(min = 240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("📊 ${poll.question}", style = MaterialTheme.typography.titleSmall)
        poll.answers.forEach { a ->
            val fraction = if (poll.totalVotes > 0) a.votes.toFloat() / poll.totalVotes else 0f
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, if (a.mine) MaterialTheme.colorScheme.primary else onBubble.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable(enabled = !poll.ended) { onVote(a.id) }
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (a.mine) "✓ " else "") + a.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (showResults) Text("${a.votes}", style = MaterialTheme.typography.labelMedium)
                }
                if (showResults) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        Text(
            when {
                poll.ended -> "Final results · ${poll.totalVotes} votes"
                !poll.disclosed -> "Results will show when the poll ends"
                voted -> "${poll.totalVotes} votes"
                else -> "Tap an answer to vote"
            },
            style = MaterialTheme.typography.labelSmall,
            color = onBubble.copy(alpha = 0.7f),
        )
    }
}

@Composable
fun ReplyQuote(senderName: String?, text: String?, onBubble: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(onBubble.copy(alpha = 0.08f))
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(senderName ?: "Loading…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            Text(text ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = onBubble.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ReactionsRow(reactions: List<ReactionUi>, own: Boolean, onClick: (String) -> Unit) {
    FlowRow(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (own) Alignment.End else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { r ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (r.mine) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = if (r.mine) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onClick(r.key) },
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.key, fontSize = 15.sp)
                    if (r.count > 1) {
                        Spacer(Modifier.width(4.dp))
                        Text(r.count.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun DayDivider(ts: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                com.vknxworks.elementctrl.ui.dayLabel(ts),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun ReadMarkerLine() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.primary))
        Text("  New  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.primary))
    }
}

private val urlRegex = Regex("""(https?://[^\s<>"]+[^\s<>".,;:!?)\]}'])|(\bmatrix:[^\s]+)""")

private val allowedLinkSchemes = setOf("http", "https", "matrix", "mailto")

/** The host a piece of visible link text claims to point at ("example.com", "https://example.com/x"), or null. */
private fun shownHost(text: String): String? {
    val t = text.trim()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) t
    else if (Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}(:\\d+)?([/?#].*)?$").matches(t)) "https://$t"
    else return null
    return runCatching { android.net.Uri.parse(withScheme).host }.getOrNull()?.lowercase()?.removePrefix("www.")
}

/**
 * Keeps only links to http(s), matrix: and mailto: targets from formatted messages; anything else (intent:,
 * javascript:, content:, file: …) loses its link. When the visible text is a URL for a different host than
 * the real target, tapping goes through [onSuspiciousLink] so the real address can be shown first.
 */
private fun sanitiseLinks(source: AnnotatedString, linkStyle: TextLinkStyles, onSuspiciousLink: ((String, String) -> Unit)?): AnnotatedString {
    val links = source.getLinkAnnotations(0, source.length)
    if (links.isEmpty()) return source
    val builder = AnnotatedString.Builder(AnnotatedString(source.text, source.spanStyles, source.paragraphStyles))
    for (range in links) {
        val url = (range.item as? LinkAnnotation.Url)?.url?.trim() ?: continue
        val target = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: continue
        val scheme = target.scheme?.lowercase() ?: continue
        if (scheme !in allowedLinkSchemes) continue
        val shown = source.text.substring(range.start, range.end)
        val claimed = shownHost(shown)
        val real = target.host?.lowercase()?.removePrefix("www.")
        val mismatch = claimed != null && claimed != real
        val listener = if (mismatch && onSuspiciousLink != null) LinkInteractionListener { onSuspiciousLink(url, shown) } else null
        builder.addLink(LinkAnnotation.Url(url, linkStyle, listener), range.start, range.end)
    }
    return builder.toAnnotatedString()
}

/** Message text with tappable links; HTML-formatted messages keep bold, italics, code and links. */
fun formatText(body: String, html: String?, onSuspiciousLink: ((String, String) -> Unit)? = null): AnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium))
    if (html != null) {
        return runCatching { sanitiseLinks(AnnotatedString.fromHtml(html.replace("\n", "<br>"), linkStyles = linkStyle), linkStyle, onSuspiciousLink) }
            .getOrElse { AnnotatedString(body) }
    }
    return buildAnnotatedString {
        var last = 0
        for (m in urlRegex.findAll(body)) {
            append(body.substring(last, m.range.first))
            pushLink(LinkAnnotation.Url(m.value, linkStyle))
            append(m.value)
            pop()
            last = m.range.last + 1
        }
        append(body.substring(last))
    }
}

fun isOnlyEmoji(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.length > 16) return false
    return t.codePoints().allMatch { cp ->
        Character.getType(cp) == Character.OTHER_SYMBOL.toInt() ||
            cp == 0x200D || cp == 0xFE0F || cp in 0x1F3FB..0x1F3FF || Character.isWhitespace(cp) || cp in 0x1F1E6..0x1F1FF
    }
}


/** Inline player: play/pause, a waveform that fills as it plays (tap to seek), and the time. */
@Composable
private fun AudioRow(key: String, c: UiContent.Audio, onBubble: Color, onToggle: () -> Unit) {
    val player by AudioPlayer.state.collectAsStateWithLifecycle()
    val mine = player.key == key
    val playing = mine && player.playing
    val duration = if (mine && player.durationMs > 0) player.durationMs else c.durationMs
    val progress = if (mine && duration > 0) (player.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bars = remember(c.waveform) { if (c.waveform.isNotEmpty()) resample(c.waveform, 36) else List(36) { i -> 0.25f + 0.2f * kotlin.math.abs(kotlin.math.sin(i * 0.7f)) } }
    Column(Modifier.padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 2.dp).widthIn(min = 220.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(onBubble.copy(alpha = 0.14f)).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                if (mine && player.loading) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = onBubble)
                } else {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", tint = onBubble)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (c.isVoice || c.waveform.isNotEmpty()) {
                Row(
                    Modifier.weight(1f).height(32.dp).pointerInput(mine) {
                        detectTapGestures { pos -> if (mine) AudioPlayer.seek(pos.x / size.width) else onToggle() }
                    },
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    bars.forEachIndexed { i, v ->
                        val played = i.toFloat() / bars.size < progress
                        Box(
                            Modifier.weight(1f).fillMaxHeight((0.15f + 0.85f * v).coerceIn(0.15f, 1f)).clip(RoundedCornerShape(2.dp))
                                .background(if (played) onBubble else onBubble.copy(alpha = 0.35f)),
                        )
                    }
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(c.filename, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                formatDuration(if (mine && player.positionMs > 0) player.positionMs else duration),
                style = MaterialTheme.typography.labelMedium,
                color = onBubble.copy(alpha = 0.75f),
            )
        }
        c.caption?.takeIf { it.isNotBlank() }?.let { Text(formatText(it, null), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp)) }
    }
}

private fun resample(src: List<Float>, n: Int): List<Float> =
    List(n) { i -> src[((i.toFloat() / n) * src.size).toInt().coerceIn(0, src.lastIndex)] }

/** Title, description and picture for the first link in a message, as returned by the homeserver. */
@Composable
private fun LinkPreviewCard(
    url: String,
    onBubble: Color,
    loader: suspend (String) -> com.vknxworks.elementctrl.matrix.LinkPreview?,
    onOpen: (String) -> Unit,
) {
    var preview by remember(url) { mutableStateOf(com.vknxworks.elementctrl.matrix.LinkPreviews.cached(url)?.takeUnless { it.isEmpty }) }
    LaunchedEffect(url) { if (preview == null) preview = loader(url) }
    val p = preview ?: return
    Row(
        Modifier
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp)
            .widthIn(max = 296.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(onBubble.copy(alpha = 0.08f))
            .clickable { onOpen(url) }
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(8.dp).weight(1f, fill = false)) {
            p.siteName?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
            p.title?.let { Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = onBubble.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis) }
            p.imageUrl?.let { img ->
                AsyncImage(
                    model = remember(img) { MxcImage(url = img, size = 600) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(140.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}
