package com.vknxworks.elementctrl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.elementctrl.matrix.AppState
import com.vknxworks.elementctrl.notify.Notifier
import com.vknxworks.elementctrl.ui.CenteredLoading
import com.vknxworks.elementctrl.ui.DeepLinks
import com.vknxworks.elementctrl.ui.ElementCtrlTheme
import com.vknxworks.elementctrl.ui.MainNav
import com.vknxworks.elementctrl.ui.SharedContent
import com.vknxworks.elementctrl.ui.onboarding.OnboardingFlow
import org.matrix.rustcomponents.sdk.MatrixId
import org.matrix.rustcomponents.sdk.parseMatrixEntityFrom

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            ElementCtrlTheme {
                Surface(Modifier.fillMaxSize()) {
                    val state by ElementCtrlApp.graph.state.collectAsStateWithLifecycle()
                    when (val s = state) {
                        AppState.Loading -> CenteredLoading()
                        is AppState.SignedOut -> OnboardingFlow(s.message)
                        is AppState.SignedIn -> MainNav(s.session)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifier.EXTRA_ROOM_ID)?.let {
            DeepLinks.openRoom.value = it
            intent.removeExtra(Notifier.EXTRA_ROOM_ID)
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::handleUri)
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (text != null || stream != null) DeepLinks.share.value = SharedContent(text, stream)
            }
        }
    }

    private fun handleUri(uri: Uri) {
        val scheme = getString(R.string.login_redirect_scheme)
        if (uri.scheme == scheme) {
            DeepLinks.authRedirect.tryEmit(uri.toString())
            return
        }
        val entity = runCatching { parseMatrixEntityFrom(uri.toString()) }.getOrNull() ?: return
        when (val id = entity.id) {
            is MatrixId.User -> DeepLinks.openUser.value = id.id
            is MatrixId.Room -> DeepLinks.joinTarget.value = id.id to entity.via
            is MatrixId.RoomAlias -> DeepLinks.joinTarget.value = id.alias to entity.via
            is MatrixId.EventOnRoomId -> DeepLinks.joinTarget.value = id.roomId to entity.via
            is MatrixId.EventOnRoomAlias -> DeepLinks.joinTarget.value = id.alias to entity.via
        }
    }
}
