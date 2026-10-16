package com.vknxworks.elementctrl.ui.room

import androidx.compose.foundation.background
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
)

@Composable
fun MessageRow(
    item: TimelineEntry.Message,
    showSender: Boolean,
    isDm: Boolean,
    actions: MessageActions,
    highlighted: Boolean = false,
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
    val own = item.isOwn
    val bubbleColor = when {
        highlighted -> MaterialTheme.colorScheme.tertiaryContainer
        own -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onBubble = when {
        highlighted -> MaterialTheme.colorScheme.onTertiaryContainer
        own -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val isMedia = item.content is UiContent.Image || item.content is UiContent.Video
    val bigEmoji = (item.content as? UiContent.Text)?.let { it.html == null && isOnlyEmoji(it.body) } == true

    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = if (showSender) 8.dp else 1.dp, bottom = 1.dp),
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!own && !isDm) {
            if (showSender) {
                Box(Modifier.clickable { actions.onOpenUser(item.sender) }) {
                    Avatar(item.senderName, item.senderAvatar, item.sender, size = 32.dp)
                }
            } else {
                Spacer(Modifier.width(32.dp))
            }
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (own) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 320.dp)) {
            if (showSender && !own && !isDm) {
                Text(
                    item.senderName,
                    style = MaterialTheme.typography.labelLarge,
                    color = senderColor(item.sender),
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp).clickable { actions.onOpenUser(item.sender) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (own) 18.dp else 6.dp,
                bottomEnd = if (own) 6.dp else 18.dp,
            )
            Surface(
                color = if (bigEmoji) Color.Transparent else bubbleColor,
                contentColor = onBubble,
                shape = shape,
                modifier = Modifier.clip(shape).combinedClickable(
                    onClick = {
                        when (item.content) {
                            is UiContent.Image, is UiContent.Video, is UiContent.Audio, is UiContent.File, is UiContent.Location -> actions.onOpenMedia(item)
                            else -> if (item.status == SendStatus.FAILED) actions.onRetry(item)
                        }
                    },
                    onLongClick = { actions.onLongPress(item) },
                ),
            ) {
                Column(Modifier.padding(if (isMedia) 4.dp else 0.dp)) {
                    item.reply?.let { r ->
                        ReplyQuote(r.senderName, r.text, onBubble, Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp).clickable { actions.onJumpToReply(r.eventId) })
                    }
                    MessageBody(item, onBubble, bigEmoji, actions)
                    Row(
                        Modifier.align(Alignment.End).padding(end = 10.dp, bottom = 6.dp, start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        item.shield?.let {
                            Icon(
                                Icons.Default.Shield, it.text, Modifier.size(12.dp),
                                tint = if (it.red) MaterialTheme.colorScheme.error else onBubble.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        if (item.edited) {
                            Text("edited · ", style = MaterialTheme.typography.labelSmall, color = onBubble.copy(alpha = 0.6f))
                        }
                        Text(timeOfDay(item.timestamp), style = MaterialTheme.typography.labelSmall, color = onBubble.copy(alpha = 0.6f))
                        if (own) {
                            Spacer(Modifier.width(4.dp))
                            when (item.status) {
                                SendStatus.SENDING -> Icon(Icons.Default.AccessTime, "Sending", Modifier.size(12.dp), tint = onBubble.copy(alpha = 0.6f))
                                SendStatus.FAILED -> Icon(Icons.Default.ErrorOutline, "Not sent, tap to retry", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                SendStatus.SENT -> Icon(Icons.Default.Done, if (item.readBy.isNotEmpty()) "Read" else "Sent", Modifier.size(12.dp), tint = if (item.readBy.isNotEmpty()) MaterialTheme.colorScheme.primary else onBubble.copy(alpha = 0.6f))
                            }
                        }
                    }
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
        }
    }
}

@Composable
private fun MessageBody(item: TimelineEntry.Message, onBubble: Color, bigEmoji: Boolean, actions: MessageActions) {
    val pad = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 2.dp)
    when (val c = item.content) {
        is UiContent.Text -> {
            val text = remember(c.body, c.html) { formatText(c.body, c.html) }
            when {
                bigEmoji -> Text(c.body, fontSize = 40.sp, lineHeight = 48.sp, modifier = Modifier.padding(4.dp))
                c.kind == TextKind.EMOTE -> Text(
                    buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("* ${item.senderName} ") }; append(text) },
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic), modifier = pad,
                )
                c.kind == TextKind.NOTICE -> Text(text, style = MaterialTheme.typography.bodyLarge, color = onBubble.copy(alpha = 0.8f), modifier = pad)
                else -> Text(text, style = MaterialTheme.typography.bodyLarge, modifier = pad)
            }
        }
        is UiContent.Image -> {
            val ratio = if (c.width > 0 && c.height > 0) (c.width.toFloat() / c.height).coerceIn(0.5f, 2.2f) else 1.3f
            Column {
                AsyncImage(
                    model = remember(c.sourceJson) { MxcImage(sourceJson = c.thumbnailJson ?: c.sourceJson, size = if (c.thumbnailJson != null) 0 else 800) },
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
        is UiContent.Audio -> AttachmentRow(
            icon = if (c.isVoice) Icons.Default.GraphicEq else Icons.Default.MusicNote,
            title = if (c.isVoice) "Voice message" else c.filename,
            subtitle = if (c.durationMs > 0) formatDuration(c.durationMs) else "Audio",
            onBubble = onBubble,
            caption = c.caption,
        )
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

/** Message text with tappable links; HTML-formatted messages keep bold, italics, code and links. */
fun formatText(body: String, html: String?): AnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium))
    if (html != null) {
        return runCatching { AnnotatedString.fromHtml(html.replace("\n", "<br>"), linkStyles = linkStyle) }
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
