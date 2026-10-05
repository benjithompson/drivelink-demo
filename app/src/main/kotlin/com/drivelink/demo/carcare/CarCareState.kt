package com.drivelink.demo.carcare

import com.drivelink.core.domain.Units
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.format.Formatters
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.Maintenance
import com.drivelink.core.domain.model.RecallStatus
import com.drivelink.core.domain.model.ServiceCenter
import com.drivelink.core.domain.model.ServiceItemStatus
import com.drivelink.core.domain.model.UserUnits
import java.util.Locale

/** The state of the Car Care headline. It sets the text, the icon and the color. */
enum class CarCareHeadline(val text: String) {
    Good("Everything Looks Good"),
    Recall("Open Recall"),
    Due("Service Due"),
    Overdue("Service Overdue"),
}

/** One row of the maintenance list. */
data class MaintenanceItemUi(
    val id: String,
    val name: String,
    /** For example "Due at 19,500mi", "Due by 12/01/26", or null when the item has no due value. */
    val due: String?,
    val status: ServiceItemStatus,
) {
    val statusLabel: String
        get() = when (status) {
            ServiceItemStatus.UPCOMING -> "Upcoming"
            ServiceItemStatus.DUE -> "Due"
            ServiceItemStatus.OVERDUE -> "Overdue"
        }
}

/** One recall. */
data class RecallUi(
    val id: String,
    val campaign: String,
    val title: String,
    val description: String?,
    val issued: String?,
    val open: Boolean,
)

/** The preferred service center card. */
data class ServiceCenterUi(
    val id: String,
    val name: String,
    val address: String,
    val phone: String?,
    /** The phone number for a `tel:` link. */
    val dialNumber: String?,
    val distance: String?,
    val hours: String?,
    val openNow: Boolean?,
)

/** Everything the Car Care tab draws once the data is loaded. Values are in the user's units. */
data class CarCareContent(
    val headline: CarCareHeadline,
    /** For example "1 item overdue, 2 items due, 1 open recall"; null when the headline is Good. */
    val summary: String?,
    val odometer: String,
    val lastMiles: String?,
    val lastDate: String?,
    val nextMiles: String?,
    val nextDate: String?,
    /** "Based on your driving habits and conditions, your service interval is 7,500 miles." */
    val intervalText: String?,
    val items: List<MaintenanceItemUi>,
    val recalls: List<RecallUi>,
    val center: ServiceCenterUi?,
)

data class CarCareUiState(
    val content: CarCareContent? = null,
    /** The last load failed. With [content] set, the screen keeps the data and shows a banner. */
    val error: AppError? = null,
    /** First load, no data yet. */
    val loading: Boolean = true,
    /** A reload runs while data is on screen. */
    val refreshing: Boolean = false,
)

/** Pure mapping from the API document to the screen state. Tested without Android. */
fun carCareContent(maintenance: Maintenance, units: UserUnits = Units.DEFAULT): CarCareContent {
    val distance = units.distance
    val overdue = maintenance.items.count { it.status == ServiceItemStatus.OVERDUE }
    val due = maintenance.items.count { it.status == ServiceItemStatus.DUE }
    val recalls = maintenance.recalls.count { it.status == RecallStatus.OPEN }
    val headline = when {
        overdue > 0 -> CarCareHeadline.Overdue
        due > 0 -> CarCareHeadline.Due
        recalls > 0 -> CarCareHeadline.Recall
        else -> CarCareHeadline.Good
    }
    val summary = listOfNotNull(
        count(overdue, "item overdue", "items overdue"),
        count(due, "item due", "items due"),
        count(recalls, "open recall", "open recalls"),
    ).joinToString(", ").ifEmpty { null }
    return CarCareContent(
        headline = headline,
        summary = summary,
        odometer = Formatters.wholeDistance(maintenance.odometerMi, distance),
        lastMiles = maintenance.lastService?.let { Formatters.wholeDistance(it.odometerMi, distance) },
        lastDate = maintenance.lastService?.let { Formatters.shortDate(it.date) },
        nextMiles = maintenance.nextService?.let { Formatters.wholeDistance(it.odometerMi, distance) },
        nextDate = maintenance.nextService?.let { Formatters.shortDate(it.date) },
        intervalText = maintenance.intervalMi?.let {
            val shown = String.format(Locale.US, "%,d", Units.distanceRounded(it, distance))
            "Based on your driving habits and conditions, your service interval is $shown ${Formatters.distanceWord(distance)}."
        },
        items = maintenance.items.map { item ->
            MaintenanceItemUi(
                id = item.id,
                name = item.name,
                due = dueText(item.dueMi, item.dueDate, distance),
                status = item.status,
            )
        },
        recalls = maintenance.recalls.map {
            RecallUi(
                id = it.id,
                campaign = it.campaign,
                title = it.title,
                description = it.description,
                issued = it.issuedDate?.let(Formatters::shortDate),
                open = it.status == RecallStatus.OPEN,
            )
        },
        center = maintenance.preferredServiceCenter?.let { centerUi(it, distance) },
    )
}

private fun count(n: Int, one: String, many: String): String? = when (n) {
    0 -> null
    1 -> "1 $one"
    else -> "$n $many"
}

private fun dueText(dueMi: Int?, dueDate: String?, distance: DistanceUnit): String? {
    val parts = listOfNotNull(
        dueMi?.let { "at " + Formatters.wholeDistance(it, distance) },
        dueDate?.let { Formatters.shortDate(it)?.let { date -> "by $date" } },
    )
    return if (parts.isEmpty()) null else "Due " + parts.joinToString(" or ")
}

private fun centerUi(center: ServiceCenter, distance: DistanceUnit) = ServiceCenterUi(
    id = center.id,
    name = center.name,
    address = center.address,
    phone = center.phone,
    dialNumber = center.phone?.let(Formatters::dialNumber)?.ifEmpty { null },
    distance = center.distanceMi?.let { Formatters.nearbyDistance(it, distance) },
    hours = center.hours,
    openNow = center.openNow,
)
