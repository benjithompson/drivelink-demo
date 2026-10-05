package com.drivelink.demo.charging

import com.drivelink.core.data.garage.GarageState
import com.drivelink.core.domain.TimeAgo
import com.drivelink.core.domain.Units
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ChargeSettings
import com.drivelink.core.domain.model.ChargeState
import com.drivelink.core.domain.model.ChargerType
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.DayOfWeek
import com.drivelink.core.domain.model.Powertrain
import com.drivelink.demo.remote.CommandUi
import java.time.Instant
import java.util.Locale

/** One cell of the stat grid. The tag is `stat_<key>`, the value has the tag `stat_<key>_value`. */
data class ChargeStatUi(val key: String, val label: String, val value: String)

/** State of the last limit save, for the line under the sliders (`limits_status`). */
enum class SaveStatus { Idle, Saving, Saved, Error }

/** AC and DC limit in percent. */
data class Limits(val ac: Int, val dc: Int)

/** What the Charging screen holds beside the shared vehicle state. */
data class ChargingLocal(
    /** The limits that the user moved and the app did not save yet. Null when the sliders show the saved limits. */
    val draft: Limits? = null,
    val settingsLoading: Boolean = false,
    val settingsError: AppError? = null,
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val saveError: AppError? = null,
)

/** Everything the Charging screen draws, from API values and the user's units. */
data class ChargingUiState(
    val title: String = "",
    /** False for a gas car: the screen shows a short notice and sends no charging request. */
    val available: Boolean = true,
    val hasData: Boolean = false,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val stale: Boolean = false,
    val staleAge: String? = null,
    val levelPct: Int = 0,
    /** The limit marker on the bar: the AC or DC limit of the charger in use, else the AC limit. */
    val limitPct: Int = 80,
    val stateLabel: String = "",
    val updatedText: String = "",
    val pluggedIn: Boolean = false,
    val active: Boolean = false,
    /** A charge command runs now. The start and stop button waits. */
    val commandRunning: Boolean = false,
    val stats: List<ChargeStatUi> = emptyList(),
    /** Null until the charge settings load. */
    val limits: Limits? = null,
    val settingsLoading: Boolean = false,
    val settingsError: AppError? = null,
    val saveStatus: SaveStatus = SaveStatus.Idle,
    val saveError: AppError? = null,
    val limitsText: String = "",
    val scheduleText: String = "",
    val departureText: String = "",
) {
    /** Text for the line under the sliders. */
    val saveText: String
        get() = when (saveStatus) {
            SaveStatus.Idle -> ""
            SaveStatus.Saving -> "Saving…"
            SaveStatus.Saved -> "Saved"
            SaveStatus.Error -> "Could not save. The limits are back to the saved values. " +
                (saveError?.message.orEmpty()) + (saveError?.correlationId?.let { " (ID $it)" } ?: "")
        }.trim()
}

/** Pure mapping from the shared vehicle state to the Charging screen state. Tested without Android. */
fun chargingUiState(
    garage: GarageState,
    local: ChargingLocal,
    command: CommandUi?,
    now: Instant = Instant.now(),
): ChargingUiState {
    val vehicle = garage.selected
    val status = garage.status
    val base = ChargingUiState(
        title = vehicle?.let { "${it.year} ${it.model}" }.orEmpty(),
        available = vehicle?.powertrain != Powertrain.ICE,
        refreshing = garage.loading,
        error = garage.error,
    )
    if (!base.available) return base
    val settings = garage.chargeSettings
    val limits = local.draft ?: settings?.let { Limits(it.acTargetPct, it.dcTargetPct) }
    val settingsPart = base.copy(
        limits = limits,
        settingsLoading = local.settingsLoading,
        settingsError = local.settingsError,
        saveStatus = local.saveStatus,
        saveError = local.saveError,
        limitsText = limits?.let { "AC ${it.ac}% · DC ${it.dc}%" } ?: "",
        scheduleText = scheduleText(settings),
        departureText = departureText(settings),
    )
    if (status == null) return settingsPart
    val units = garage.user?.units ?: Units.DEFAULT
    val charging = status.charging
    val level = status.batteryPct ?: 0
    val active = charging?.active == true
    val limit = when {
        limits != null -> if (charging?.chargerType == ChargerType.DC) limits.dc else limits.ac
        else -> charging?.targetPct ?: DEFAULT_LIMIT
    }
    val age = TimeAgo.describe(status.updatedAt, now)
    val range = "${Units.distanceRounded(status.rangeMi, units.distance)} ${Units.distanceLabel(units.distance)}"
    val dash = "--"
    return settingsPart.copy(
        hasData = true,
        stale = garage.stale,
        staleAge = age,
        levelPct = level,
        limitPct = limit,
        stateLabel = stateLabel(charging?.state, charging?.chargerType),
        updatedText = "Last updated " + (age ?: "recently"),
        pluggedIn = charging?.pluggedIn == true,
        active = active,
        commandRunning = command?.running == true &&
            (command.type == CommandType.CHARGE_START || command.type == CommandType.CHARGE_STOP),
        stats = listOf(
            ChargeStatUi("battery", "Battery Level", "$level%"),
            ChargeStatUi("range", "Range", range),
            ChargeStatUi(
                "time", "Time Remaining to $limit%",
                if (active) charging.minutesToTarget?.let { "${it / 60}hr ${it % 60}min" } ?: dash else dash,
            ),
            ChargeStatUi(
                "rate", "Charging Rate",
                if (active) charging.rateKw?.let { String.format(Locale.US, "%.1f kW", it) } ?: dash else dash,
            ),
            ChargeStatUi("cost", "Cost", charging?.costUsd?.let { String.format(Locale.US, "$%.2f", it) } ?: dash),
            ChargeStatUi("energy", "Energy Added", charging?.energyAddedKwh?.let { "+" + kwh(it) + " kWh" } ?: dash),
        ),
    )
}

private const val DEFAULT_LIMIT = 80

private fun kwh(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private fun stateLabel(state: ChargeState?, charger: ChargerType?): String = when (state) {
    ChargeState.CHARGING -> if (charger == ChargerType.DC) "DC fast charging" else "AC charging"
    ChargeState.COMPLETE -> "Charge complete"
    ChargeState.SCHEDULED -> "Plugged in, not charging"
    ChargeState.NOT_PLUGGED, null -> "Not plugged in"
}

/** "Active" when a schedule is on, "Off" when all are off, "None" without a schedule. Empty before the settings load. */
private fun scheduleText(settings: ChargeSettings?): String = when {
    settings == null -> ""
    settings.schedules.isEmpty() -> "None"
    settings.schedules.any { it.enabled } -> "Active"
    else -> "Off"
}

private fun departureText(settings: ChargeSettings?): String {
    val departure = settings?.departure ?: return if (settings == null) "" else "Off"
    return if (departure.enabled) formatTime(departure.time) else "Off"
}

/** "07:30" becomes "7:30 AM". A value that is not HH:mm stays as it is. */
fun formatTime(hhmm: String): String {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return hhmm
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return hhmm
    val hour12 = if (h % 12 == 0) 12 else h % 12
    return String.format(Locale.US, "%d:%02d %s", hour12, m, if (h < 12) "AM" else "PM")
}

/** Short text for a list of days: "Every day", "Weekdays", "Weekends" or "Mon, Tue". */
fun formatDays(days: List<DayOfWeek>): String {
    val set = days.toSet()
    return when {
        set.size == 7 -> "Every day"
        set == setOf(DayOfWeek.MON, DayOfWeek.TUE, DayOfWeek.WED, DayOfWeek.THU, DayOfWeek.FRI) -> "Weekdays"
        set == setOf(DayOfWeek.SAT, DayOfWeek.SUN) -> "Weekends"
        set.isEmpty() -> "No days"
        else -> DayOfWeek.entries.filter { it in set }.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }
    }
}
