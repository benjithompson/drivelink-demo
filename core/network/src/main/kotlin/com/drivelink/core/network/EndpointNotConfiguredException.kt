package com.drivelink.core.network

import java.io.IOException

/**
 * The active profile has no usable base URL (empty, or not an http/https URL).
 * [com.drivelink.core.network.apiCall] maps it to [com.drivelink.core.domain.error.AppError.Network]
 * with a message that names the profile.
 */
class EndpointNotConfiguredException(
    val profileName: String,
    val baseUrl: String,
) : IOException(
    if (baseUrl.isBlank()) {
        "The profile \"$profileName\" has no base URL."
    } else {
        "The profile \"$profileName\" has an invalid base URL: $baseUrl"
    },
)
