package com.vknxworks.elementctrl.ui.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vknxworks.elementctrl.BuildConfig
import com.vknxworks.elementctrl.R
import com.vknxworks.elementctrl.Strings
import com.vknxworks.elementctrl.ui.CtrlColors
import com.vknxworks.elementctrl.ui.CtrlShapes
import com.vknxworks.elementctrl.ui.CtrlType
import com.vknxworks.elementctrl.ui.LocalCtrlColors
import com.vknxworks.elementctrl.ui.ScreenTopBar
import com.vknxworks.elementctrl.ui.components.CtrlButton
import com.vknxworks.elementctrl.ui.components.CtrlDialog
import com.vknxworks.elementctrl.ui.components.CtrlSecondaryButton
import com.vknxworks.elementctrl.ui.components.CtrlSquareButton
import com.vknxworks.elementctrl.ui.components.CtrlTextField
import com.vknxworks.elementctrl.ui.copyText
import java.io.File
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay

/** What to send: a point, how it was picked, an optional description and the map's zoom. */
data class LocationToSend(val spot: GeoSpot, val mine: Boolean, val description: String?, val zoom: Int)

/** Where the map opens when nothing else is known: Brussels, at country level. */
private val FALLBACK = GeoPoint(50.85, 4.35)

/** Map tiles are cached in the app's own cache folder; requests say which app they're from, as OpenStreetMap asks. */
private fun configureOsm(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = "ElementCtrl/${BuildConfig.VERSION_NAME} (${context.packageName})"
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
}

/** An OpenStreetMap map (tiles from tile.openstreetmap.org), dimmed for dark mode, following the screen's lifecycle. */
@Composable
private fun rememberMap(center: GeoPoint, zoom: Double): MapView {
    val context = LocalContext.current
    val dark = LocalCtrlColors.current.isDark
    val map = remember {
        configureOsm(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            maxZoomLevel = 19.5
            controller.setZoom(zoom)
            controller.setCenter(center)
        }
    }
    // Light tiles turned dark in dark mode, so the map doesn't glare at night.
    LaunchedEffect(dark) {
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        map.invalidate()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }
    return map
}

@Composable
private fun OsmCredit(modifier: Modifier) {
    // OpenStreetMap's licence asks for this credit on the map.
    Text(
        stringResource(R.string.osm_credit),
        style = CtrlType.Meta,
        color = CtrlColors.Muted,
        modifier = modifier.clip(CtrlShapes.Badge).background(CtrlColors.Surface.copy(alpha = 0.9f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Full-screen location picker for a chat: an OpenStreetMap map with a pin in the middle. Move the map (or tap a
 * spot) to put the pin somewhere, or use "Send my current location", read once from Android's own location
 * service. Before sending, a preview shows the spot and takes an optional description.
 */
@Composable
fun LocationPickerDialog(onDismiss: () -> Unit, onSend: (LocationToSend) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LocationPicker(onDismiss, onSend)
    }
}

@Composable
private fun LocationPicker(onDismiss: () -> Unit, onSend: (LocationToSend) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val accent = CtrlColors.Accent.toArgb()
    val map = rememberMap(FALLBACK, 6.0)
    var locating by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf<GeoSpot?>(null) }
    var preview by remember { mutableStateOf<LocationToSend?>(null) }
    var precise by remember { mutableStateOf(Locator.hasPrecise(context)) }
    var askedOnce by remember { mutableStateOf(false) }

    fun showMine(spot: GeoSpot) {
        mine = spot
        map.overlays.removeAll { it is Marker }
        map.overlays.add(
            Marker(map).apply {
                position = GeoPoint(spot.lat, spot.lon)
                icon = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accent)
                    setStroke(5, android.graphics.Color.WHITE)
                    setSize(44, 44)
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
            },
        )
        map.invalidate()
    }

    fun locate(thenSend: Boolean) {
        if (Locator.isOff(context)) {
            scope.launch { snackbar.showSnackbar(Strings.str(R.string.location_off)) }
            return
        }
        locating = true
        scope.launch {
            val loc = Locator.current(context)
            locating = false
            if (loc == null) {
                snackbar.showSnackbar(Strings.str(R.string.location_not_found))
                return@launch
            }
            val spot = GeoSpot(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy else null)
            showMine(spot)
            map.controller.animateTo(GeoPoint(spot.lat, spot.lon), maxOf(map.zoomLevelDouble, 17.0), 600L)
            if (thenSend) preview = LocationToSend(spot, mine = true, description = null, zoom = 17)
        }
    }

    var pendingSend by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        askedOnce = true
        precise = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || Locator.hasPrecise(context)
        if (Locator.hasAny(context)) {
            if (!precise) scope.launch { snackbar.showSnackbar(Strings.str(R.string.location_approximate_only)) }
            locate(pendingSend)
        } else {
            scope.launch { snackbar.showSnackbar(Strings.str(R.string.location_permission_denied)) }
        }
        pendingSend = false
    }

    fun myLocation(thenSend: Boolean) {
        // Precise location is asked for (approximate is offered by Android too and still works, less exactly).
        if (Locator.hasPrecise(context) || (Locator.hasAny(context) && askedOnce)) {
            locate(thenSend)
        } else {
            pendingSend = thenSend
            com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn()
            askPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // Tapping a spot moves the pin there.
    DisposableEffect(map) {
        val events = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { map.controller.animateTo(it) }
                return p != null
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        map.overlays.add(0, events)
        onDispose { map.overlays.remove(events) }
    }
    // Open where you are if location is already allowed; otherwise wait until you ask.
    LaunchedEffect(Unit) { if (Locator.hasAny(context) && !Locator.isOff(context)) locate(thenSend = false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { ScreenTopBar(stringResource(R.string.location_send_title), onBack = onDismiss) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
                // The pin: its point is the middle of the map.
                Icon(
                    Icons.Outlined.Place, null,
                    Modifier.align(Alignment.Center).offset(y = (-18).dp).size(40.dp),
                    tint = CtrlColors.AccentText,
                )
                CtrlSquareButton(
                    onClick = { myLocation(thenSend = false) },
                    contentDescription = stringResource(R.string.location_my_location),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    size = 52.dp,
                ) {
                    if (locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.MyLocation, null, tint = CtrlColors.AccentText)
                }
                OsmCredit(Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            Column(
                Modifier.fillMaxWidth().background(CtrlColors.Surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CtrlButton(onClick = { myLocation(thenSend = true) }, enabled = !locating, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.MyLocation, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.location_send_mine))
                }
                CtrlSecondaryButton(
                    onClick = {
                        val c = map.mapCenter
                        preview = LocationToSend(GeoSpot(c.latitude, c.longitude), mine = false, description = null, zoom = map.zoomLevelDouble.toInt().coerceIn(3, 19))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Place, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.location_send_pin))
                }
                Text(stringResource(R.string.location_picker_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    preview?.let { p ->
        var description by remember(p) { mutableStateOf("") }
        CtrlDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(if (p.mine) R.string.location_preview_mine else R.string.location_preview_pin)) },
            text = {
                Column(Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LocationMiniMap(p.spot, Modifier.fillMaxWidth().height(180.dp))
                    Text(p.spot.display(), style = CtrlType.Meta, color = CtrlColors.Text)
                    p.spot.accuracy?.let { Text(stringResource(R.string.location_accuracy, it.toInt()), style = MaterialTheme.typography.bodySmall) }
                    CtrlTextField(
                        description, { description = it.take(200) },
                        label = { Text(stringResource(R.string.location_description_optional)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preview = null
                    onSend(p.copy(description = description.trim().ifBlank { null }))
                }) { Text(stringResource(R.string.send)) }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text(stringResource(R.string.lock_cancel)) } },
        )
    }
}

/** A small, still map with a pin, for previews. */
@Composable
private fun LocationMiniMap(spot: GeoSpot, modifier: Modifier) {
    val point = remember(spot) { GeoPoint(spot.lat, spot.lon) }
    val map = rememberMap(point, 16.0)
    val accent = CtrlColors.Accent.toArgb()
    LaunchedEffect(map, point) {
        map.setMultiTouchControls(false)
        map.setOnTouchListener { _, _ -> true }
        map.overlays.removeAll { it is Marker }
        map.overlays.add(pinMarker(map, point, accent))
        map.controller.setCenter(point)
        map.invalidate()
    }
    Box(modifier.clip(CtrlShapes.Control).border(1.dp, CtrlColors.Border, CtrlShapes.Control)) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
    }
}

private fun pinMarker(map: MapView, point: GeoPoint, color: Int) = Marker(map).apply {
    position = point
    icon = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(5, android.graphics.Color.WHITE)
        setSize(44, 44)
    }
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    setOnMarkerClickListener { _, _ -> true }
}

/**
 * A location someone sent, on a full-screen OpenStreetMap map, with its description, the coordinates (tap to
 * copy) and "Open in maps app" for directions in another app.
 */
@Composable
fun LocationViewerDialog(title: String, geoUri: String, onDismiss: () -> Unit) {
    val spot = remember(geoUri) { parseGeoUri(geoUri) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    if (spot == null) {
        LaunchedEffect(Unit) { dismiss() }
        return
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val point = remember(spot) { GeoPoint(spot.lat, spot.lon) }
        val map = rememberMap(point, 16.0)
        val accent = CtrlColors.Accent.toArgb()
        LaunchedEffect(map) {
            map.overlays.add(pinMarker(map, point, accent))
            map.invalidate()
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = { ScreenTopBar(title, onBack = onDismiss) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
                    CtrlSquareButton(
                        onClick = { map.controller.animateTo(point, 17.0, 600L) },
                        contentDescription = stringResource(R.string.location_recenter),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        size = 52.dp,
                    ) { Icon(Icons.Outlined.Place, null, tint = CtrlColors.AccentText) }
                    OsmCredit(Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
                Column(
                    Modifier.fillMaxWidth().background(CtrlColors.Surface).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(spot.display(), style = CtrlType.Meta, color = CtrlColors.Text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { copyText(context, Strings.str(R.string.location_3), spot.display()) }) {
                            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.copy))
                        }
                    }
                    spot.accuracy?.let { Text(stringResource(R.string.location_accuracy, it.toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    CtrlButton(
                        onClick = {
                            // Only the coordinates go to the other app, never the rest of the sender's URI.
                            val uri = Uri.parse("geo:${spot.lat},${spot.lon}?q=${spot.lat},${spot.lon}")
                            runCatching {
                                com.vknxworks.elementctrl.ui.lock.AppLock.expectReturn()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { scope.launch { snackbar.showSnackbar(Strings.str(R.string.no_maps_app_to_open_this_location)) } }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.location_open_in_maps))
                    }
                }
            }
        }
    }
}
