package com.drivelink.demo.nav

import kotlinx.serialization.Serializable

// Type-safe routes for Navigation Compose. One object for each screen.

@Serializable data object Login
@Serializable data object PinSetup

// Bottom tabs.
@Serializable data object Home
@Serializable data object CarCare
@Serializable data object Maps
@Serializable data object Menu

// Sub-screens.
@Serializable data object Controls
@Serializable data object Climate
@Serializable data object PinEntry
@Serializable data object Charging
@Serializable data object Status
@Serializable data object Gallery
@Serializable data object Console
@Serializable data object ChargeSchedule
@Serializable data object Trips
@Serializable data object ServiceRequest
@Serializable data object Alerts
@Serializable data object Profile
@Serializable data object Settings

/** The `screen` launch extra values and the route each one opens. */
object ScreenNames {
    fun route(name: String?): Any? = when (name) {
        "home" -> Home
        "carcare" -> CarCare
        "maps" -> Maps
        "menu" -> Menu
        "controls" -> Controls
        "climate" -> Climate
        "pin" -> PinEntry
        "charging" -> Charging
        "status" -> Status
        "gallery" -> Gallery
        "console" -> Console
        "login" -> Login
        "schedule" -> ChargeSchedule
        "trips" -> Trips
        "service" -> ServiceRequest
        "alerts" -> Alerts
        "profile" -> Profile
        "settings" -> Settings
        else -> null
    }
}
