package com.drivelink.core.domain.config

import kotlinx.coroutines.flow.StateFlow

/**
 * The local 4-digit PIN. The user sets it once, after the first sign-in. The app checks the PIN
 * on the device before each remote command, then sends it in the command body.
 * The store keeps a salted hash, not the PIN.
 */
interface PinStore {
    /** True when a PIN is set. Current in memory; the value from storage is read at creation. */
    val hasPin: StateFlow<Boolean>

    /** @throws IllegalArgumentException when [pin] is not exactly 4 digits. */
    suspend fun set(pin: String)

    /** True when [pin] matches. False when no PIN is set. */
    fun verify(pin: String): Boolean

    suspend fun clear()
}
