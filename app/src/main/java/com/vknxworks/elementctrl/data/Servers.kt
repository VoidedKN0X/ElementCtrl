package com.vknxworks.elementctrl.data

/** A homeserver offered on the first-start picker. */
data class ServerOption(
    val name: String,
    val address: String,
    val description: String,
)

object Servers {
    /** Well-known public homeservers. Any of them, or your own, works; the sign-in method is discovered from the server. */
    val standard = listOf(
        ServerOption("matrix.org", "matrix.org", "The largest public server, run by the Matrix.org Foundation. Free and open to everyone."),
        ServerOption("Mozilla", "mozilla.org", "Mozilla's community server. Sign in with a Mozilla account or GitHub."),
        ServerOption("envs.net", "envs.net", "A non-profit community server with free registration."),
        ServerOption("tchncs.de", "tchncs.de", "A long-running community server hosted in Germany."),
        ServerOption("KDE", "kde.org", "The KDE community's server for contributors and users."),
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
