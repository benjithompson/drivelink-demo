package com.drivelink.core.domain.config

/**
 * The scenario catalog (api/scenarios.yaml), in catalog order. The app sends the name as
 * X-Scenario; [DEFAULT] sends no header. A unit test checks the list against the catalog.
 */
object DemoScenarios {
    const val DEFAULT = "default"

    val ALL: List<String> = listOf(
        DEFAULT,
        "fast",
        "slow-vehicle",
        "vehicle-asleep",
        "vehicle-offline",
        "command-fails",
        "command-timeout",
        "low-battery",
        "door-ajar",
        "tire-low",
        "auth-expired",
        "rate-limited",
        "server-error",
        "bad-payload",
    )
}
