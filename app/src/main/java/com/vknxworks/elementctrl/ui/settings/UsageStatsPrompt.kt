package com.vknxworks.elementctrl.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.ElementCtrlApp

/** What's shared, in the words used by the first-run question and the Settings footer. */
const val USAGE_STATS_DESCRIPTION =
    "Which screens you open, whether you send text, voice messages, pictures or files, whether you use search, " +
        "the app version, Android version, phone model and language. Never your homeserver, your Matrix ID, " +
        "who you talk to, which rooms you're in or what you write or search for. " +
        "Sent to VKNXWorks' own server, not to any analytics company."

/** Asked once, after signing in. Nothing is sent until the user says yes; they can change it in Settings. */
@Composable
fun UsageStatsPrompt() {
    val graph = ElementCtrlApp.graph
    if (!graph.telemetry.available) return
    val asked by graph.prefs.usageStatsAsked.collectAsStateWithLifecycle()
    if (asked) return
    fun answer(share: Boolean) = graph.prefs.answerUsageStats(share)
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Help improve ElementCtrl?") },
        text = {
            Text(
                "Share anonymous usage stats so I can see which features people use and which Android " +
                    "versions to support.\n\n$USAGE_STATS_DESCRIPTION\n\nYou can change this any time in Settings.",
            )
        },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text("Share stats") } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text("No thanks") } },
    )
}
