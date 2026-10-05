package com.drivelink.demo.trips

import com.drivelink.core.domain.Units
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.format.Formatters
import com.drivelink.core.domain.model.Trip
import com.drivelink.core.domain.model.UserUnits
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** One trip row. The API has no start or end place, so a row shows times, duration, distance and efficiency. */
data class TripRowUi(
    val id: String,
    /** For example "2:05 PM – 2:31 PM". */
    val time: String,
    /** For example "26 min". */
    val duration: String,
    /** For example "12.4 mi". */
    val distance: String,
    /** For example "3.9 mi/kWh" or "34.2 MPG"; null when the trip has no value. */
    val efficiency: String?,
)

/** The trips of one calendar day (device time zone). [key] is `yyyy-MM-dd`. */
data class TripDayUi(val key: String, val title: String, val trips: List<TripRowUi>)

data class TripsSummaryUi(val count: Int, val countText: String, val distanceText: String)

data class TripsUiState(
    val summary: TripsSummaryUi? = null,
    val days: List<TripDayUi> = emptyList(),
    /** The list loaded and has no trip. */
    val empty: Boolean = false,
    /** The last load failed. With data on screen the screen keeps the data and shows a banner. */
    val error: AppError? = null,
    /** First load, no data yet. */
    val loading: Boolean = true,
    /** A reload runs while data is on screen. */
    val refreshing: Boolean = false,
) {
    val hasData: Boolean get() = summary != null
}

private val TimeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val DayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

/** Pure mapping from the trips of the API to the screen state. Tested without Android. */
fun tripsContent(
    trips: List<Trip>,
    units: UserUnits = Units.DEFAULT,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now(),
): Pair<TripsSummaryUi, List<TripDayUi>> {
    val today = now.atZone(zone).toLocalDate()
    val rows = trips.mapNotNull { trip ->
        val start = parse(trip.startedAt)?.atZone(zone) ?: return@mapNotNull null
        val end = parse(trip.endedAt)?.atZone(zone)
        start to TripRowUi(
            id = trip.id,
            time = start.format(TimeFormat) + (end?.let { " – " + it.format(TimeFormat) } ?: ""),
            duration = Formatters.duration(trip.durationMin),
            distance = Formatters.tripDistance(trip.distanceMi, units.distance),
            efficiency = Formatters.efficiency(trip.efficiency, trip.efficiencyUnit, units.distance),
        )
    }.sortedByDescending { it.first.toInstant() }
    val days = rows.groupBy { it.first.toLocalDate() }.map { (day, entries) ->
        TripDayUi(day.toString(), dayTitle(day, today), entries.map { it.second })
    }
    val count = trips.size
    val summary = TripsSummaryUi(
        count = count,
        countText = if (count == 1) "1 trip" else "$count trips",
        distanceText = Formatters.tripDistance(trips.sumOf { it.distanceMi }, units.distance),
    )
    return summary to days
}

private fun dayTitle(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DayFormat)
}

private fun parse(iso: String): Instant? = try {
    Instant.parse(iso)
} catch (e: DateTimeParseException) {
    null
}
