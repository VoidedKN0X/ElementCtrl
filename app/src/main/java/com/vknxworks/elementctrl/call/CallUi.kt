package com.vknxworks.elementctrl.call

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.ui.openUrl
import kotlinx.coroutines.launch

/**
 * Starts or joins a call in a room, after checking that your homeserver announces a MatrixRTC (LiveKit) service.
 * Without one, explains that calls aren't set up on the server instead of opening the call screen.
 */
@Composable
fun rememberCallStarter(session: MatrixSession): (roomId: String, audioOnly: Boolean) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var notSetUp by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val server = remember(session) { session.userId.substringAfter(':', session.homeserver) }
    val busy = stringResource(R.string.call_already_in_call)

    if (notSetUp) {
        AlertDialog(
            onDismissRequest = { notSetUp = false },
            title = { Text(stringResource(R.string.call_not_set_up_title)) },
            text = { Text(stringResource(R.string.call_not_set_up_body, server)) },
            confirmButton = { TextButton(onClick = { notSetUp = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                TextButton(onClick = { notSetUp = false; openUrl(context, CALLS_HELP_URL) }) { Text(stringResource(R.string.call_not_set_up_link)) }
            },
        )
    }
    failed?.let { msg ->
        AlertDialog(
            onDismissRequest = { failed = null },
            title = { Text(stringResource(R.string.call_error_title)) },
            text = { Text(stringResource(R.string.call_check_failed, msg)) },
            confirmButton = { TextButton(onClick = { failed = null }) { Text(stringResource(R.string.ok)) } },
        )
    }

    return { roomId, audioOnly ->
        val active = CallManager.active.value
        when {
            active != null && active.roomId == roomId -> CallManager.reopen(context)
            active != null -> Toast.makeText(context, busy, Toast.LENGTH_LONG).show()
            checking -> Unit
            else -> {
                checking = true
                scope.launch {
                    when (val r = CallSupport.check(session)) {
                        is CallSupport.Result.Ready -> CallManager.open(context, roomId, audioOnly)
                        CallSupport.Result.NotSetUp -> notSetUp = true
                        is CallSupport.Result.Failed -> failed = r.message
                    }
                    checking = false
                }
            }
        }
    }
}

/** Voice (direct chats) and video call buttons for a room's top bar. */
@Composable
fun CallButtons(isDm: Boolean, onCall: (audioOnly: Boolean) -> Unit) {
    if (isDm) {
        IconButton(onClick = { onCall(true) }) { Icon(Icons.Filled.Call, stringResource(R.string.call_voice)) }
    }
    IconButton(onClick = { onCall(false) }) { Icon(Icons.Filled.Videocam, stringResource(R.string.call_video)) }
}

/** "Call in progress" with a Join button, shown at the top of a chat while a call is going on in it. */
@Composable
fun CallBanner(roomId: String, members: Int, isDm: Boolean, modifier: Modifier = Modifier, onJoin: (audioOnly: Boolean) -> Unit) {
    val active by CallManager.active.collectAsStateWithLifecycle()
    val here = active?.roomId == roomId
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Videocam, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.call_ongoing_banner), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (members > 0) {
                    Text(stringResource(R.string.call_ongoing_people, members), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!here && isDm) {
                    IconButton(onClick = { onJoin(true) }) {
                        Icon(Icons.Filled.Call, stringResource(R.string.call_join_voice), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Button(onClick = { onJoin(false) }) {
                    Text(stringResource(if (here) R.string.call_back_to_call else R.string.call_join))
                }
            }
        }
    }
}
