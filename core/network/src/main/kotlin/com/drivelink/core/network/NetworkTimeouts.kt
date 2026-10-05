package com.drivelink.core.network

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * OkHttp timeouts (D-16). [connect], [read] and [write] apply to each attempt. [call] is the
 * outer bound for the whole logical call, so one GET retry after a timeout still fits.
 */
data class NetworkTimeouts(
    val connect: Duration = 10.seconds,
    val read: Duration = 15.seconds,
    val write: Duration = 15.seconds,
    val call: Duration = 35.seconds,
)
