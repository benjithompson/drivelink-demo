package com.drivelink.demo.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Window
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.art.DlIcons
import com.drivelink.core.designsystem.art.VehicleTopView
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlUnderlineTabs
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.component.StatusPill
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.format.displayMessageWithId

/** Space at the end of a scrolling screen, so the scenario chip never covers text. */
private val ChipClearance = 64.dp

/** Vehicle Status sub-screen: Quick View (diagram and rows) and Full List (every item as a row). */
@Composable
fun StatusRoute(viewModel: StatusViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StatusScreen(state, onRefresh = viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(state: StatusUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val c = DlTheme.colors
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    // Each tab starts at the top.
    LaunchedEffect(tab) { scroll.scrollTo(0) }
    Column(modifier.fillMaxSize().testTag("screen_status")) {
        DlUnderlineTabs(listOf("Quick View", "Full List"), tab, { tab = it })
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).testTag("status_refresh"),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                when {
                    state.hasData -> {
                        if (state.error != null || state.stale) {
                            Column(
                                Modifier.padding(horizontal = 20.dp).padding(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (state.error != null) {
                                    DlBanner(
                                        BannerKind.Error,
                                        state.error.displayMessageWithId(),
                                        actionLabel = "Retry",
                                        onAction = onRefresh,
                                    )
                                }
                                if (state.stale) {
                                    DlBanner(
                                        BannerKind.Stale,
                                        "Vehicle is offline. Status may be out of date" +
                                            (state.staleAge?.let { " (updated $it)." } ?: "."),
                                    )
                                }
                            }
                        }
                        if (tab == 0) QuickView(state) else FullList(state)
                    }
                    state.error != null -> Box(Modifier.padding(20.dp)) {
                        ErrorState(
                            message = state.error.displayMessage(),
                            correlationId = state.error.correlationId,
                            onRetry = onRefresh,
                            title = "Couldn't load vehicle status",
                        )
                    }
                    else -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("status_loading"))
                    }
                }
                Spacer(Modifier.height(ChipClearance))
            }
        }
    }
}

@Composable
private fun QuickView(state: StatusUiState) {
    val c = DlTheme.colors
    val o = state.openings
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .background(c.card)
                .height(400.dp)
                .padding(vertical = 16.dp)
                .testTag("status_diagram"),
            contentAlignment = Alignment.Center,
        ) {
            VehicleTopView(
                Modifier.height(320.dp),
                fill = c.tile.copy(alpha = 0.55f),
                outline = c.tile,
                alertColor = c.error,
                openings = o,
            )
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    DlIcons.Fan,
                    contentDescription = if (state.climateOn) "Climate on" else "Climate off",
                    tint = if (state.climateOn) c.brand else c.textSecondary,
                    modifier = Modifier.size(26.dp).testTag("status_climate_icon"),
                )
                Spacer(Modifier.height(90.dp))
                Icon(
                    if (state.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (state.locked) "Locked" else "Unlocked",
                    tint = if (state.locked) c.brand else c.error,
                    modifier = Modifier.size(26.dp).testTag("status_lock_icon"),
                )
            }
            StatusPill(
                if (o.hood) "Hood Open" else "Hood Closed",
                Modifier.align(Alignment.TopCenter).testTag("pill_hood"), alert = o.hood,
            )
            DoorPill(o.frontLeft, "pill_door_fl", Modifier.align(Alignment.CenterStart).padding(start = 16.dp, bottom = 90.dp))
            DoorPill(o.rearLeft, "pill_door_rl", Modifier.align(Alignment.CenterStart).padding(start = 16.dp, top = 40.dp))
            DoorPill(o.frontRight, "pill_door_fr", Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, bottom = 90.dp))
            DoorPill(o.rearRight, "pill_door_rr", Modifier.align(Alignment.CenterEnd).padding(end = 16.dp, top = 40.dp))
            StatusPill(
                if (o.trunk) "Trunk Open" else "Trunk Closed",
                Modifier.align(Alignment.BottomCenter).testTag("pill_trunk"), alert = o.trunk,
            )
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            state.quickRows.forEach { row ->
                StatusInfoRow(row, state.isEv)
                if (row.key == "tires") TirePressures(state)
            }
            Text(
                state.updatedText,
                modifier = Modifier.padding(top = 12.dp).testTag("status_updated"),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

@Composable
private fun FullList(state: StatusUiState) {
    val c = DlTheme.colors
    Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp).testTag("status_full_list")) {
        state.fullRows.forEach { StatusInfoRow(it, state.isEv) }
        Text(
            state.updatedText,
            modifier = Modifier.padding(top = 12.dp).testTag("status_updated"),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
    }
}

@Composable
private fun DoorPill(open: Boolean, tag: String, modifier: Modifier) =
    StatusPill(if (open) "Door Open" else "Door Closed", modifier.testTag(tag), alert = open)

/** The four tire pressures under the Tire Pressure row. A wheel in `lowWheels` is red. */
@Composable
private fun TirePressures(state: StatusUiState) {
    val c = DlTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.card)
            .padding(12.dp)
            .testTag("status_tires"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.tires.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { tire ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag("tire_${tire.position}")) {
                        Text(tire.label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Text(
                            tire.text,
                            modifier = Modifier.testTag("tire_${tire.position}_value"),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (tire.low) c.error else c.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusInfoRow(row: StatusRow, isEv: Boolean) {
    val c = DlTheme.colors
    val color: Color = when (row.tone) {
        Tone.Normal -> c.textSecondary
        Tone.Good -> c.successStrong
        Tone.Alert -> c.error
    }
    Column(Modifier.fillMaxWidth().testTag("status_row_${row.key}")) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(iconFor(row.key, isEv), contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(row.label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(
                row.value,
                modifier = Modifier.testTag("status_row_${row.key}_value"),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
        HorizontalDivider(color = c.divider)
    }
}

private fun iconFor(key: String, isEv: Boolean): ImageVector = when {
    key == "location" -> Icons.Filled.Place
    key == "vehicle" -> Icons.Filled.PowerSettingsNew
    key == "lock" -> Icons.Filled.Lock
    key == "climate" -> DlIcons.Fan
    key == "tires" || key.startsWith("tire_") -> Icons.Filled.TireRepair
    key.startsWith("window_") -> Icons.Filled.Window
    key == "level" -> if (isEv) Icons.Filled.Bolt else Icons.Filled.LocalGasStation
    key == "range" -> Icons.Filled.Route
    key == "charging" -> Icons.Filled.Bolt
    key == "odometer" -> Icons.Filled.Speed
    key == "oil" -> Icons.Filled.OilBarrel
    key == "aux12v" -> Icons.Filled.BatteryFull
    else -> Icons.Filled.DirectionsCar
}
