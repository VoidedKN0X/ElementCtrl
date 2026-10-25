package com.vknxworks.elementctrl.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.elementctrl.R

/**
 * The call's audio output picker, opened from the audio output button in the call's bar: earpiece, speaker,
 * Bluetooth earphones, wired or USB headsets, with the one in use marked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioOutputSheet(options: List<AudioOutput>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = cs.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(R.string.call_audio_output),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            if (options.isEmpty()) {
                Text(
                    stringResource(R.string.call_audio_output_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            options.forEach { o ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (o.selected) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onPick(o.id) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .background(if (o.selected) cs.primary else cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon(o.kind), null, Modifier.size(22.dp), tint = if (o.selected) cs.onPrimary else cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        o.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (o.selected) cs.onSecondaryContainer else cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (o.selected) Icon(Icons.Outlined.Check, stringResource(R.string.call_audio_output_in_use), tint = cs.onSecondaryContainer)
                }
            }
        }
    }
}

private fun icon(kind: AudioOutput.Kind): ImageVector = when (kind) {
    AudioOutput.Kind.EARPIECE -> Icons.Outlined.PhoneInTalk
    AudioOutput.Kind.SPEAKER -> Icons.AutoMirrored.Outlined.VolumeUp
    AudioOutput.Kind.BLUETOOTH -> Icons.Outlined.Bluetooth
    AudioOutput.Kind.WIRED -> Icons.Outlined.Headphones
    AudioOutput.Kind.USB -> Icons.Outlined.Usb
    AudioOutput.Kind.OTHER -> Icons.AutoMirrored.Outlined.VolumeUp
}
