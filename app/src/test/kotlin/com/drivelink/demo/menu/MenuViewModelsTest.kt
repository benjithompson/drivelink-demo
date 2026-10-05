package com.drivelink.demo.menu

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.AppPreferences
import com.drivelink.core.domain.config.NotificationKind
import com.drivelink.core.domain.config.ThemeMode
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.UserUnits
import com.drivelink.demo.Examples
import com.drivelink.demo.FakePinStore
import com.drivelink.demo.GarageHarness
import com.drivelink.demo.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** In-memory [AppPreferences]. The real one is tested in :core:settings. */
class FakeAppPreferences : AppPreferences {
    override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    override val enabledNotifications: MutableStateFlow<Set<NotificationKind>> = MutableStateFlow(NotificationKind.entries.toSet())
    override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
    override suspend fun setNotification(kind: NotificationKind, enabled: Boolean) {
        enabledNotifications.value = if (enabled) enabledNotifications.value + kind else enabledNotifications.value - kind
    }
}

private val DebugBuild = AppBuildInfo(mockBaseUrl = "", clientVersion = "android/0.1.0", debug = true)

class MenuViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()

    @Test fun beforeTheUserLoads_theHeaderUsesTheEmailOfTheSession() {
        val vm = MenuViewModel(h.garage, h.sessions, DebugBuild)

        assertThat(vm.state.value.name).isEmpty()
        assertThat(vm.state.value.email).isEqualTo("alex.rivera@drivelink.test")
    }

    @Test fun afterTheLoad_theHeaderShowsNameAndEmailOfTheAccount() = runTest {
        val vm = MenuViewModel(h.garage, h.sessions, DebugBuild)
        backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect { } }
        h.garage.refresh()

        assertThat(vm.state.value.name).isEqualTo("Alex Rivera")
        assertThat(vm.state.value.email).isEqualTo("alex.rivera@drivelink.test")
    }

    @Test fun aboutVersion_comesFromTheBuildValues() {
        assertThat(DebugBuild.versionText()).isEqualTo("0.1.0 (debug)")
        assertThat(DebugBuild.copy(debug = false).versionText()).isEqualTo("0.1.0")
        assertThat(MenuViewModel(h.garage, h.sessions, DebugBuild).state.value.version).isEqualTo("0.1.0 (debug)")
    }

    @Test fun signOut_clearsTheSession() {
        MenuViewModel(h.garage, h.sessions, DebugBuild).signOut()

        assertThat(h.auth.logouts).isEqualTo(1)
        assertThat(h.sessions.session.value).isNull()
    }

    @Test fun initials() {
        assertThat(initialsOf("Alex Rivera", "")).isEqualTo("AR")
        assertThat(initialsOf("  alex  m  rivera ", "")).isEqualTo("AR")
        assertThat(initialsOf("Alex", "")).isEqualTo("A")
        assertThat(initialsOf("", "zed@drivelink.test")).isEqualTo("Z")
        assertThat(initialsOf("", "")).isEqualTo("D")
    }
}

class ProfileViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()

    private fun viewModel() = ProfileViewModel(h.account, h.garage)

    @Test fun loadsTheAccountAndTheSelectedVehicle() {
        val state = viewModel().state.value

        assertThat(state.loading).isFalse()
        assertThat(state.error).isNull()
        assertThat(state.user?.name).isEqualTo("Alex Rivera")
        assertThat(state.user?.email).isEqualTo("alex.rivera@drivelink.test")
        assertThat(state.vehicle?.title).isEqualTo("2026 Aurora EV")
        assertThat(state.vehicle?.vin).isEqualTo("DLEV26AURA0000101")
    }

    @Test fun error_showsMessageAndCorrelationId_thenRetryLoads() {
        h.account.user = Outcome.Err(AppError.Server(500, "cid-me"))
        val vm = viewModel()

        assertThat(vm.state.value.user).isNull()
        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.error?.correlationId).isEqualTo("cid-me")

        h.account.user = Outcome.Ok(Examples.user)
        vm.refresh()

        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.user?.name).isEqualTo("Alex Rivera")
    }

    @Test fun unauthorized_endsTheSession() {
        h.account.user = Outcome.Err(AppError.Unauthorized("cid-401"))
        viewModel()

        assertThat(h.auth.logouts).isEqualTo(1)
        assertThat(h.sessions.session.value).isNull()
    }

    @Test fun unitsText() {
        assertThat(UserUnits(DistanceUnit.MI, TemperatureUnit.F).describe()).isEqualTo("Miles (mi), Fahrenheit (°F)")
        assertThat(UserUnits(DistanceUnit.KM, TemperatureUnit.C).describe()).isEqualTo("Kilometers (km), Celsius (°C)")
    }
}

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()
    private val prefs = FakeAppPreferences()
    private val pins = FakePinStore("1234")

    private fun viewModel(pinStore: FakePinStore = pins) = SettingsViewModel(prefs, pinStore, h.garage, DebugBuild)

    private fun SettingsViewModel.type(pin: String) = pin.forEach { pinDigit(it - '0') }

    @Test fun theme_isStoredAndShown() {
        val vm = viewModel()
        assertThat(vm.state.value.theme).isEqualTo(ThemeMode.SYSTEM)

        vm.setTheme(ThemeMode.DARK)

        assertThat(prefs.themeMode.value).isEqualTo(ThemeMode.DARK)
        assertThat(vm.state.value.theme).isEqualTo(ThemeMode.DARK)
    }

    @Test fun units_showTheAccountUnitsReadOnly() = runTest {
        h.account.user = Outcome.Ok(Examples.user.copy(units = UserUnits(DistanceUnit.KM, TemperatureUnit.C)))
        val vm = viewModel()
        assertThat(vm.state.value.unitsAreDefault).isTrue()

        h.garage.refresh()

        assertThat(vm.state.value.unitsAreDefault).isFalse()
        assertThat(vm.state.value.units).isEqualTo("Kilometers (km), Celsius (°C)")
    }

    @Test fun notifications_switchIndividually() {
        val vm = viewModel()
        vm.setNotification(NotificationKind.CHARGING_UPDATES, false)

        assertThat(vm.state.value.notifications)
            .containsExactly(NotificationKind.VEHICLE_ALERTS, NotificationKind.SERVICE_REMINDERS)
    }

    @Test fun version_showsTheBuildVersion() {
        assertThat(viewModel().state.value.version).isEqualTo("0.1.0 (debug)")
    }

    @Test fun changePin_happyPath_storesTheNewPin() {
        val vm = viewModel()
        vm.startChangePin()
        assertThat(vm.state.value.changePin?.step).isEqualTo(PinStep.CURRENT)

        vm.type("1234")
        assertThat(vm.state.value.changePin?.step).isEqualTo(PinStep.NEW)
        vm.type("4321")
        assertThat(vm.state.value.changePin?.step).isEqualTo(PinStep.CONFIRM)
        vm.type("4321")

        assertThat(vm.state.value.changePin).isNull()
        assertThat(vm.state.value.pinChanged).isTrue()
        assertThat(pins.verify("4321")).isTrue()
        assertThat(pins.verify("1234")).isFalse()
    }

    @Test fun changePin_wrongCurrentPin_showsErrorAndStaysOnTheStep() {
        val vm = viewModel()
        vm.startChangePin()
        vm.type("9999")

        val pin = vm.state.value.changePin
        assertThat(pin?.step).isEqualTo(PinStep.CURRENT)
        assertThat(pin?.error).isEqualTo("The PIN is incorrect.")
        assertThat(pin?.entered).isEmpty()
        assertThat(pins.verify("1234")).isTrue()
    }

    @Test fun changePin_mismatch_restartsAtTheNewStep_andKeepsTheOldPin() {
        val vm = viewModel()
        vm.startChangePin()
        vm.type("1234")
        vm.type("4321")
        vm.type("1111")

        val pin = vm.state.value.changePin
        assertThat(pin?.step).isEqualTo(PinStep.NEW)
        assertThat(pin?.error).isEqualTo("The PINs do not match. Start again.")
        assertThat(pins.verify("1234")).isTrue()
        assertThat(vm.state.value.pinChanged).isFalse()
    }

    @Test fun changePin_theSamePin_isRejected() {
        val vm = viewModel()
        vm.startChangePin()
        vm.type("1234")
        vm.type("1234")

        assertThat(vm.state.value.changePin?.step).isEqualTo(PinStep.NEW)
        assertThat(vm.state.value.changePin?.error).contains("different")
    }

    @Test fun changePin_withoutAStoredPin_startsAtTheNewStep() {
        val empty = FakePinStore()
        val vm = viewModel(empty)
        vm.startChangePin()
        assertThat(vm.state.value.changePin?.step).isEqualTo(PinStep.NEW)

        vm.type("2468")
        vm.type("2468")

        assertThat(empty.verify("2468")).isTrue()
    }

    @Test fun changePin_deleteAndCancel() {
        val vm = viewModel()
        vm.startChangePin()
        vm.pinDigit(1)
        vm.pinDigit(2)
        assertThat(vm.state.value.changePin?.entered).isEqualTo("12")

        vm.pinDelete()
        assertThat(vm.state.value.changePin?.entered).isEqualTo("1")

        vm.cancelChangePin()
        assertThat(vm.state.value.changePin).isNull()
        assertThat(pins.verify("1234")).isTrue()
    }

    @Test fun startingChangePinAgain_clearsTheDoneMessage() {
        val vm = viewModel()
        vm.startChangePin()
        vm.type("1234")
        vm.type("5555")
        vm.type("5555")
        assertThat(vm.state.value.pinChanged).isTrue()

        vm.startChangePin()

        assertThat(vm.state.value.pinChanged).isFalse()
    }

    @Test fun aFifthDigitStartsTheNextStep() {
        val vm = viewModel()
        vm.startChangePin()
        vm.type("12345")

        assertThat(vm.state.value.changePin?.step).isEqualTo(PinStep.NEW)
        assertThat(vm.state.value.changePin?.entered).isEqualTo("5")
    }
}
