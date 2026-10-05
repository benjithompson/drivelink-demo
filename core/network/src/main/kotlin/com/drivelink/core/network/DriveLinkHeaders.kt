package com.drivelink.core.network

/** Request header names that the app sends (api/openapi.yaml). */
object DriveLinkHeaders {
    const val AUTHORIZATION = "Authorization"
    const val SCENARIO = "X-Scenario"
    const val CORRELATION_ID = "X-Correlation-Id"
    const val CLIENT_VERSION = "X-Client-Version"
    const val POLL_ATTEMPT = "X-Poll-Attempt"
    const val RETRY_AFTER = "Retry-After"

    /** The scenario name that sends no X-Scenario header. */
    const val DEFAULT_SCENARIO = "default"
}
