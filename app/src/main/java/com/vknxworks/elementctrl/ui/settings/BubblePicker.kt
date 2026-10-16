package com.vknxworks.elementctrl.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vknxworks.elementctrl.data.BubbleStyle
import com.vknxworks.elementctrl.matrix.ReactionUi
import com.vknxworks.elementctrl.matrix.SendStatus
import com.vknxworks.elementctrl.matrix.TimelineEntry
import com.vknxworks.elementctrl.matrix.UiContent
import com.vknxworks.elementctrl.ui.room.LocalBubbleStyle
import com.vknxworks.elementctrl.ui.room.MessageActions
import com.vknxworks.elementctrl.ui.room.MessageRow

/** Lets the user pick a chat bubble design, previewing each one with sample messages. */
@Composable
fun BubbleStylePicker(current: BubbleStyle, onPick: (BubbleStyle) -> Unit, onDismiss: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val theirs = remember { sample("a", "@ana:example.org", "Ana", "Are we still on for tonight? 🍕", false, now - 120_000, emptyList()) }
    val mine = remember { sample("b", "@me:example.org", "Me", "Yes! See you at 8", true, now - 60_000, listOf(ReactionUi("👍", 1, false, listOf("@ana:example.org")))) }
    val noop = remember {
        MessageActions(
            onLongPress = {}, onReact = { _, _ -> }, onOpenMedia = {}, onOpenUser = {}, onOpenThread = {},
            onJumpToReply = {}, onVote = { _, _ -> }, onRetry = {},
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Chat bubbles", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(BubbleStyle.entries) { style ->
                val selected = style == current
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(style); onDismiss() },
                ) {
                    Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected, onClick = { onPick(style); onDismiss() })
                        Column {
                            Text(style.label, style = MaterialTheme.typography.titleSmall)
                            Text(style.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    CompositionLocalProvider(LocalBubbleStyle provides style) {
                        Column(Modifier.padding(bottom = 10.dp)) {
                            MessageRow(theirs, showSender = true, isDm = false, actions = noop)
                            MessageRow(mine, showSender = true, isDm = false, actions = noop)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun sample(key: String, sender: String, name: String, text: String, own: Boolean, ts: Long, reactions: List<ReactionUi>) =
    TimelineEntry.Message(
        key = key, eventId = null, transactionId = null, sender = sender, senderName = name, senderAvatar = null,
        isOwn = own, timestamp = ts, content = UiContent.Text(text, null), reactions = reactions, reply = null,
        edited = false, status = SendStatus.SENT, canReply = false, isEditable = false, threadReplies = 0,
        threadLatest = null, readBy = if (own) listOf(sender) else emptyList(), shield = null,
    )
