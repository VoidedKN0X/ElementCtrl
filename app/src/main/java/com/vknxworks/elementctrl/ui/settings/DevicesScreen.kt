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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.ui.ScreenTopBar

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
                .onFailure { snackbar.showSnackbar(it.userMessage().ifBlank { Strings.str(R.string.couldn_t_load_devices) }) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun signOut(ids: List<String>, password: String? = null, authSession: String? = null) {
        scope.launch {
            runCatching { service.delete(ids, password, authSession) }
                .onSuccess { r ->
                    when (r) {
                        DeleteResult.Done -> { snackbar.showSnackbar(if (ids.size == 1) Strings.str(R.string.signed_out) else Strings.plural(R.plurals.signed_out_devices, ids.size, ids.size)); reload() }
                        is DeleteResult.NeedsPassword -> passwordFor = ids to r.session
                        is DeleteResult.OpenWebPage -> openUrl(context, r.url)
                    }
                }
                .onFailure { snackbar.showSnackbar(it.userMessage().ifBlank { Strings.str(R.string.couldn_t_sign_out) }) }
        }
    }

    val others = devices.filter { !it.isCurrent }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.account_devices),
                onBack = onBack,
                actions = { IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) } },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn {
                devices.firstOrNull { it.isCurrent }?.let { me ->
                    item { SectionHeader(stringResource(R.string.this_device)) }
                    item { DeviceRow(me) { selected = me } }
                }
                if (others.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.other_devices, others.size)) }
                    items(others, key = { it.deviceId }) { d -> DeviceRow(d) { selected = d } }
                    item {
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        SettingRow(
                            Icons.AutoMirrored.Filled.Logout, stringResource(R.string.sign_out_all_other_devices),
                            tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error,
                        ) { confirmOut = others.map { it.deviceId } }
                    }
                }
                item {
                    SettingRow(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.manage_on_your_account_page), stringResource(R.string.for_accounts_using_an_oauth_sign_in)) {
                        scope.launch { service.webPage(null)?.let { openUrl(context, it.url) } ?: snackbar.showSnackbar(Strings.str(R.string.your_server_has_no_account_page)) }
                    }
                }
            }
        }
    }

    selected?.let { d ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Text(d.displayName ?: d.deviceId, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.session_id, d.deviceId), style = MaterialTheme.typography.bodyMedium)
                d.lastSeenTs?.let { Text(stringResource(R.string.last_active, shortTime(it)), style = MaterialTheme.typography.bodyMedium) }
                d.lastSeenIp?.let { Text(stringResource(R.string.ip_address, it), style = MaterialTheme.typography.bodyMedium) }
            }
            SettingRow(Icons.Default.Edit, stringResource(R.string.rename)) { renaming = d; selected = null }
            if (!d.isCurrent) {
                SettingRow(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.sign_out), tint = MaterialTheme.colorScheme.error, titleColor = MaterialTheme.colorScheme.error) {
                    confirmOut = listOf(d.deviceId); selected = null
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        }
    }

    renaming?.let { d ->
        TextInputDialog(stringResource(R.string.rename_device), initial = d.displayName.orEmpty(), onDismiss = { renaming = null }) { name ->
            scope.launch {
                runCatching { service.rename(d.deviceId, name.trim()) }.onSuccess { reload() }.onFailure { snackbar.showSnackbar(it.userMessage().ifBlank { Strings.str(R.string.couldn_t_rename) }) }
            }
        }
    }

    confirmOut?.let { ids ->
        ConfirmDialog(
            if (ids.size == 1) stringResource(R.string.sign_out_this_device) else pluralStringResource(R.plurals.sign_out_devices, ids.size, ids.size),
            stringResource(R.string.they_ll_need_to_sign_in_again_make),
            stringResource(R.string.sign_out),
            destructive = true,
            onDismiss = { confirmOut = null },
            onConfirm = { signOut(ids) },
        )
    }

    passwordFor?.let { (ids, authSession) ->
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { passwordFor = null },
            title = { Text(stringResource(R.string.confirm_with_your_password)) },
            text = {
                OutlinedTextField(
                    password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    // Password type: keyboards don't suggest, learn or remember what's typed here.
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                )
            },
            confirmButton = { TextButton(enabled = password.isNotEmpty(), onClick = { passwordFor = null; signOut(ids, password, authSession) }) { Text(stringResource(R.string.sign_out)) } },
            dismissButton = { TextButton(onClick = { passwordFor = null }) { Text(stringResource(R.string.lock_cancel)) } },
        )
    }
}

@Composable
private fun DeviceRow(d: DeviceInfo, onClick: () -> Unit) {
    SettingRow(
        Icons.Default.PhoneAndroid,
        d.displayName ?: d.deviceId,
        listOfNotNull(d.deviceId, d.lastSeenTs?.let { Strings.str(R.string.active, shortTime(it)) }).joinToString(" · "),
        tint = if (d.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
    )
}
