package com.vknxworks.elementctrl.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.notify.Notifier
import com.vknxworks.elementctrl.notify.Push
import com.vknxworks.elementctrl.ui.openUrl
import kotlinx.coroutines.launch

/** Choose instant push through a UnifiedPush app (ntfy) or the periodic background check. */
@Composable
fun PushSetupDialog(session: MatrixSession, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val distributors = remember { Push.distributors(context) }
    val current = if (Push.isEnabled(context)) Push.selectedDistributor(context) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification delivery") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Instant push uses a UnifiedPush app such as ntfy, which keeps one connection open for all your apps. " +
                        "Your homeserver sends only the message ID to it; ElementCtrl fetches and decrypts the message itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                distributors.forEach { d ->
                    Option(d.label, "Instant push", selected = current == d.packageName) {
                        Push.enable(context, d)
                        onDismiss()
                    }
                }
                Option("Background check", "Every ~15 minutes, no extra app needed", selected = current == null) {
                    scope.launch {
                        Push.disable(context, session)
                        Notifier.schedule(context, ElementCtrlApp.graph.prefs.notifications.value)
                    }
                    onDismiss()
                }
                if (distributors.isEmpty()) {
                    Text("No UnifiedPush app found on this phone.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openUrl(context, "https://play.google.com/store/apps/details?id=io.heckel.ntfy") }) { Text("ntfy on Play") }
                        OutlinedButton(onClick = { openUrl(context, "https://f-droid.org/packages/io.heckel.ntfy/") }) { Text("F-Droid") }
                    }
                    Text(
                        "After installing ntfy, come back here and pick it. It works with ntfy.sh or your own ntfy server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Option(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
