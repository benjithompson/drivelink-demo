package com.drivelink.core.settings

import java.util.concurrent.atomic.AtomicReference

/**
 * External setup that arrives before the settings exist: the instrumentation arguments
 * `baseUrl` and `scenario` (DriveLinkTestRunner sets them before the Hilt component is created).
 *
 * [DataStoreDemoConfig] takes the values once, after its initial read, and applies them with
 * [DataStoreDemoConfig.applyExternal]. The values are persisted, so later instances (for example
 * a new Hilt test component) read them from the store.
 */
class PendingExternalConfig {

    data class Values(val baseUrl: String?, val scenario: String?)

    private val pending = AtomicReference<Values?>(null)

    /** The message of the last rejected value, for example an invalid URL. Null when none. */
    @Volatile
    var lastError: String? = null
        internal set

    /** Stores the values. Blank values count as absent. Nothing is stored when both are absent. */
    fun set(baseUrl: String?, scenario: String?) {
        val values = Values(baseUrl?.takeIf { it.isNotBlank() }, scenario?.takeIf { it.isNotBlank() })
        if (values.baseUrl != null || values.scenario != null) pending.set(values)
    }

    /** Returns the values and clears them, so they apply once. */
    fun take(): Values? = pending.getAndSet(null)

    companion object {
        /** The process-wide instance that the runner sets and the app's settings read. */
        val Default: PendingExternalConfig = PendingExternalConfig()
    }
}
