package com.vknxworks.elementctrl.data

import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.R

/** A homeserver offered on the first-start picker. */
data class ServerOption(
    val name: String,
    val address: String,
    val description: String,
)

object Servers {
    /** Well-known public homeservers. Any of them, or your own, works; the sign-in method is discovered from the server. */
    val standard = listOf(
        ServerOption("matrix.org", "matrix.org", Strings.str(R.string.the_largest_public_server_run_by_the)),
        ServerOption(Strings.str(R.string.mozilla), "mozilla.org", Strings.str(R.string.mozilla_s_community_server_sign_in)),
    )

    /**
     * Turns what someone typed into something the SDK can look up: a bare server name (example.com) is kept so
     * .well-known discovery runs, a full URL is kept as typed, and a user ID (@me:example.com) is reduced to its server.
     */
    fun normalise(input: String): String {
        var s = input.trim()
        if (s.startsWith("@") && s.contains(":")) s = s.substringAfter(":")
        s = s.trimEnd('/')
        return s
    }
}
