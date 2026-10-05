package com.drivelink.core.network

import java.util.UUID

/**
 * Carries the correlation id from [apiCall] to the OkHttp request.
 *
 * Mechanism: [apiCall] generates a UUID and installs it in this ThreadLocal as a coroutine
 * context element (`asContextElement`). Retrofit creates the OkHttp call synchronously on the
 * calling coroutine's thread, so [DriveLinkHttp.callFactory] reads the value there and stores it
 * as a [CorrelationTag] on the request. The CorrelationInterceptor then sends it as
 * X-Correlation-Id. Because the same id is known to [apiCall], transport errors (no response)
 * also carry the exact id that the request used.
 */
internal object CorrelationContext {
    val current: ThreadLocal<String?> = ThreadLocal()
}

/** A new random (version 4) UUID string. */
fun newCorrelationId(): String = UUID.randomUUID().toString()
