package com.drivelink.core.domain.model

import kotlinx.serialization.Serializable

/** One item of the listAlerts (GET /alerts) 200 array. */
@Serializable
data class Alert(
    val id: String,
    val vin: String? = null,
    val type: AlertType,
    val severity: AlertSeverity,
    val title: String,
    val body: String,
    /** ISO 8601, UTC. */
    val createdAt: String,
    val read: Boolean,
)

@Serializable
enum class AlertType { DOOR_OPEN, DOOR_UNLOCKED, LOW_TIRE, LOW_RANGE, CHARGE_COMPLETE, THEFT_ALARM, SERVICE_DUE, RECALL }

@Serializable
enum class AlertSeverity { INFO, WARN, CRITICAL }
