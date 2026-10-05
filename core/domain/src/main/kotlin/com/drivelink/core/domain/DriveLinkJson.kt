package com.drivelink.core.domain

import kotlinx.serialization.json.Json

/**
 * The one Json configuration for the network layer, the contract test and profile export.
 * Unknown fields are ignored (additive API changes, D-08). Absent optional fields use defaults.
 */
val DriveLinkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
