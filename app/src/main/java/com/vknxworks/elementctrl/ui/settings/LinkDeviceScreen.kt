package com.vknxworks.elementctrl.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.LinkDeviceService
import com.vknxworks.elementctrl.matrix.LinkStep
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.CtrlColors
import com.vknxworks.elementctrl.ui.CtrlType
import com.vknxworks.elementctrl.ui.ScreenTopBar
import com.vknxworks.elementctrl.ui.SecureScreen
import com.vknxworks.elementctrl.ui.components.CtrlButton
import com.vknxworks.elementctrl.ui.components.CtrlCard
import com.vknxworks.elementctrl.ui.components.CtrlIconTile
import com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.elementctrl.ui.components.CtrlTextField
import com.vknxworks.elementctrl.ui.openUrl
import com.vknxworks.elementctrl.ui.qr.QrImage
import com.vknxworks.elementctrl.ui.qr.QrScanDialog
import kotlinx.coroutines.launch

/**
 * Link new device: sign a new phone or computer in to this account with a QR code (MSC4108). Either this phone
 * shows a code for the new device to scan, or it scans the code the new device shows. Needs Matrix Authentication
 * Service on the homeserver with QR sign-in turned on.
 */
@Composable
fun LinkDeviceScreen(session: MatrixSession, onBack: () -> Unit) {
    val service = remember(session) { LinkDeviceService(session) }
    val step by service.step.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var supported by remember { mutableStateOf<Boolean?>(null) }
    var running by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The QR code is a key to your account for a few minutes: never in screenshots, recordings or Recents.
    SecureScreen()
    LaunchedEffect(Unit) { supported = service.supported() }
    DisposableEffect(service) { onDispose { service.cancel() } }

    fun back() {
        service.cancel()
        onBack()
    }
    BackHandler { back() }

    fun launchStep(block: suspend () -> Unit) {
        error = null
        running = true
        scope.launch {
            runCatching { block() }.onFailure { error = it.userMessage() }
            running = false
        }
    }

    if (scanning) {
        QrScanDialog(
            stringResource(R.string.qr_scan_prompt_link),
            onResult = { bytes -> scanning = false; launchStep { service.scanCode(bytes) } },
            onDismiss = { scanning = false },
        )
    }

    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.link_device_title), onBack = ::back) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = step) {
                LinkStep.Idle -> when (supported) {
                    null -> CircularProgressIndicator(Modifier.padding(top = 32.dp))
                    false -> {
                        CtrlIconTile(Icons.Outlined.Warning)
                        Text(stringResource(R.string.link_device_unsupported_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.qr_server_unsupported), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    true -> {
                        CtrlIconTile(Icons.Outlined.QrCode2)
                        Text(stringResource(R.string.link_device_headline), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(stringResource(R.string.link_device_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        error?.let {
                            CtrlCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                                Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        CtrlButton(onClick = { launchStep { service.showCode() } }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.QrCode2, null)
                            Text(stringResource(R.string.link_device_show_qr), Modifier.padding(start = 8.dp))
                        }
                        CtrlSecondaryButton(onClick = { scanning = true }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.QrCodeScanner, null)
                            Text(stringResource(R.string.link_device_scan_qr), Modifier.padding(start = 8.dp))
                        }
                    }
                }
                LinkStep.Starting -> Waiting(stringResource(R.string.qr_connecting))
                is LinkStep.ShowQr -> {
                    Text(stringResource(R.string.link_device_scan_this), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    QrImage(s.bytes, stringResource(R.string.link_device_qr_description))
                    Text(stringResource(R.string.link_device_scan_this_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                is LinkStep.EnterCheckCode -> {
                    var code by remember(s) { mutableStateOf("") }
                    Text(stringResource(R.string.link_device_enter_code), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.qr_check_code_explain), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    CtrlTextField(
                        code,
                        { v -> code = v.filter { it.isDigit() }.take(2) },
                        label = { Text(stringResource(R.string.link_device_code_label)) },
                        singleLine = true,
                        textStyle = CtrlType.Key,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CtrlButton(
                        onClick = { val n = code.toInt(); scope.launch { runCatching { service.sendCheckCode(s.sender, n) }.onFailure { error = it.userMessage() } } },
                        enabled = code.length == 2,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.continue_text)) }
                }
                is LinkStep.ShowCheckCode -> {
                    Text(stringResource(R.string.qr_enter_this_code_other_device), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(s.code, style = CtrlType.Number, color = CtrlColors.AccentText)
                    Text(stringResource(R.string.qr_check_code_explain), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    CircularProgressIndicator()
                }
                is LinkStep.Approve -> {
                    Text(stringResource(R.string.link_device_approve_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.link_device_approve_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    CtrlButton(
                        // The account page comes from the server: only ever opened as an https page in the browser.
                        onClick = { if (s.url.startsWith("https://", ignoreCase = true)) openUrl(context, s.url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.link_device_open_account_page)) }
                    CtrlSecondaryButton(onClick = { scope.launch { runCatching { service.approved(s.continuation) }.onFailure { error = it.userMessage() } } }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.link_device_approved))
                    }
                }
                LinkStep.Syncing -> Waiting(stringResource(R.string.link_device_sending_keys))
                LinkStep.Done -> {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.padding(top = 24.dp), tint = CtrlColors.AccentText)
                    Text(stringResource(R.string.link_device_done), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.link_device_done_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    CtrlButton(onClick = { service.reset(); onBack() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lock_done)) }
                }
            }
            if (running && step != LinkStep.Done && step != LinkStep.Idle) {
                CtrlSecondaryButton(onClick = { service.cancel() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lock_cancel)) }
            }
        }
    }
}

@Composable
private fun Waiting(text: String) {
    CircularProgressIndicator(Modifier.padding(top = 32.dp))
    Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
