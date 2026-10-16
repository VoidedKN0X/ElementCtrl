package com.vknxworks.elementctrl.ui

import android.content.ContentResolver
import android.content.Context
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

/**
 * Whether a URI handed to us by another app (share sheet) is safe to read and upload. Only content: URIs from
 * other apps' providers are accepted: file: URIs and our own providers (FileProvider etc.) could otherwise be
 * used to make ElementCtrl read and send its own private files, such as the session databases.
 */
fun isForeignContentUri(context: Context, uri: Uri): Boolean {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
    val authority = uri.authority?.takeIf { it.isNotBlank() } ?: return false
    val own = context.packageName
    if (authority == own || authority.startsWith("$own.")) return false
    val provider = runCatching { context.packageManager.resolveContentProvider(authority, 0) }.getOrNull()
    if (provider != null && provider.packageName == own) return false
    // Reject paths that point back into this app's private storage through another provider (e.g. a file manager).
    val path = uri.path.orEmpty()
    val privateDirs = listOfNotNull(context.dataDir?.absolutePath, context.filesDir?.parentFile?.absolutePath, "/data/data/$own", "/data/user/0/$own")
    if (privateDirs.any { it.isNotBlank() && path.contains(it) }) return false
    return true
}
