package com.vknxworks.elementctrl.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.elementctrl.ElementCtrlApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PinMode { New, Change, Disable }

/** Set, change or remove the app-lock PIN (same flow as SeerrCtrl). */
@Composable
fun PinSetupDialog(mode: PinMode, onDismiss: () -> Unit) {
    val lock = ElementCtrlApp.graph.lock
    val needsCurrent = mode != PinMode.New
    val needsNew = mode != PinMode.Disable
    var current by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun digits(v: String) = v.filter { it.isDigit() }.take(PIN_MAX)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, null) },
        title = { Text(when (mode) { PinMode.New -> "Set a PIN"; PinMode.Change -> "Change PIN"; PinMode.Disable -> "Turn off app lock" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needsCurrent) PinField("Current PIN", current) { current = digits(it) }
                if (needsNew) {
                    PinField("New PIN ($NEW_PIN_MIN–$PIN_MAX digits)", first) { first = digits(it) }
                    PinField("Repeat new PIN", second) { second = digits(it) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !working, onClick = {
                scope.launch {
                    working = true
                    val currentOk = !needsCurrent || withContext(Dispatchers.Default) { lock.checkPin(current) }
                    error = when {
                        !currentOk -> "Current PIN is wrong"
                        needsNew && first.length < NEW_PIN_MIN -> "Use at least $NEW_PIN_MIN digits"
                        needsNew && first != second -> "The PINs don't match"
                        needsNew && first.toSet().size == 1 -> "Pick a PIN that isn't one repeated digit"
                        else -> null
                    }
                    if (error == null) {
                        withContext(Dispatchers.Default) { if (needsNew) lock.setPin(first) else lock.clearPin() }
                        onDismiss()
                    }
                    working = false
                }
            }) { Text(if (mode == PinMode.Disable) "Turn off" else "Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
