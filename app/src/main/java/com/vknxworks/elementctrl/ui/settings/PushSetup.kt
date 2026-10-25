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
import androidx.compose.ui.res.stringResource
import com.vknxworks.elementctrl.R

/** Choose instant push through ntfy or only the periodic background check. */
@Composable
fun PushSetupDialog(session: MatrixSession, ntfyInstalled: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushOn = Push.isEnabled(context)

    com.vknxworks.elementctrl.ui.components.CtrlDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notification_delivery)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.push_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Option(
                    stringResource(R.string.instant_push_with_ntfy),
                    if (ntfyInstalled) stringResource(R.string.plus_the_background_check) else stringResource(R.string.ntfy_isn_t_installed_on_this_phone),
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
                Option(stringResource(R.string.background_check_only), stringResource(R.string.every_15_minutes_no_extra_app_needed), selected = !pushOn) {
                    scope.launch {
                        Push.disable(context, session)
                        Notifier.schedule(context, ElementCtrlApp.graph.prefs.notifications.value)
                    }
                    onDismiss()
                }
                if (!ntfyInstalled) {
                    Text(
                        stringResource(R.string.install_ntfy_then_come_back_here_and),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton(onClick = { openUrl(context, UnifiedPush.NTFY_DOWNLOAD) }) { Text(stringResource(R.string.warn_install_ntfy)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lock_close)) } },
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
                        com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton(onClick = onClick) { Text(label, color = MaterialTheme.colorScheme.onErrorContainer) }
                    }
                }
            }
        }
    }
}
