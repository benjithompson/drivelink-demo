package com.drivelink.core.network

/**
 * OkHttp request tags that carry per-call data between the call site and the interceptors.
 * Tags survive `request.newBuilder()`, so a retry keeps them.
 */

/** The X-Correlation-Id for a logical call. Set by [DriveLinkHttp.callFactory]. */
data class CorrelationTag(val id: String)

/**
 * Set by [com.drivelink.core.network.interceptor.BaseUrlInterceptor]: the profile it used and the
 * API path before the rewrite (for example `/v1/auth/token`). The inspector masks [apiKeyHeader].
 */
data class EndpointTag(val profileName: String, val apiKeyHeader: String?, val apiPath: String)

/** Attempt number of this request within the logical call (1 = first). */
data class AttemptTag(val number: Int)
