package com.drivelink.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.drivelink.core.domain.config.AppPreferences
import com.drivelink.core.domain.config.NotificationKind
import com.drivelink.core.domain.config.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppPreferences] in the settings [DataStore] (D-24: one instance for the process).
 * The constructor reads the stored values once, blocking, so the first frame already uses the
 * saved theme. A write publishes the new value before it returns.
 *
 * An unknown stored theme name loads as [ThemeMode.SYSTEM]. Unknown notification names are ignored.
 */
@Singleton
class DataStoreAppPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppPreferences {

    private val mutex = Mutex()
    private val initial = readBlocking()

    private val _theme = MutableStateFlow(themeOf(initial))
    private val _notifications = MutableStateFlow(notificationsOf(initial))

    override val themeMode: StateFlow<ThemeMode> = _theme.asStateFlow()
    override val enabledNotifications: StateFlow<Set<NotificationKind>> = _notifications.asStateFlow()

    override suspend fun setThemeMode(mode: ThemeMode) {
        mutex.withLock {
            dataStore.edit { it[KEY_THEME] = mode.name }
            _theme.value = mode
        }
    }

    override suspend fun setNotification(kind: NotificationKind, enabled: Boolean) {
        mutex.withLock {
            val next = if (enabled) _notifications.value + kind else _notifications.value - kind
            dataStore.edit { it[KEY_NOTIFICATIONS] = next.map(NotificationKind::name).toSet() }
            _notifications.value = next
        }
    }

    private fun readBlocking(): Preferences = try {
        runBlocking { dataStore.data.first() }
    } catch (e: IOException) {
        emptyPreferences()
    }

    private fun themeOf(prefs: Preferences): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs[KEY_THEME] } ?: ThemeMode.SYSTEM

    private fun notificationsOf(prefs: Preferences): Set<NotificationKind> {
        val stored = prefs[KEY_NOTIFICATIONS] ?: return NotificationKind.entries.toSet()
        return NotificationKind.entries.filter { it.name in stored }.toSet()
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_NOTIFICATIONS = stringSetPreferencesKey("notifications_enabled")
    }
}
