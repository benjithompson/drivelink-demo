package com.drivelink.demo.home

import androidx.compose.ui.graphics.Color
import com.drivelink.core.data.garage.GarageState
import com.drivelink.core.designsystem.art.BodyStyle
import com.drivelink.core.designsystem.art.VehiclePaint
import com.drivelink.core.domain.TimeAgo
import com.drivelink.core.domain.Units
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ChargeState
import com.drivelink.core.domain.model.OpenClosed
import com.drivelink.core.domain.model.Powertrain
import com.drivelink.core.domain.model.Vehicle
import com.drivelink.core.domain.model.VehicleStatus
import com.drivelink.demo.remote.CommandUi
import java.time.Instant
import java.util.Locale

/** One entry of the vehicle picker. [style] and [paint] draw the thumbnail. */
data class VehicleOption(
    val vin: String,
    val title: String,
    val trim: String,
    val selected: Boolean,
    val style: BodyStyle = BodyStyle.Crossover,
    val paint: Color = VehiclePaint.GlacierBlue,
) {
    /** The last six characters of the VIN, as the picker shows them. */
    val vinTail: String get() = vin.takeLast(6)
}

/** The status chip under the stats: a label and an optional highlighted value. */
data class ChipUi(val label: String, val value: String? = null, val opensCharging: Boolean = false)

/** Everything the Home screen draws, from API values and the user's units. */
data class HomeUiState(
    val title: String = "",
    val trim: String = "",
    val style: BodyStyle = BodyStyle.Crossover,
    val paint: Color = VehiclePaint.GlacierBlue,
    val isEv: Boolean = true,
    val vehicles: List<VehicleOption> = emptyList(),
    /** Battery or fuel percent; null before the status loads. */
    val level: Int? = null,
    val range: Int? = null,
    val rangeUnit: String = "mi",
    val lowLevel: Boolean = false,
    val locked: Boolean = true,
    val climateOn: Boolean = false,
    val chip: ChipUi? = null,
    val stale: Boolean = false,
    val staleAge: String? = null,
    val locationText: String = "Locating…",
    val vehicleStatusText: String = "Parked",
    val doorOpen: Boolean = false,
    val healthText: String = "All Systems Normal",
    val tireLow: Boolean = false,
    val tripText: String = "No trips yet",
    val unreadAlerts: Int = 0,
    /** A load or a refresh runs now. */
    val refreshing: Boolean = false,
    /** The last load failed. With [hasData] false the screen shows the error instead of the stats. */
    val error: AppError? = null,
    val command: CommandUi? = null,
) {
    val hasData: Boolean get() = level != null
}

/** Pure mapping from the shared vehicle state to the Home state. Tested without Android. */
fun homeUiState(garage: GarageState, command: CommandUi?, now: Instant = Instant.now()): HomeUiState {
    val vehicle = garage.selected
    val status = garage.status
    val units = garage.user?.units ?: Units.DEFAULT
    val (style, paint) = vehicle?.let { artFor(it.imageKey) } ?: (BodyStyle.Crossover to VehiclePaint.GlacierBlue)
    val base = HomeUiState(
        title = vehicle?.let { "${it.year} ${it.model}" }.orEmpty(),
        trim = vehicle?.trim.orEmpty(),
        style = style,
        paint = paint,
        isEv = vehicle?.powertrain != Powertrain.ICE,
        vehicles = garage.vehicles.map {
            val (optionStyle, optionPaint) = artFor(it.imageKey)
            VehicleOption(it.vin, "${it.year} ${it.model}", it.trim.orEmpty(), it.vin == vehicle?.vin, optionStyle, optionPaint)
        },
        unreadAlerts = garage.unreadAlerts,
        refreshing = garage.loading,
        error = garage.error,
        command = command,
        rangeUnit = Units.distanceLabel(units.distance),
    )
    if (status == null) return base
    val level = status.batteryPct ?: status.fuelPct ?: 0
    val last = garage.lastTrip
    return base.copy(
        level = level,
        range = Units.distanceRounded(status.rangeMi, units.distance),
        lowLevel = level <= LOW_LEVEL_PCT,
        locked = status.locked,
        climateOn = status.climate.on,
        chip = chipFor(status, vehicle),
        stale = garage.stale,
        staleAge = TimeAgo.describe(status.updatedAt, now),
        locationText = garage.locationLabel ?: "Unknown",
        doorOpen = status.anyOpen(),
        vehicleStatusText = when {
            status.anyOpen() -> "Door Open"
            status.engineOn -> "Running"
            else -> "Parked"
        },
        tireLow = status.tires.lowWarning,
        healthText = when {
            status.tires.lowWarning -> "Tire Pressure Low"
            !status.aux12vOk -> "12V Battery Low"
            else -> "All Systems Normal"
        },
        tripText = last?.let {
            val d = Units.distance(it.distanceMi, units.distance)
            "Last trip " + String.format(Locale.US, "%.1f", d) + " " + Units.distanceLabel(units.distance)
        } ?: "No trips yet",
    )
}

/** The battery or fuel level at or below this percent is red and shows a warning banner. */
const val LOW_LEVEL_PCT = 10

private fun VehicleStatus.anyOpen(): Boolean = listOf(doors.fl, doors.fr, doors.rl, doors.rr, doors.trunk, doors.hood)
    .any { it == OpenClosed.OPEN }

private fun chipFor(status: VehicleStatus, vehicle: Vehicle?): ChipUi? {
    if (vehicle?.powertrain == Powertrain.ICE) {
        return ChipUi(if (status.locked) "Doors Locked" else "Doors Unlocked")
    }
    val charging = status.charging ?: return ChipUi("Charge Schedule", "On", opensCharging = true)
    return when (charging.state) {
        ChargeState.CHARGING -> ChipUi("Charging", charging.minutesToTarget?.let { "$it min left" } ?: "On", opensCharging = true)
        ChargeState.COMPLETE -> ChipUi("Charge complete", opensCharging = true)
        ChargeState.NOT_PLUGGED -> ChipUi("Not plugged in", opensCharging = true)
        ChargeState.SCHEDULED -> ChipUi("Charge Schedule", "On", opensCharging = true)
    }
}

/** Maps `Vehicle.imageKey` (for example `crossover-glacier-blue`) to the in-app illustration. */
fun artFor(imageKey: String): Pair<BodyStyle, Color> {
    val style = if (imageKey.startsWith("sedan")) BodyStyle.Sedan else BodyStyle.Crossover
    val paint = when {
        imageKey.endsWith("glacier-blue") -> VehiclePaint.GlacierBlue
        imageKey.endsWith("ember-red") -> VehiclePaint.EmberRed
        imageKey.endsWith("arctic-white") -> VehiclePaint.ArcticWhite
        imageKey.endsWith("graphite-gray") -> VehiclePaint.GraphiteGray
        imageKey.endsWith("midnight-black") -> VehiclePaint.MidnightBlack
        imageKey.endsWith("sage-green") -> VehiclePaint.SageGreen
        else -> VehiclePaint.GlacierBlue
    }
    return style to paint
}
