package com.vknxworks.elementctrl.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.elementctrl.matrix.DeleteResult
import com.vknxworks.elementctrl.matrix.DeviceInfo
import com.vknxworks.elementctrl.matrix.DeviceService
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.ConfirmDialog
import com.vknxworks.elementctrl.ui.SectionHeader
import com.vknxworks.elementctrl.ui.SettingRow
import com.vknxworks.elementctrl.ui.TextInputDialog
import com.vknxworks.elementctrl.ui.openUrl
import com.vknxworks.elementctrl.ui.shortTime
import kotlinx.coroutines.launch

/** Every session signed in to your account: rename them or sign them out. */
@Composable
fun DevicesScreen(session: MatrixSession, onBack: () -> Unit) {
    val service = remember { DeviceService(session) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<DeviceInfo?>(null) }
    var renaming by remember { mutableStateOf<DeviceInfo?>(null) }
    var confirmOut by remember { mutableStateOf<List<String>?>(null) }
    var passwordFor by remember { mutableStateOf<Pair<List<String>, String>?>(null) }

    fun reload() {
        loading = true
        scope.launch {
            runCatching { service.list() }
                .onSuccess { devices = it }
                .onFailure { snackbar.showSnackbar(it.userMessage().ifBlank { "Couldn't load devices" }) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun signOut(ids: List<String>, password: String? = null, authSession: String? = null) {
        scope.launch {
            runCatching { service.delete(ids, password, authSession) }
                .onSuccess { r ->
                    when (r) {
                        DeleteResult.Done -> { snackbar.showSnackbar(if (ids.size == 1) "Signed out" else "Signed out ${ids.size} devices"); reload() }
                        is DeleteResult.NeedsPassword -> passwordFor = ids to r.session
                        is DeleteResult.OpenWebPage -> openUrl(context, r.url)
                    }
                }
                .onFailure { snackbar.showSnackbar(it.userMessage().ifBlank { "Couldn't sign out" }) }
        }
    }

    val others = devices.filter { !it.isCurrent }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, "Refresh") } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn {
                devices.firstOrNull { it.isCurrent }?.let { me ->
                    item { SectionHeader("This device") }
                    item { DeviceRow(me) { selected = me } }
                }
                if (others.isNotEmpty()) {
                    item { SectionHeader("Other devices · ${others.size}") }
                    items(others, key = { it.deviceId }) { d -> DeviceRow(d) { selected = d } }
                    item {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        SettingRow(
                            Icons.AutoMirrored.Filled.Logout, "Sign out all other devices",
                            tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
                        ) { confirmOut = others.map { it.deviceId } }
                    }
                }
                item {
                    SettingRow(Icons.AutoMirrored.Filled.OpenInNew, "Manage on your account page", "For accounts using an OAuth sign-in server") {
                        scope.launch { service.webPage(null)?.let { openUrl(context, it.url) } ?: snackbar.showSnackbar("Your server has no account page") }
                    }
                }
            }
        }
    }

    selected?.let { d ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Text(d.displayName ?: d.deviceId, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Session ID: ${d.deviceId}", style = MaterialTheme.typography.bodyMedium)
                d.lastSeenTs?.let { Text("Last active: ${shortTime(it)}", style = MaterialTheme.typography.bodyMedium) }
                d.lastSeenIp?.let { Text("IP address: $it", style = MaterialTheme.typography.bodyMedium) }
            }
            SettingRow(Icons.Default.Edit, "Rename") { renaming = d; selected = null }
            if (!d.isCurrent) {
                SettingRow(Icons.AutoMirrored.Filled.Logout, "Sign out", tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
                    confirmOut = listOf(d.deviceId); selected = null
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        }
    }

    renaming?.let { d ->
        TextInputDialog("Rename device", initial = d.displayName.orEmpty(), onDismiss = { renaming = null }) { name ->
            scope.launch {
                runCatching { service.rename(d.deviceId, name.trim()) }.onSuccess { reload() }.onFailure { snackbar.showSnackbar(it.userMessage().ifBlank { "Couldn't rename" }) }
            }
        }
    }

    confirmOut?.let { ids ->
        ConfirmDialog(
            if (ids.size == 1) "Sign out this device?" else "Sign out ${ids.size} devices?",
            "They'll need to sign in again. Make sure recovery is set up so you don't lose access to encrypted messages.",
            "Sign out",
            destructive = true,
            onDismiss = { confirmOut = null },
            onConfirm = { signOut(ids) },
        )
    }

    passwordFor?.let { (ids, authSession) ->
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passwordFor = null },
            title = { Text("Confirm with your password") },
            text = { OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation()) },
            confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { passwordFor = null; signOut(ids, password, authSession) }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { passwordFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DeviceRow(d: DeviceInfo, onClick: () -> Unit) {
    SettingRow(
        Icons.Default.PhoneAndroid,
        d.displayName ?: d.deviceId,
        listOfNotNull(d.deviceId, d.lastSeenTs?.let { "active ${shortTime(it)}" }).joinToString(" · "),
        tint = if (d.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
    )
}
