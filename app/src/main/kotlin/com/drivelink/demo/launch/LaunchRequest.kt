package com.drivelink.demo.launch

import android.content.Intent
import com.drivelink.core.domain.config.DemoConfig

/**
 * The launch extras of MainActivity.
 *
 * | Extra | Values |
 * | --- | --- |
 * | `screen` | home, carcare, maps, menu, controls, climate, pin, charging, status, gallery, console, login |
 * | `scenario` | an X-Scenario name; sets the demo scenario |
 * | `vehicle` | aurora (EV), solace (gas); selects the vehicle |
 * | `dark` | true or false; absent follows the system |
 *
 * [id] is new for each intent, so a second intent with the same screen still navigates.
 */
data class LaunchRequest(
    val id: Int,
    val screen: String?,
    val scenario: String?,
    val vin: String?,
    val dark: Boolean?,
) {
    companion object {
        const val EXTRA_SCREEN = "screen"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_VEHICLE = "vehicle"
        const val EXTRA_DARK = "dark"

        const val VIN_AURORA = "DLEV26AURA0000101"
        const val VIN_SOLACE = "DLGS25SLACE000202"

        fun vinFor(vehicle: String?): String? = when (vehicle?.lowercase()) {
            "aurora" -> VIN_AURORA
            "solace" -> VIN_SOLACE
            else -> null
        }

        fun from(id: Int, intent: Intent?): LaunchRequest {
            val extras = intent?.extras
            return LaunchRequest(
                id = id,
                screen = extras?.getString(EXTRA_SCREEN)?.lowercase(),
                scenario = extras?.getString(EXTRA_SCENARIO)?.trim()?.ifEmpty { null },
                vin = vinFor(extras?.getString(EXTRA_VEHICLE)),
                dark = extras?.getString(EXTRA_DARK)?.toBooleanStrictOrNull(),
            )
        }
    }
}

/** Writes the scenario and the vehicle of [request] to the demo settings. Run before the UI loads data. */
suspend fun LaunchRequest.applyTo(config: DemoConfig) {
    if (scenario != null) config.setScenario(scenario)
    if (vin != null) config.setVin(vin)
}
