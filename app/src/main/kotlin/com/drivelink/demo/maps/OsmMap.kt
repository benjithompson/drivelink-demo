package com.drivelink.demo.maps

import android.content.Context
import android.graphics.Canvas
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.drivelink.core.designsystem.theme.DlTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** Map zoom when the map centers on the vehicle (street level). */
private const val VEHICLE_ZOOM = 16.0

/**
 * Sets the osmdroid user agent and the tile cache, once per process. The user agent is the
 * package name (the tile servers require one). The cache lives in the app cache directory, so
 * the app needs no storage permission. Safe to call again.
 */
fun configureOsmdroid(context: Context) {
    val app = context.applicationContext
    val config = Configuration.getInstance()
    config.userAgentValue = app.packageName
    val base = File(app.cacheDir, "osmdroid").apply { mkdirs() }
    config.osmdroidBasePath = base
    config.osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
}

/**
 * The map: an osmdroid [MapView] with OpenStreetMap tiles (no API key). It centers on [vehicle]
 * when the vehicle changes and whenever [recenterToken] changes. [onVehiclePixel] reports the
 * screen position of the vehicle inside the map view on each draw, so a Compose marker can
 * follow the map. Tiles need the internet; without them the map stays blank and everything else works.
 *
 * In previews and layout tests ([LocalInspectionMode]) a drawing stands in for the map, and the
 * marker sits in the middle.
 */
@Composable
fun OsmMap(
    vehicle: GeoPoint?,
    recenterToken: Int,
    onVehiclePixel: (IntOffset?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        MapPlaceholder(onVehiclePixel, modifier)
        return
    }
    val context = LocalContext.current
    val dark = DlTheme.isDark
    val mapView = remember {
        configureOsmdroid(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            controller.setZoom(VEHICLE_ZOOM)
        }
    }

    // The overlay runs on each draw of the map (pan, zoom, resize), so the marker never lags.
    val tracker = remember(mapView) { VehicleTracker(onVehiclePixel) }
    DisposableEffect(mapView, tracker) {
        mapView.overlays.add(tracker)
        onDispose { mapView.overlays.remove(tracker) }
    }
    SideEffect {
        tracker.callback = onVehiclePixel
        tracker.point = vehicle
        mapView.invalidate()
    }

    LaunchedEffect(mapView, dark) {
        // Map tiles are the only place with colors outside the theme: invert them in dark mode.
        mapView.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        mapView.invalidate()
    }
    LaunchedEffect(mapView, vehicle, recenterToken) {
        if (vehicle != null) {
            mapView.controller.setZoom(VEHICLE_ZOOM)
            mapView.controller.setCenter(vehicle)
            mapView.invalidate()
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Reports where [point] is on screen each time the map draws. Draws nothing. */
private class VehicleTracker(var callback: (IntOffset?) -> Unit) : Overlay() {
    var point: GeoPoint? = null
    private var last: IntOffset? = null

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val p = point
        val next = p?.let { mapView.projection.toPixels(it, null) }?.let { IntOffset(it.x, it.y) }
        if (next != last) {
            last = next
            callback(next)
        }
    }
}

@Composable
private fun MapPlaceholder(onVehiclePixel: (IntOffset?) -> Unit, modifier: Modifier) {
    val c = DlTheme.colors
    Box(modifier.fillMaxSize().onSizeChanged { onVehiclePixel(IntOffset(it.width / 2, it.height / 2)) }) {
        ComposeCanvas(Modifier.fillMaxSize()) {
            drawRect(c.row)
            drawCircle(c.success.copy(alpha = 0.18f), radius = size.width * 0.28f, center = Offset(size.width * 0.2f, size.height * 0.45f))
            drawLine(c.divider, Offset(0f, size.height * 0.38f), Offset(size.width, size.height * 0.52f), 18f)
            drawLine(c.divider, Offset(size.width * 0.35f, 0f), Offset(size.width * 0.55f, size.height), 14f)
        }
    }
}
