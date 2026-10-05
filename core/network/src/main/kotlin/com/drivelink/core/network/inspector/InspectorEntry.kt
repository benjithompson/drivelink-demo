package com.drivelink.core.network.inspector

/**
 * One HTTP attempt as the Network Inspector shows it. Secret header values are already masked.
 * Bodies are UTF-8 text, truncated to [NetworkInspector.MAX_BODY_BYTES] with [NetworkInspector.TRUNCATED_MARKER].
 */
data class InspectorEntry(
    val id: Long,
    val startedAtEpochMs: Long,
    val method: String,
    val url: String,
    val requestHeaders: List<Pair<String, String>>,
    val requestBody: String?,
    /** Null when the attempt failed without a response (transport error). */
    val status: Int?,
    val responseHeaders: List<Pair<String, String>>,
    val responseBody: String?,
    val durationMs: Long,
    /** Exception class and message for a transport error, for example `SocketTimeoutException: timeout`. */
    val error: String?,
    val correlationId: String?,
    val scenario: String?,
    val profileName: String?,
    /** 1 for the first attempt; 2 for a GET retry or a retry after a token refresh. */
    val attempt: Int,
)
