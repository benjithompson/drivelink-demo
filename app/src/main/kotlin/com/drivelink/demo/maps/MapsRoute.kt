package com.drivelink.demo.maps

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.format.displayMessageWithId
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.error.AppError
import org.osmdroid.util.GeoPoint

/** Space that the scenario chip of the app shell takes at the bottom left of the sheet. */
private val ChipClearance = 56.dp

/**
 * Maps tab (no top bar; the map runs under the status bar). Shows the selected vehicle on an
 * OpenStreetMap map (osmdroid, no API key), a right rail of buttons and a bottom sheet with the
 * "My Vehicle" card. Only Vehicle (recenter) and Refresh do something; the other buttons show a
 * message.
 */
@Composable
fun MapsRoute(modifier: Modifier = Modifier, viewModel: MapsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onShown() }
    MapsScreen(state, onRefresh = viewModel::refresh, modifier = modifier)
}

/**
 * Test tags: `screen_maps`, `map_vehicle_marker`, `map_recenter`, `map_refresh`, `map_route`,
 * `map_my_location`, `map_chargers`, `map_service`, `map_attribution`, `map_search`,
 * `map_shortcut_<name>`, `map_loading`, `map_vehicle_card`, `map_vehicle_name`,
 * `map_vehicle_address`, `map_vehicle_distance`, `map_vehicle_accuracy`, `map_vehicle_updated`,
 * and the error tags `error_state`, `error_message`, `error_correlation_id`, `error_retry`,
 * `banner_error`, `banner_action`.
 */
@Composable
fun MapsScreen(state: MapsUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    val density = LocalDensity.current
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var markerPx by remember { mutableStateOf<IntOffset?>(null) }
    var recenterToken by remember { mutableIntStateOf(0) }
    val location = state.location
    val vehicle = remember(location?.lat, location?.lon) { location?.let { GeoPoint(it.lat, it.lon) } }
    // The map ends 20 dp under the top edge of the sheet, so the rounded sheet corners show the map.
    val mapBottom = with(density) { sheetHeightPx.toDp() } - 20.dp
    val mapPadding = if (mapBottom > 0.dp) mapBottom else 0.dp

    Box(modifier.fillMaxSize().testTag("screen_maps")) {
        Box(Modifier.fillMaxSize().padding(bottom = mapPadding)) {
            OsmMap(vehicle, recenterToken, onVehiclePixel = { markerPx = it }, modifier = Modifier.fillMaxSize())
            val pixel = markerPx
            if (vehicle != null && pixel != null) {
                VehicleMarker(
                    description = state.vehicleTitle.ifEmpty { "Vehicle" } + " location",
                    modifier = Modifier.offset {
                        val radius = MarkerRadius.roundToPx()
                        IntOffset(pixel.x - radius, pixel.y - radius)
                    },
                )
            }
            Text(
                "© OpenStreetMap contributors",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // The last 20 dp of the map sit under the rounded top of the sheet.
                    .padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 26.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp)
                    .testTag("map_attribution"),
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
            )
        }
        Rail(
            hasLocation = vehicle != null,
            onRecenter = { recenterToken++ },
            onRefresh = onRefresh,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(12.dp),
        )
        Sheet(
            state = state,
            onRefresh = onRefresh,
            modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { sheetHeightPx = it.height },
        )
    }
}

private val MarkerRadius = 20.dp

/** The vehicle on the map: a round badge centered on the position. A Compose node, so tests and UiAutomator can find it. */
@Composable
private fun VehicleMarker(description: String, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    Box(
        modifier
            .size(MarkerRadius * 2)
            .clip(CircleShape)
            .background(c.accent)
            .testTag("map_vehicle_marker")
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Rail(hasLocation: Boolean, onRecenter: () -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun stub(what: String) = Toast.makeText(context, "$what is not available in the demo.", Toast.LENGTH_SHORT).show()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RailButton(Icons.Filled.Directions, "Route", "map_route") { stub("Route planning") }
        RailButton(Icons.Filled.DirectionsCar, "Vehicle", "map_recenter", enabled = hasLocation, onClick = onRecenter)
        RailButton(Icons.Filled.MyLocation, "My location", "map_my_location") { stub("Your location") }
        RailButton(Icons.Filled.Bolt, "Chargers", "map_chargers") { stub("The charger search") }
        RailButton(Icons.Filled.Storefront, "Service centers", "map_service") { stub("The service center search") }
        RailButton(Icons.Filled.Refresh, "Refresh", "map_refresh", onClick = onRefresh)
    }
}

@Composable
private fun RailButton(icon: ImageVector, label: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = DlTheme.colors
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) c.textPrimary else c.iconInactive,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun Sheet(state: MapsUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    val context = LocalContext.current
    fun stub(what: String) = Toast.makeText(context, "$what is not available in the demo.", Toast.LENGTH_SHORT).show()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.divider)
                .clickable { stub("Navigation") }
                .padding(12.dp)
                .testTag("map_search"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = c.textSecondary)
            Spacer(Modifier.width(8.dp))
            Text("Navigate", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
        }
        if (state.location != null || state.error == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Shortcut(Icons.Filled.Bolt, "Chargers") { stub("The charger search") }
                Shortcut(Icons.Filled.Storefront, "Service") { stub("The service center search") }
                Shortcut(Icons.Filled.LocalParking, "Parking") { stub("Parking search") }
                Shortcut(Icons.Filled.Home, "Home") { stub("Home navigation") }
                Shortcut(Icons.Filled.Work, "Work") { stub("Work navigation") }
            }
        }
        Text("My Vehicle", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        VehicleCard(state, onRefresh)
        if (state.scenarioChipVisible) Spacer(Modifier.height(ChipClearance))
    }
}

@Composable
private fun VehicleCard(state: MapsUiState, onRefresh: () -> Unit) {
    val c = DlTheme.colors
    val location = state.location
    val error = state.error
    when {
        location != null -> {
            if (error != null) {
                DlBanner(
                    BannerKind.Error,
                    error.displayMessageWithId(),
                    actionLabel = "Retry",
                    onAction = onRefresh,
                )
            }
            Column(Modifier.fillMaxWidth().testTag("map_vehicle_card"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    state.vehicleTitle,
                    modifier = Modifier.testTag("map_vehicle_name"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
                Text(
                    location.address,
                    modifier = Modifier.testTag("map_vehicle_address"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        location.distance,
                        modifier = Modifier.testTag("map_vehicle_distance"),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textPrimary,
                    )
                    Text(
                        location.accuracy,
                        modifier = Modifier.testTag("map_vehicle_accuracy"),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                }
                Text(
                    location.updated,
                    modifier = Modifier.testTag("map_vehicle_updated"),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
        error != null -> ErrorState(
            message = error.displayMessage(),
            correlationId = error.correlationId,
            onRetry = onRefresh,
            title = "Couldn't load the vehicle location",
        )
        else -> Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("map_loading"))
        }
    }
}

@Composable
private fun Shortcut(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = DlTheme.colors
    Column(
        Modifier.clickable(onClick = onClick).testTag("map_shortcut_${label.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(c.brand),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = c.onBrand) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
    }
}
