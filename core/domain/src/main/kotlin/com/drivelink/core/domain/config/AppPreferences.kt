package com.drivelink.core.domain.config

import kotlinx.coroutines.flow.StateFlow

/** The app theme the user picked in Settings. [SYSTEM] follows the device setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** A notification type with a local on/off switch. The switches only store a choice; the demo sends no push messages. */
enum class NotificationKind { VEHICLE_ALERTS, CHARGING_UPDATES, SERVICE_REMINDERS }

/**
 * User preferences stored on the device (Settings screen). No API call is involved.
 * The StateFlows are current in memory, so the theme applies without a restart.
 */
interface AppPreferences {
    val themeMode: StateFlow<ThemeMode>

    /** The notification types that are switched on. All are on until the user changes them. */
    val enabledNotifications: StateFlow<Set<NotificationKind>>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setNotification(kind: NotificationKind, enabled: Boolean)
}
