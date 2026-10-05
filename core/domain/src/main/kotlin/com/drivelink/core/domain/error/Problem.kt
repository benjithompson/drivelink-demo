package com.drivelink.core.domain.error

import kotlinx.serialization.Serializable

/** RFC 9457 problem body (components.schemas.Problem). [code] stays a String so unknown codes still parse. */
@Serializable
data class Problem(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
    val code: String,
    val correlationId: String? = null,
)
