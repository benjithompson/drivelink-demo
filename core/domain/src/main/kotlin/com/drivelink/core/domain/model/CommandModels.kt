package com.drivelink.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class CommandType { LOCK, UNLOCK, START, STOP, HORN_LIGHTS, LIGHTS, CHARGE_START, CHARGE_STOP }

/** Body of sendCommand (POST /vehicles/{vin}/commands). */
@Serializable
data class CommandRequest(
    val type: CommandType,
    /** The user's 4-digit PIN. */
    val pin: String,
    /** Climate options for START. */
    val params: ClimateParams? = null,
)

/** 202 body of sendCommand and 200 body of getCommand (GET /commands/{commandId}). */
@Serializable
data class Command(
    val commandId: String,
    val type: CommandType,
    val status: CommandStatus,
    /** Sub-state while PENDING (WAKING), or the cause when FAILED. */
    val reason: CommandReason? = null,
    /** ISO 8601, UTC. */
    val createdAt: String,
    /** ISO 8601, UTC. */
    val updatedAt: String,
)

@Serializable
enum class CommandStatus { QUEUED, PENDING, SUCCEEDED, FAILED }

@Serializable
enum class CommandReason { WAKING, DOOR_OPEN, HOOD_OPEN, ENGINE_RUNNING, KEY_IN_VEHICLE, LOW_BATTERY, UNKNOWN }
