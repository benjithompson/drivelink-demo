package com.drivelink.core.domain.format

import com.drivelink.core.domain.Units
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.EfficiencyUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Text for values of the API (Car Care, Trips). The API sends miles only (D-13);
 * these functions convert for the user's distance unit. Pure functions, no Android.
 */
object Formatters {
    private const val KM_PER_MILE = 1.609344

    /** Liters per 100 km for 1 mile per gallon is 235.215 divided by the mpg value (US gallon). */
    private const val L_PER_100KM_X_MPG = 235.215

    private val shortDate = DateTimeFormatter.ofPattern("MM/dd/yy", Locale.US)

    /** Whole distance with grouping and no space, for example "18,452mi" or "29,694km". */
    fun wholeDistance(miles: Int, unit: DistanceUnit): String =
        String.format(Locale.US, "%,d", Units.distanceRounded(miles, unit)) + Units.distanceLabel(unit)

    /** Distance with one decimal and a space, for example "12.4 mi" or "20.0 km". */
    fun tripDistance(miles: Double, unit: DistanceUnit): String =
        String.format(Locale.US, "%.1f", Units.distance(miles, unit)) + " " + Units.distanceLabel(unit)

    /** The unit as a word, for sentences: "miles" or "kilometers". */
    fun distanceWord(unit: DistanceUnit): String = if (unit == DistanceUnit.KM) "kilometers" else "miles"

    /** A `yyyy-MM-dd` date as `MM/dd/yy`, for example "06/12/26". Null when [iso] is not a date. */
    fun shortDate(iso: String): String? = try {
        LocalDate.parse(iso).format(shortDate)
    } catch (e: DateTimeParseException) {
        null
    }

    /** "26 min", "1 hr 5 min" or "2 hr". */
    fun duration(minutes: Int): String {
        if (minutes < 60) return "$minutes min"
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "$h hr" else "$h hr $m min"
    }

    /**
     * Trip efficiency. EV: "3.9 mi/kWh" or "6.3 km/kWh". Gas car: "34.2 MPG" or "6.9 L/100 km".
     * Null when the trip has no value or no unit.
     */
    fun efficiency(value: Double?, unit: EfficiencyUnit?, distance: DistanceUnit): String? {
        if (value == null || unit == null) return null
        return when (unit) {
            EfficiencyUnit.MI_PER_KWH ->
                if (distance == DistanceUnit.KM) {
                    String.format(Locale.US, "%.1f", value * KM_PER_MILE) + " km/kWh"
                } else {
                    String.format(Locale.US, "%.1f", value) + " mi/kWh"
                }
            EfficiencyUnit.MPG ->
                if (distance == DistanceUnit.KM && value > 0) {
                    String.format(Locale.US, "%.1f", L_PER_100KM_X_MPG / value) + " L/100 km"
                } else {
                    String.format(Locale.US, "%.1f", value) + " MPG"
                }
        }
    }

    /** Digits and a leading plus sign of a phone number, for a `tel:` link. "+1 555 0142" gives "+15550142". */
    fun dialNumber(phone: String): String = phone.filterIndexed { i, ch -> ch.isDigit() || (i == 0 && ch == '+') }

    /** Rounds a mile value for display, for example the distance to a service center: "3.1mi" or "5.0km". */
    fun nearbyDistance(miles: Double, unit: DistanceUnit): String {
        val shown = Units.distance(miles, unit)
        return String.format(Locale.US, "%.1f", (shown * 10).roundToInt() / 10.0) + Units.distanceLabel(unit)
    }
}

/**
 * The message a screen shows for [this]. A rate-limit error with a Retry-After value adds the wait,
 * for example "Too many requests. Try again in 30 seconds."
 */
fun AppError.displayMessage(): String = when {
    this is AppError.RateLimited && retryAfterSec != null ->
        "Too many requests. Try again in $retryAfterSec seconds."
    else -> message
}

/** [displayMessage] plus the correlation id, for example "Something went wrong. (ID 7f3c)". */
fun AppError.displayMessageWithId(): String = displayMessage() + (correlationId?.let { " (ID $it)" } ?: "")
