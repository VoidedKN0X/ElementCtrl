package com.vknxworks.elementctrl.ui

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Content another app shared into ElementCtrl, waiting for the user to pick a room. */
data class SharedContent(val text: String?, val uri: Uri?)

/** Things that arrive from outside the UI (intents, notifications) for the screens to act on. */
object DeepLinks {
    /** OAuth/SSO redirect back from the browser. */
    val authRedirect = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)
    /** A room to open, from a notification or a matrix.to link. */
    val openRoom = MutableStateFlow<String?>(null)
    /** A room ID or alias from a link that may need joining first. */
    val joinTarget = MutableStateFlow<Pair<String, List<String>>?>(null)
    /** A user from a link, to open their profile. */
    val openUser = MutableStateFlow<String?>(null)
    val share = MutableStateFlow<SharedContent?>(null)
}
