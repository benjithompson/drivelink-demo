package com.drivelink.core.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drivelink.core.domain.config.NotificationKind
import com.drivelink.core.domain.config.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreAppPreferencesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val stores = mutableListOf<TestStore>()

    @After fun tearDown() = stores.forEach { it.close() }

    private fun open(): Pair<DataStoreAppPreferences, TestStore> {
        stores.forEach { it.close() }
        val store = TestStore(File(tmp.root, "prefs.preferences_pb")).also { stores += it }
        return DataStoreAppPreferences(store.dataStore) to store
    }

    @Test fun defaults_followSystemTheme_andAllNotificationsOn() {
        val (prefs, _) = open()

        assertThat(prefs.themeMode.value).isEqualTo(ThemeMode.SYSTEM)
        assertThat(prefs.enabledNotifications.value).containsExactlyElementsIn(NotificationKind.entries)
    }

    @Test fun theme_publishesAtOnce_andPersists() = runBlocking<Unit> {
        val (prefs, _) = open()
        prefs.setThemeMode(ThemeMode.DARK)

        assertThat(prefs.themeMode.value).isEqualTo(ThemeMode.DARK)
        assertThat(open().first.themeMode.value).isEqualTo(ThemeMode.DARK)
    }

    @Test fun theme_canReturnToSystem() = runBlocking<Unit> {
        val (prefs, _) = open()
        prefs.setThemeMode(ThemeMode.LIGHT)
        prefs.setThemeMode(ThemeMode.SYSTEM)

        assertThat(open().first.themeMode.value).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun unknownStoredTheme_loadsAsSystem() = runBlocking<Unit> {
        val (_, store) = open()
        store.dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }

        assertThat(open().first.themeMode.value).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test fun notifications_persistEachSwitch() = runBlocking<Unit> {
        val (prefs, _) = open()
        prefs.setNotification(NotificationKind.CHARGING_UPDATES, false)
        prefs.setNotification(NotificationKind.SERVICE_REMINDERS, false)
        prefs.setNotification(NotificationKind.SERVICE_REMINDERS, true)

        assertThat(prefs.enabledNotifications.value)
            .containsExactly(NotificationKind.VEHICLE_ALERTS, NotificationKind.SERVICE_REMINDERS)
        assertThat(open().first.enabledNotifications.value)
            .containsExactly(NotificationKind.VEHICLE_ALERTS, NotificationKind.SERVICE_REMINDERS)
    }

    @Test fun allSwitchedOff_staysOff_afterReopen() = runBlocking<Unit> {
        val (prefs, _) = open()
        NotificationKind.entries.forEach { prefs.setNotification(it, false) }

        assertThat(open().first.enabledNotifications.value).isEmpty()
    }

    @Test fun sharesTheFileWithTheOtherStores() = runBlocking<Unit> {
        val (prefs, store) = open()
        val pins = DataStorePinStore(store.dataStore)
        pins.set("1234")
        prefs.setThemeMode(ThemeMode.LIGHT)

        assertThat(pins.verify("1234")).isTrue()
        assertThat(prefs.themeMode.value).isEqualTo(ThemeMode.LIGHT)
    }
}
