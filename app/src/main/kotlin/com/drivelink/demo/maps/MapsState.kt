package com.drivelink.demo.maps

import com.drivelink.core.data.garage.GarageState
import com.drivelink.core.domain.TimeAgo
import com.drivelink.core.domain.Units
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.Location
import java.time.Instant
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The fixed point that the Maps card measures the distance from (D-40). The app asks for no
 * location permission, so it cannot know where the phone is. The point lies 0.3 mi north of the
 * Aurora's sample position, so the demo shows "0.3 mi" for the Aurora and about 1.4 mi for the Solace.
 */
object DemoOrigin {
    const val LAT = 33.6232
    const val LON = -117.9298
}

private const val EARTH_RADIUS_MI = 3958.8

/** Great-circle distance between two points, in miles (haversine formula). */
fun distanceMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_MI * asin(sqrt(a))
}

/** The location card of the selected vehicle, as text. */
data class VehicleLocationUi(
    val lat: Double,
    val lon: Double,
    val address: String,
    /** For example "0.3 mi away" or "0.5 km away", from the user's units (D-13). */
    val distance: String,
    /** For example "±8 m". The API sends meters only. */
    val accuracy: String,
    /** For example "Updated 3 min ago". */
    val updated: String,
)

/** What the Maps screen draws. */
data class MapsUiState(
    /** For example "2026 Aurora EV"; empty before the vehicle list loads. */
    val vehicleTitle: String = "",
    val location: VehicleLocationUi? = null,
    /** A location call or the vehicle list is loading. */
    val loading: Boolean = true,
    /** The last load failed. With [location] set, the card shows older data and a banner. */
    val error: AppError? = null,
    /** The scenario chip (drawn by the app shell) covers the bottom left corner. */
    val scenarioChipVisible: Boolean = false,
)

/** The result of the last getVehicleLocation call, for [vin]. */
data class LocationLoad(
    val vin: String? = null,
    val location: Location? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
)

/** Pure mapping from the shared state and the location call to the Maps state. Tested without Android. */
fun mapsUiState(
    garage: GarageState,
    load: LocationLoad,
    scenarioChipVisible: Boolean = false,
    now: Instant = Instant.now(),
): MapsUiState {
    val vehicle = garage.selected
    val title = vehicle?.let { "${it.year} ${it.model}" }.orEmpty()
    // A result for another vehicle is not shown (the user switched the vehicle on Home).
    val own = load.takeIf { vehicle != null && it.vin == vehicle.vin }
    val units = garage.user?.units ?: Units.DEFAULT
    val location = own?.location?.let { loc ->
        val miles = distanceMiles(DemoOrigin.LAT, DemoOrigin.LON, loc.lat, loc.lon)
        VehicleLocationUi(
            lat = loc.lat,
            lon = loc.lon,
            address = loc.address ?: loc.locality ?: "Address unavailable",
            distance = formatDistance(miles, units.distance) + " away",
            accuracy = "±${loc.accuracyM} m",
            updated = TimeAgo.describe(loc.updatedAt, now)?.let { "Updated $it" } ?: "Updated recently",
        )
    }
    return MapsUiState(
        vehicleTitle = title,
        location = location,
        loading = if (vehicle == null) garage.loading else own?.loading ?: true,
        error = if (vehicle == null) garage.error else own?.error,
        scenarioChipVisible = scenarioChipVisible,
    )
}

/** One decimal place, in the unit of the user: "0.3 mi" or "0.5 km". */
fun formatDistance(miles: Double, unit: DistanceUnit): String =
    String.format(Locale.US, "%.1f", Units.distance(miles, unit)) + " " + Units.distanceLabel(unit)
