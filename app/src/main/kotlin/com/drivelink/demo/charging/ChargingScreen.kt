package com.drivelink.demo.charging

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.ChargeProgressBar
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlOutlinedButton
import com.drivelink.core.designsystem.component.DlPrimaryButton
import com.drivelink.core.designsystem.component.ErrorState
import com.drivelink.core.designsystem.component.SectionTitle
import com.drivelink.core.designsystem.theme.DlTheme
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.format.displayMessageWithId
import com.drivelink.demo.remote.DlSlider

/** Space at the end of a scrolling screen, so the scenario chip never covers text. */
internal val ChipClearance = 64.dp

/**
 * Charging sub-screen. [onPin] opens Enter PIN after a charge command is set as pending (see RemoteCommands);
 * [onSchedule] opens Charging Schedule; [onFindStation] opens the Maps tab.
 */
@Composable
fun ChargingRoute(
    onPin: () -> Unit,
    onSchedule: () -> Unit,
    onFindStation: () -> Unit,
    viewModel: ChargingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChargingScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onToggleCharge = {
            viewModel.requestToggle()
            onPin()
        },
        onFindStation = onFindStation,
        onSchedule = onSchedule,
        onAcLimit = viewModel::setAcLimit,
        onDcLimit = viewModel::setDcLimit,
        onRetrySettings = viewModel::retrySettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingScreen(
    state: ChargingUiState,
    onRefresh: () -> Unit,
    onToggleCharge: () -> Unit,
    onFindStation: () -> Unit,
    onSchedule: () -> Unit,
    onAcLimit: (Int) -> Unit,
    onDcLimit: (Int) -> Unit,
    onRetrySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().testTag("charging_refresh"),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("screen_charging")) {
            when {
                !state.available -> Unavailable()
                state.hasData -> ChargingData(
                    state, onRefresh, onToggleCharge, onFindStation, onSchedule, onAcLimit, onDcLimit, onRetrySettings,
                )
                state.error != null -> Box(Modifier.padding(20.dp)) {
                    ErrorState(
                        message = state.error.displayMessage(),
                        correlationId = state.error.correlationId,
                        onRetry = onRefresh,
                        title = "Couldn't load charging",
                    )
                }
                else -> Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("charging_loading"))
                }
            }
            Spacer(Modifier.height(ChipClearance))
        }
    }
}

@Composable
private fun Unavailable() {
    val c = DlTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(20.dp).padding(top = 24.dp).testTag("charging_unavailable"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.FlashOff, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "Charging is not available for this vehicle",
            modifier = Modifier.testTag("charging_unavailable_text"),
            style = MaterialTheme.typography.titleMedium,
            color = c.textPrimary,
        )
    }
}

@Composable
private fun ChargingData(
    state: ChargingUiState,
    onRefresh: () -> Unit,
    onToggleCharge: () -> Unit,
    onFindStation: () -> Unit,
    onSchedule: () -> Unit,
    onAcLimit: (Int) -> Unit,
    onDcLimit: (Int) -> Unit,
    onRetrySettings: () -> Unit,
) {
    val c = DlTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.success.copy(alpha = 0.55f),MaterialTheme.colorScheme.background)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("Charging", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        }
        Text(
            state.stateLabel,
            modifier = Modifier.testTag("charge_state").semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
        )
        Text(state.updatedText, modifier = Modifier.testTag("charge_updated"), style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
        Spacer(Modifier.height(16.dp))
        ChargeProgressBar(
            percent = state.levelPct.coerceIn(0, 100),
            limitPercent = state.limitPct,
            modifier = Modifier.testTag("charge_bar"),
        )
    }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        StatGrid(state)
        if (state.active) {
            DlOutlinedButton(
                "Stop charge", onToggleCharge, Modifier.fillMaxWidth().testTag("charge_stop"),
                enabled = !state.commandRunning, icon = Icons.Filled.FlashOff,
            )
        } else {
            DlOutlinedButton(
                "Start charge", onToggleCharge, Modifier.fillMaxWidth().testTag("charge_start"),
                enabled = state.pluggedIn && !state.commandRunning, icon = Icons.Filled.Bolt,
            )
        }
        DlPrimaryButton(
            "Find a charging station", onFindStation, Modifier.fillMaxWidth().testTag("charge_find_station"),
            icon = Icons.Filled.Search,
        )
        Column {
            ValueRow(Icons.Filled.Bolt, "Charging Limits", state.limitsText, "row_charge_limits")
            ValueRow(Icons.Filled.Schedule, "Charging Schedule", state.scheduleText, "row_charge_schedule", onSchedule)
            ValueRow(
                Icons.Filled.Thermostat, "Departure Climate", state.departureText, "row_departure",
                onSchedule, showDivider = false,
            )
        }
        SectionTitle("Charging Limits")
        Limits(state, onAcLimit, onDcLimit, onRetrySettings)
    }
}

@Composable
private fun StatGrid(state: ChargingUiState) {
    val c = DlTheme.colors
    Column(Modifier.fillMaxWidth().testTag("charge_stats")) {
        state.stats.chunked(2).forEachIndexed { row, pair ->
            if (row > 0) HorizontalDivider(color = c.divider)
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                pair.forEach { stat ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag("stat_${stat.key}")) {
                        Text(stat.label, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Text(
                            stat.value,
                            modifier = Modifier.testTag("stat_${stat.key}_value"),
                            style = if (row == 0) {
                                MaterialTheme.typography.displayMedium
                            } else {
                                MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal)
                            },
                            color = c.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueRow(
    icon: ImageVector,
    label: String,
    value: String,
    tag: String,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val c = DlTheme.colors
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = c.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(value, modifier = Modifier.testTag("${tag}_value"), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        if (showDivider) HorizontalDivider(color = c.divider)
    }
}

@Composable
private fun Limits(
    state: ChargingUiState,
    onAcLimit: (Int) -> Unit,
    onDcLimit: (Int) -> Unit,
    onRetrySettings: () -> Unit,
) {
    val c = DlTheme.colors
    val limits = state.limits
    when {
        limits != null -> {
            LimitSlider("AC (Level 2)", limits.ac, "limit_ac", onAcLimit)
            LimitSlider("DC (Fast charge)", limits.dc, "limit_dc", onDcLimit)
            Text(
                state.saveText,
                modifier = Modifier
                    .testTag("limits_status")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.saveStatus == SaveStatus.Error) c.error else c.textSecondary,
            )
        }
        state.settingsError != null -> DlBanner(
            BannerKind.Warning,
            "Charging limits are not available. " + state.settingsError.displayMessageWithId(),
            actionLabel = "Retry",
            onAction = onRetrySettings,
        )
        else -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = c.brand, modifier = Modifier.testTag("limits_loading"))
        }
    }
}

@Composable
private fun LimitSlider(label: String, percent: Int, tag: String, onChange: (Int) -> Unit) {
    val c = DlTheme.colors
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(
                "$percent%",
                modifier = Modifier.testTag("${tag}_value"),
                style = MaterialTheme.typography.titleSmall,
                color = c.brand,
            )
        }
        DlSlider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 50f..100f,
            steps = 4,
            modifier = Modifier
                .semantics { contentDescription = "$label limit $percent percent" }
                .testTag(tag),
        )
    }
}
