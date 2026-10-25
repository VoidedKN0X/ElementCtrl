package com.vknxworks.elementctrl.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Where the phone is, read once from Android's own location service when you ask for it: no Google Play services
 * and nothing in the background. Asks every available provider at once (satellite, network, Android's own fused
 * provider) and takes the first answer.
 */
object Locator {
    fun hasPrecise(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasAny(context: Context): Boolean = hasPrecise(context) ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Location is turned off in Android's quick settings. */
    fun isOff(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return true
        return !lm.isLocationEnabled
    }

    /** A recent fix (under a minute old) if one is known, otherwise a fresh one; null after [timeoutMs]. */
    @Suppress("MissingPermission")
    suspend fun current(context: Context, timeoutMs: Long = 20_000L): Location? {
        if (!hasAny(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }

        val recent = (providers + LocationManager.PASSIVE_PROVIDER).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_AGE_NANOS }
            .minByOrNull { it.accuracy }
        if (recent != null) return recent
        if (providers.isEmpty()) return null

        val result = CompletableDeferred<Location?>()
        val signals = providers.map { CancellationSignal() }
        try {
            providers.forEachIndexed { i, provider ->
                runCatching {
                    lm.getCurrentLocation(provider, signals[i], context.mainExecutor) { loc ->
                        if (loc != null) result.complete(loc)
                    }
                }
            }
            return withTimeoutOrNull(timeoutMs) { result.await() }
        } finally {
            signals.forEach { runCatching { it.cancel() } }
        }
    }

    private const val MAX_AGE_NANOS = 60L * 1_000_000_000
}

/** A point on the map, with how sure we are of it (metres) when it came from the phone's location. */
data class GeoSpot(val lat: Double, val lon: Double, val accuracy: Float? = null)

/**
 * Reads a geo: URI (RFC 5870), such as `geo:50.85,4.35;u=20`. Only coordinates are taken from someone else's
 * event, never anything else in it.
 */
fun parseGeoUri(uri: String): GeoSpot? {
    val t = uri.trim()
    if (!t.startsWith("geo:", ignoreCase = true)) return null
    val main = t.substring(4).substringBefore(';').substringBefore('?')
    val parts = main.split(',')
    if (parts.size < 2) return null
    val lat = parts[0].trim().toDoubleOrNull() ?: return null
    val lon = parts[1].trim().toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    val u = Regex(";u=([0-9.]+)", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)?.toFloatOrNull()
    return GeoSpot(lat, lon, u)
}

/** A geo: URI for sending: six decimals (about 10 cm) and the uncertainty when it's known. */
fun GeoSpot.toGeoUri(): String {
    val base = "geo:" + "%.6f".format(java.util.Locale.ROOT, lat) + "," + "%.6f".format(java.util.Locale.ROOT, lon)
    return accuracy?.let { base + ";u=" + "%.0f".format(java.util.Locale.ROOT, it) } ?: base
}

fun GeoSpot.display(): String =
    "%.5f".format(java.util.Locale.ROOT, lat) + ", " + "%.5f".format(java.util.Locale.ROOT, lon)
