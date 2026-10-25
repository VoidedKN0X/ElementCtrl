package com.vknxworks.elementctrl.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.ElementCtrlApp
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.matrix.QrSignInStep
import com.vknxworks.elementctrl.matrix.userMessage
import com.vknxworks.elementctrl.ui.CtrlColors
import com.vknxworks.elementctrl.ui.CtrlType
import com.vknxworks.elementctrl.ui.ScreenTopBar
import com.vknxworks.elementctrl.ui.SecureScreen
import com.vknxworks.elementctrl.ui.components.CtrlButton
import com.vknxworks.elementctrl.ui.components.CtrlCard
import com.vknxworks.elementctrl.ui.components.CtrlIconTile
import com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.elementctrl.ui.qr.QrScanDialog
import kotlinx.coroutines.launch

/**
 * Sign in by scanning the QR code another signed-in device shows (Element X, Element Web or ElementCtrl:
 * "Link new device"). The server comes from the code, so no server has to be picked first. Needs a server that
 * signs in through Matrix Authentication Service with QR sign-in (MSC4108) turned on.
 */
@Composable
fun QrSignInScreen(onBack: () -> Unit) {
    val auth = ElementCtrlApp.graph.auth
    val scope = rememberCoroutineScope()
    val step by auth.qrStep.collectAsStateWithLifecycle()
    var scanning by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The check code and the code the other device shows are part of the secure sign-in: keep them off screenshots.
    SecureScreen()
    // Leaving the screen in any way (including the app being closed) stops a sign-in that's still going.
    DisposableEffect(Unit) { onDispose { auth.cancelQr() } }

    fun back() {
        if (running) auth.cancelQr()
        onBack()
    }
    BackHandler { back() }

    fun start(bytes: ByteArray) {
        scanning = false
        error = null
        running = true
        scope.launch {
            runCatching { auth.loginWithQr(bytes) }.onFailure { error = it.userMessage() }
            running = false
        }
    }

    if (scanning) QrScanDialog(stringResource(R.string.qr_scan_prompt_sign_in), onResult = ::start, onDismiss = { scanning = false })

    Scaffold(topBar = { ScreenTopBar(stringResource(R.string.qr_sign_in_title), onBack = ::back) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = step) {
                QrSignInStep.Idle -> if (!running) {
                    CtrlIconTile(Icons.Outlined.QrCodeScanner)
                    Text(stringResource(R.string.qr_sign_in_headline), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    CtrlCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.qr_sign_in_steps), style = MaterialTheme.typography.bodyMedium)
                    }
                    error?.let {
                        CtrlCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                            Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(it, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    CtrlButton(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.QrCodeScanner, null)
                        Text(stringResource(R.string.qr_scan_button), Modifier.padding(start = 8.dp))
                    }
                    Text(
                        stringResource(R.string.qr_sign_in_needs_mas),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else Waiting(stringResource(R.string.qr_connecting))
                QrSignInStep.Connecting -> Waiting(stringResource(R.string.qr_connecting))
                is QrSignInStep.ShowCheckCode -> {
                    Text(stringResource(R.string.qr_enter_this_code_other_device), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Text(s.code, style = CtrlType.Number, color = CtrlColors.AccentText)
                    Text(stringResource(R.string.qr_check_code_explain), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    CircularProgressIndicator()
                }
                is QrSignInStep.WaitingForApproval -> {
                    Text(stringResource(R.string.qr_approve_on_other_device), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    if (s.userCode.isNotBlank()) {
                        Text(stringResource(R.string.qr_user_code_matches), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Text(s.userCode, style = CtrlType.Key, color = CtrlColors.AccentText)
                    }
                    CircularProgressIndicator()
                }
                QrSignInStep.Syncing -> Waiting(stringResource(R.string.qr_getting_keys))
            }
            if (running) {
                CtrlSecondaryButton(onClick = { auth.cancelQr() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lock_cancel)) }
            }
        }
    }
}

@Composable
private fun Waiting(text: String) {
    CircularProgressIndicator(Modifier.padding(top = 32.dp))
    Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
