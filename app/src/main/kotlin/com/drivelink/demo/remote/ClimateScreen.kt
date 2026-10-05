package com.drivelink.demo.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.component.BannerKind
import com.drivelink.core.designsystem.component.DlBanner
import com.drivelink.core.designsystem.component.DlLevelSelector
import com.drivelink.core.designsystem.component.DlPrimaryButton
import com.drivelink.core.designsystem.component.DlToggleRow
import com.drivelink.core.designsystem.component.TemperatureStepper
import com.drivelink.core.designsystem.theme.DlTheme

/** Remote Start. [onPin] opens the PIN prompt after "Start Vehicle" remembered the START command. */
@Composable
fun ClimateRoute(
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClimateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ClimateScreen(
        state = state,
        onPreset = viewModel::selectPreset,
        onLower = viewModel::lower,
        onRaise = viewModel::raise,
        onFrontDefrost = viewModel::setFrontDefrost,
        onRearDefrost = viewModel::setRearDefrost,
        onHeatedWheel = viewModel::setHeatedWheel,
        onHeatedSeats = viewModel::setHeatedSeats,
        onDuration = viewModel::setDuration,
        onRetryPresets = viewModel::loadPresets,
        onStart = {
            viewModel.requestStart()
            onPin()
        },
        modifier = modifier,
    )
}

private val SeatLevels = listOf("Off", "1", "2", "3")

@Composable
fun ClimateScreen(
    state: ClimateUiState,
    onPreset: (Int) -> Unit,
    onLower: () -> Unit,
    onRaise: () -> Unit,
    onFrontDefrost: (Boolean) -> Unit,
    onRearDefrost: (Boolean) -> Unit,
    onHeatedWheel: (Boolean) -> Unit,
    onHeatedSeats: (Int) -> Unit,
    onDuration: (Int) -> Unit,
    onRetryPresets: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = DlTheme.colors
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("screen_climate"),
    ) {
        state.presetError?.let {
            DlBanner(BannerKind.Warning, "Presets are not available. ${it.message}", actionLabel = "Retry", onAction = onRetryPresets)
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.presets.forEachIndexed { i, preset ->
                val on = i == state.selectedPreset
                Text(
                    preset.name,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (on) c.brand else c.card)
                        .clickable { onPreset(i) }
                        .semantics {
                            selected = on
                            contentDescription = "Preset ${preset.name}"
                            stateDescription = if (on) "Selected" else "Not selected"
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("preset_$i"),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) c.onBrand else c.textPrimary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        TemperatureStepper(state.temperature, onDecrease = onLower, onIncrease = onRaise)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = c.divider)
        DlToggleRow("Front Defrost", state.frontDefrost, onFrontDefrost, icon = Icons.Filled.AcUnit)
        DlToggleRow("Rear Defrost", state.rearDefrost, onRearDefrost, icon = Icons.Filled.Air)
        DlToggleRow("Heated Steering Wheel", state.heatedWheel, onHeatedWheel, icon = Icons.Filled.RadioButtonChecked)
        DlLevelSelector(
            label = "Heated Seats",
            options = SeatLevels,
            selectedIndex = state.heatedSeats,
            onSelect = onHeatedSeats,
            tag = "climate_heated_seats",
            icon = Icons.Filled.EventSeat,
        )
        HorizontalDivider(color = c.divider)
        Spacer(Modifier.height(12.dp))
        Row {
            Text("Duration", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text("${state.durationMin} min", style = MaterialTheme.typography.titleSmall, color = c.brand)
        }
        DlSlider(
            value = state.durationMin.toFloat(),
            onValueChange = { onDuration(it.toInt()) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier
                .semantics { contentDescription = "Duration ${state.durationMin} minutes" }
                .testTag("climate_duration"),
        )
        Spacer(Modifier.height(16.dp))
        DlPrimaryButton("Start Vehicle", onStart, Modifier.fillMaxWidth().testTag("climate_start"))
    }
}
