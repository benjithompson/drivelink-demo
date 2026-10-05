package com.drivelink.demo.status

import com.drivelink.core.data.garage.GarageState
import com.drivelink.core.designsystem.art.VehicleOpenings
import com.drivelink.core.domain.TimeAgo
import com.drivelink.core.domain.Units
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ChargeState
import com.drivelink.core.domain.model.OpenClosed
import com.drivelink.core.domain.model.Powertrain
import com.drivelink.core.domain.model.WheelPosition
import com.drivelink.core.domain.model.WindowState
import com.drivelink.demo.home.LOW_LEVEL_PCT
import java.time.Instant
import java.util.Locale

/** How a row value is colored: normal text, green for a good state, red for a problem. */
enum class Tone { Normal, Good, Alert }

/**
 * One row of the Vehicle Status lists. [key] names the item (`hood`, `door_fl`, `lock`, ...) and
 * gives the test tag `status_row_<key>`; the value has the tag `status_row_<key>_value`.
 */
data class StatusRow(val key: String, val label: String, val value: String, val tone: Tone = Tone.Normal)

/** Tire pressure of one wheel, for the Quick View. The tag is `tire_<position>`. */
data class TireUi(val position: String, val label: String, val text: String, val low: Boolean)

/** Everything the Vehicle Status screen draws, from API values and the user's units. */
data class StatusUiState(
    val title: String = "",
    val isEv: Boolean = true,
    /** The status loaded. Without it the screen shows a progress indicator or the error card. */
    val hasData: Boolean = false,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val stale: Boolean = false,
    val staleAge: String? = null,
    val openings: VehicleOpenings = VehicleOpenings(),
    val locked: Boolean = true,
    val climateOn: Boolean = false,
    val tires: List<TireUi> = emptyList(),
    /** Rows under the diagram on the Quick View tab. */
    val quickRows: List<StatusRow> = emptyList(),
    /** Every item as a row, for the Full List tab. */
    val fullRows: List<StatusRow> = emptyList(),
    val updatedText: String = "",
)

/** Pure mapping from the shared vehicle state to the Status screen state. Tested without Android. */
fun statusUiState(garage: GarageState, now: Instant = Instant.now()): StatusUiState {
    val vehicle = garage.selected
    val status = garage.status
    val base = StatusUiState(
        title = vehicle?.let { "${it.year} ${it.model}" }.orEmpty(),
        isEv = vehicle?.powertrain != Powertrain.ICE,
        refreshing = garage.loading,
        error = garage.error,
    )
    if (status == null) return base
    val units = garage.user?.units ?: Units.DEFAULT
    val d = status.doors
    fun open(v: OpenClosed) = v == OpenClosed.OPEN
    val lowWheels = status.tires.lowWheels.toSet()
    val tires = listOf(
        Triple(WheelPosition.FL, "Front left", status.tires.flPsi),
        Triple(WheelPosition.FR, "Front right", status.tires.frPsi),
        Triple(WheelPosition.RL, "Rear left", status.tires.rlPsi),
        Triple(WheelPosition.RR, "Rear right", status.tires.rrPsi),
    ).map { (pos, label, psi) -> TireUi(pos.name.lowercase(), label, "$psi psi", pos in lowWheels) }
    val tireLow = status.tires.lowWarning || lowWheels.isNotEmpty()
    val level = status.batteryPct ?: status.fuelPct
    val levelLabel = if (base.isEv) "Battery" else "Fuel"
    val lowLevel = level != null && level <= LOW_LEVEL_PCT
    val range = "${Units.distanceRounded(status.rangeMi, units.distance)} ${Units.distanceLabel(units.distance)}"
    val odometer = String.format(Locale.US, "%,d", Units.distanceRounded(status.odometerMi, units.distance)) +
        " " + Units.distanceLabel(units.distance)
    val location = garage.locationLabel?.let { if (status.engineOn) it else "Parked in $it" } ?: "Unknown"
    val vehicleOn = if (status.engineOn) "On" else "Off"
    val vehicleTone = if (status.engineOn) Tone.Normal else Tone.Good
    val climateText = if (status.climate.on) "On" else "Off"
    val tireText = if (tireLow) "Low" else "Normal"
    val tireTone = if (tireLow) Tone.Alert else Tone.Good

    val quick = listOf(
        StatusRow("location", "Location", location),
        StatusRow("vehicle", "Vehicle", vehicleOn, vehicleTone),
        StatusRow("tires", "Tire Pressure", tireText, tireTone),
        StatusRow("climate", "Climate", climateText, Tone.Good),
        StatusRow("level", levelLabel, (level?.let { "$it%" } ?: "Unknown") + " · " + range, if (lowLevel) Tone.Alert else Tone.Normal),
        StatusRow("odometer", "Odometer", odometer),
    )
    val full = buildList {
        add(StatusRow("location", "Location", location))
        add(StatusRow("vehicle", "Vehicle", vehicleOn, vehicleTone))
        add(StatusRow("lock", "Doors", if (status.locked) "Locked" else "Unlocked", if (status.locked) Tone.Good else Tone.Alert))
        add(StatusRow("hood", "Hood", openText(open(d.hood)), openTone(open(d.hood))))
        add(StatusRow("door_fl", "Front left door", openText(open(d.fl)), openTone(open(d.fl))))
        add(StatusRow("door_fr", "Front right door", openText(open(d.fr)), openTone(open(d.fr))))
        add(StatusRow("door_rl", "Rear left door", openText(open(d.rl)), openTone(open(d.rl))))
        add(StatusRow("door_rr", "Rear right door", openText(open(d.rr)), openTone(open(d.rr))))
        add(StatusRow("trunk", "Trunk", openText(open(d.trunk)), openTone(open(d.trunk))))
        status.windows?.let { w ->
            listOf(
                Triple("fl", "Front left window", w.fl),
                Triple("fr", "Front right window", w.fr),
                Triple("rl", "Rear left window", w.rl),
                Triple("rr", "Rear right window", w.rr),
            ).forEach { (key, label, state) ->
                if (state != null) {
                    add(StatusRow("window_$key", label, windowText(state), if (state == WindowState.CLOSED) Tone.Good else Tone.Alert))
                }
            }
        }
        add(StatusRow("climate", "Climate", climateText, Tone.Good))
        add(StatusRow("tires", "Tire Pressure", tireText, tireTone))
        tires.forEach { add(StatusRow("tire_${it.position}", it.label + " tire", it.text, if (it.low) Tone.Alert else Tone.Normal)) }
        add(StatusRow("level", levelLabel, level?.let { "$it%" } ?: "Unknown", if (lowLevel) Tone.Alert else Tone.Normal))
        add(StatusRow("range", "Est. Range", range))
        status.charging?.let { add(StatusRow("charging", "Charging", chargeText(it.state))) }
        add(StatusRow("odometer", "Odometer", odometer))
        status.oilLifePct?.let { add(StatusRow("oil", "Oil life", "$it%", if (it <= 10) Tone.Alert else Tone.Normal)) }
        add(StatusRow("aux12v", "12V battery", if (status.aux12vOk) "OK" else "Low", if (status.aux12vOk) Tone.Good else Tone.Alert))
    }
    return base.copy(
        hasData = true,
        stale = garage.stale,
        staleAge = TimeAgo.describe(status.updatedAt, now),
        openings = VehicleOpenings(
            frontLeft = open(d.fl), frontRight = open(d.fr), rearLeft = open(d.rl), rearRight = open(d.rr),
            trunk = open(d.trunk), hood = open(d.hood),
        ),
        locked = status.locked,
        climateOn = status.climate.on,
        tires = tires,
        quickRows = quick,
        fullRows = full,
        updatedText = "Updated " + (TimeAgo.describe(status.updatedAt, now) ?: "recently"),
    )
}

private fun openText(open: Boolean) = if (open) "Open" else "Closed"

private fun openTone(open: Boolean) = if (open) Tone.Alert else Tone.Good

private fun windowText(state: WindowState) = when (state) {
    WindowState.OPEN -> "Open"
    WindowState.CLOSED -> "Closed"
    WindowState.VENTED -> "Vented"
}

private fun chargeText(state: ChargeState) = when (state) {
    ChargeState.CHARGING -> "Charging"
    ChargeState.COMPLETE -> "Complete"
    ChargeState.NOT_PLUGGED -> "Not plugged in"
    ChargeState.SCHEDULED -> "Scheduled"
}
