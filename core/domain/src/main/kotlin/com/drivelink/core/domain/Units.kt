package com.drivelink.core.domain

import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.UserUnits
import kotlin.math.roundToInt

/**
 * Display units. The API sends miles and degrees Fahrenheit only.
 * The app converts for display and converts back before it sends a value.
 */
object Units {
    private const val KM_PER_MILE = 1.609344

    /** The units that apply when the user profile is not loaded. */
    val DEFAULT = UserUnits(DistanceUnit.MI, TemperatureUnit.F)

    /** Remote-start temperature range in the API, in degrees Fahrenheit. */
    const val MIN_TEMP_F = 62
    const val MAX_TEMP_F = 82

    /** Remote-start temperature range shown when the user's unit is Celsius. */
    const val MIN_TEMP_C = 17
    const val MAX_TEMP_C = 28

    fun distance(miles: Double, unit: DistanceUnit): Double = if (unit == DistanceUnit.KM) miles * KM_PER_MILE else miles

    fun distanceRounded(miles: Int, unit: DistanceUnit): Int = distance(miles.toDouble(), unit).roundToInt()

    fun distanceLabel(unit: DistanceUnit): String = if (unit == DistanceUnit.KM) "km" else "mi"

    /** Degrees Celsius for a Fahrenheit value of the API, rounded to a whole degree. */
    fun fahrenheitToCelsius(f: Int): Int = ((f - 32) * 5.0 / 9.0).roundToInt()

    /** Degrees Fahrenheit for a whole Celsius value, clamped to the API range 62..82. */
    fun celsiusToFahrenheit(c: Int): Int = (c * 9.0 / 5.0 + 32).roundToInt().coerceIn(MIN_TEMP_F, MAX_TEMP_F)
}
