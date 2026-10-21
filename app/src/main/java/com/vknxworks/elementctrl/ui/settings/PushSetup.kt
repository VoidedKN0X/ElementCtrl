package com.vknxworks.elementctrl.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.notify.Notifier
import com.vknxworks.elementctrl.notify.Push
import com.vknxworks.elementctrl.notify.UnifiedPush
import com.vknxworks.elementctrl.ui.openUrl
import kotlinx.coroutines.launch

/** Choose instant push through ntfy or only the periodic background check. */
@Composable
fun PushSetupDialog(session: MatrixSession, ntfyInstalled: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushOn = Push.isEnabled(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification delivery") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Instant push comes through ntfy, which keeps one connection to your ntfy server (ntfy.sh or your own) for all " +
                        "your apps. Your homeserver sends only the message ID, through that ntfy server's own Matrix gateway; " +
                        "ElementCtrl then fetches and decrypts the message itself. The background check every ~15 minutes keeps " +
                        "running either way, so nothing is missed if a push doesn't arrive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Option(
                    "Instant push with ntfy",
                    if (ntfyInstalled) "Plus the background check" else "ntfy isn't installed on this phone",
                    selected = pushOn,
                    enabled = ntfyInstalled,
                ) {
                    if (Push.enable(context)) {
                        // Like Element X: push only works reliably if the app may use the network while the phone sleeps.
                        if (!com.vknxworks.elementctrl.notify.Battery.isUnrestricted(context)) {
                            com.vknxworks.elementctrl.notify.Battery.requestUnrestricted(context)
                        }
                    }
                    Notifier.schedule(context, ElementCtrlApp.graph.prefs.notifications.value)
                    onDismiss()
                }
                Option("Background check only", "Every ~15 minutes, no extra app needed", selected = !pushOn) {
                    scope.launch {
                        Push.disable(context, session)
                        Notifier.schedule(context, ElementCtrlApp.graph.prefs.notifications.value)
                    }
                    onDismiss()
                }
                if (!ntfyInstalled) {
                    Text(
                        "Install ntfy, then come back here and turn on instant push. It works with ntfy.sh or your own ntfy server.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    OutlinedButton(onClick = { openUrl(context, UnifiedPush.NTFY_DOWNLOAD) }) { Text("Install ntfy") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Option(title: String, subtitle: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A clearly visible problem in the Notifications settings, with the buttons that fix it. */
@Composable
fun PushWarning(icon: ImageVector, title: String, body: String, actions: List<Pair<String, () -> Unit>>) {
    Surface(
        // Shown inside the Notifications group's card.
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            if (actions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { (label, onClick) ->
                        OutlinedButton(onClick = onClick) { Text(label, color = MaterialTheme.colorScheme.onErrorContainer) }
                    }
                }
            }
        }
    }
}
