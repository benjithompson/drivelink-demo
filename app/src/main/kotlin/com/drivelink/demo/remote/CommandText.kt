package com.drivelink.demo.remote

import com.drivelink.core.domain.model.CommandReason
import com.drivelink.core.domain.model.CommandType

/** Human text for command types and failure reasons. */
object CommandText {
    fun label(type: CommandType): String = when (type) {
        CommandType.LOCK -> "Lock"
        CommandType.UNLOCK -> "Unlock"
        CommandType.START -> "Remote Start"
        CommandType.STOP -> "Stop"
        CommandType.HORN_LIGHTS -> "Horn & Lights"
        CommandType.LIGHTS -> "Lights"
        CommandType.CHARGE_START -> "Start charge"
        CommandType.CHARGE_STOP -> "Stop charge"
    }

    fun reason(reason: CommandReason?): String = when (reason) {
        CommandReason.DOOR_OPEN -> "A door is open. Close all doors and try again."
        CommandReason.HOOD_OPEN -> "The hood is open. Close it and try again."
        CommandReason.ENGINE_RUNNING -> "The engine is already running."
        CommandReason.KEY_IN_VEHICLE -> "The key is inside the vehicle. Take it out and try again."
        CommandReason.LOW_BATTERY -> "The battery is too low for this command."
        CommandReason.WAKING -> "The vehicle is still waking up. Try again in a moment."
        CommandReason.UNKNOWN, null -> "The vehicle could not complete the command."
    }
}
