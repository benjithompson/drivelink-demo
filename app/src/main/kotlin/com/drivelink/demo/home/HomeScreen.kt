package com.drivelink.demo.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.format.displayMessageWithId
import com.drivelink.core.designsystem.art.DlIcons
import com.drivelink.core.designsystem.art.VehicleSideView
import com.drivelink.core.designsystem.component.ActionTile
import com.drivelink.core.designsystem.component.ActionTileRow
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.BigStat
import com.drivelink.core.designsystem.component.CommandProgressCard
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlTopBar
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.component.HeroBackdrop
import com.drivelink.core.designsystem.component.InfoRow
import com.drivelink.core.designsystem.component.StatusChip
import com.drivelink.core.designsystem.component.VehicleHeader
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.model.CommandType

/** Where a Home action leads. The navigation graph implements it. */
data class HomeActions(
    val onLock: () -> Unit,
    val onClimate: () -> Unit,
    val onCharge: () -> Unit,
    val onFuel: () -> Unit,
    val onControls: () -> Unit,
    val onLocation: () -> Unit,
    val onStatus: () -> Unit,
    val onAlerts: () -> Unit,
    val onTrips: () -> Unit,
)

/** Home tab with its own top bar. [onLockRequested] runs after the Lock tile set the pending command. */
@Composable
fun HomeRoute(
    actions: HomeActions,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onShown() }
    HomeScreen(
        state = state,
        actions = actions.copy(onLock = {
            viewModel.requestLockToggle()
            actions.onLock()
        }),
        onRefresh = viewModel::refresh,
        onSelectVehicle = viewModel::selectVehicle,
        onRetryCommand = viewModel::retryCommand,
        onDismissCommand = viewModel::dismissCommand,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    onRefresh: () -> Unit,
    onSelectVehicle: (String) -> Unit,
    onRetryCommand: () -> Unit,
    onDismissCommand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    var picker by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        DlTopBar(alertCount = state.unreadAlerts, onAlerts = actions.onAlerts, onChat = {})
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).testTag("home_refresh"),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("screen_home"),
            ) {
                HeroBackdrop(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
                        VehicleHeader(
                            state.title.ifEmpty { "Your vehicle" },
                            state.trim,
                            onSwitch = if (state.vehicles.size > 1) ({ picker = true }) else null,
                        )
                        VehicleSideView(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                            style = state.style,
                            paint = state.paint,
                        )
                    }
                }
                Column(
                    Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when {
                        state.hasData -> HomeData(state, actions, onRefresh, onRetryCommand, onDismissCommand)
                        state.error != null -> ErrorState(
                            message = state.error.displayMessage(),
                            correlationId = state.error.correlationId,
                            onRetry = onRefresh,
                            modifier = Modifier.padding(top = 8.dp),
                            title = "Couldn't load your vehicle",
                        )
                        else -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("home_loading"))
                        }
                    }
                    // Room for the scenario chip (bottom left), so it never covers the last row.
                    Spacer(Modifier.height(64.dp))
                }
            }
        }
    }
    if (picker) {
        VehiclePickerSheet(
            options = state.vehicles,
            onSelect = {
                picker = false
                onSelectVehicle(it)
            },
            onDismiss = { picker = false },
        )
    }
}

@Composable
private fun HomeData(
    state: HomeUiState,
    actions: HomeActions,
    onRefresh: () -> Unit,
    onRetryCommand: () -> Unit,
    onDismissCommand: () -> Unit,
) {
    val c = DlTheme.colors
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
            "Vehicle is offline. Status may be out of date" + (state.staleAge?.let { " (updated $it)." } ?: "."),
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
    ) {
        BigStat(
            "${state.level}", "%", if (state.isEv) "Battery" else "Fuel",
            Modifier.testTag("stat_level"),
            valueColor = if (state.lowLevel) c.error else c.textPrimary,
            centered = true,
        )
        BigStat("${state.range}", state.rangeUnit, "Est. Range", Modifier.testTag("stat_range"), centered = true)
    }
    if (state.lowLevel) {
        DlBanner(
            BannerKind.Warning,
            if (state.isEv) "Low range. Find a charging station soon." else "Low fuel. Find a gas station soon.",
        )
    }
    state.chip?.let { chip ->
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StatusChip(
                chip.label,
                Modifier.testTag("status_chip"),
                value = chip.value,
                leadingIcon = if (!state.isEv) (if (state.locked) Icons.Filled.Lock else Icons.Filled.LockOpen) else null,
                onClick = if (chip.opensCharging) actions.onCharge else null,
            )
        }
    }
    ActionTileRow { m ->
        val running = state.command?.takeIf { it.running }?.type
        ActionTile(
            if (state.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            if (state.locked) "Locked" else "Unlocked",
            onClick = actions.onLock,
            modifier = m,
            active = state.locked,
            busy = running == CommandType.LOCK || running == CommandType.UNLOCK,
            testTag = "tile_lock",
        )
        ActionTile(
            DlIcons.Fan, if (state.climateOn) "Climate on" else "Climate", actions.onClimate, m,
            active = state.climateOn,
            busy = running == CommandType.START || running == CommandType.STOP,
            testTag = "tile_climate",
        )
        if (state.isEv) {
            ActionTile(
                Icons.Filled.Bolt, "Charge", actions.onCharge, m,
                busy = running == CommandType.CHARGE_START || running == CommandType.CHARGE_STOP,
                testTag = "tile_charge",
            )
        } else {
            ActionTile(Icons.Filled.LocalGasStation, "Fuel", actions.onFuel, m, testTag = "tile_fuel")
        }
        ActionTile(Icons.Filled.GridView, "Controls", actions.onControls, m, testTag = "tile_controls")
    }
    state.command?.let { command ->
        CommandProgressCard(
            commandLabel = command.label,
            stage = command.stage,
            detail = command.detail,
            onRetry = if (command.canRetry) onRetryCommand else null,
            onDismiss = onDismissCommand,
        )
    }
    Column {
        InfoRow(
            Icons.Filled.Place, "Location", state.locationText,
            onClick = actions.onLocation, modifier = Modifier.testTag("row_location"),
        )
        InfoRow(
            Icons.Filled.DirectionsCar, "Vehicle Status", state.vehicleStatusText,
            valueColor = if (state.doorOpen) c.error else c.textSecondary,
            onClick = actions.onStatus, modifier = Modifier.testTag("row_status"),
        )
        InfoRow(
            Icons.Filled.MonitorHeart, "Vehicle Health", state.healthText,
            valueColor = if (state.tireLow) c.error else c.accent,
            onClick = actions.onStatus, modifier = Modifier.testTag("row_health"),
        )
        InfoRow(
            Icons.Filled.Route, "Trips", state.tripText,
            showDivider = false, onClick = actions.onTrips, modifier = Modifier.testTag("row_trips"),
        )
    }
}

/** Bottom sheet that lists the vehicles of the account. Tags: `vehicle_picker`, `vehicle_option_<vin>`. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun VehiclePickerSheet(
    options: List<VehicleOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = DlTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .semantics { testTagsAsResourceId = true }
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .testTag("vehicle_picker"),
        ) {
            Text("Choose a vehicle", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            options.forEach { option ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option.vin) }
                        .semantics { stateDescription = if (option.selected) "Selected" else "Not selected" }
                        .testTag("vehicle_option_${option.vin}"),
                ) {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        VehicleSideView(Modifier.width(88.dp), style = option.style, paint = option.paint)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(option.trim, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            Text(
                                "VIN …${option.vinTail}",
                                modifier = Modifier.testTag("vehicle_option_${option.vin}_vin"),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary,
                            )
                        }
                        if (option.selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = c.brand,
                                modifier = Modifier.size(24.dp).testTag("vehicle_option_${option.vin}_selected"),
                            )
                        }
                    }
                    HorizontalDivider(color = c.divider)
                }
            }
        }
    }
}
