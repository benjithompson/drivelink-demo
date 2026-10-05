package com.drivelink.demo.status

import com.drivelink.core.data.garage.GarageState
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.UserUnits
import com.drivelink.demo.AURORA
import com.drivelink.demo.Examples
import com.drivelink.demo.FakeCommandRepository
import com.drivelink.demo.GarageHarness
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.SOLACE
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.demo.remote.RemoteCommands
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

class StatusViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()
    private val remote = RemoteCommands(
        RunCommandUseCase(FakeCommandRepository(), TimingLog { _, _, _ -> }),
        h.garage,
        CoroutineScope(Dispatchers.Unconfined),
        2500.milliseconds,
    )

    private fun viewModel() = StatusViewModel(h.garage, remote)

    private fun StatusUiState.quick(key: String) = quickRows.first { it.key == key }
    private fun StatusUiState.full(key: String) = fullRows.first { it.key == key }

    @Test fun loadsQuickViewForTheEv() {
        val state = viewModel().state.value

        assertThat(state.hasData).isTrue()
        assertThat(state.title).isEqualTo("2026 Aurora EV")
        assertThat(state.isEv).isTrue()
        assertThat(state.openings.hood).isFalse()
        assertThat(state.openings.frontLeft).isFalse()
        assertThat(state.locked).isTrue()
        assertThat(state.climateOn).isFalse()
        assertThat(state.quick("location").value).isEqualTo("Parked in Harbor District")
        assertThat(state.quick("vehicle").value).isEqualTo("Off")
        assertThat(state.quick("tires").value).isEqualTo("Normal")
        assertThat(state.quick("climate").value).isEqualTo("Off")
        assertThat(state.quick("level").label).isEqualTo("Battery")
        assertThat(state.quick("level").value).isEqualTo("72% · 226 mi")
        assertThat(state.quick("odometer").value).isEqualTo("18,452 mi")
        assertThat(state.tires.map { it.text }).containsExactly("38 psi", "38 psi", "37 psi", "38 psi").inOrder()
        assertThat(state.updatedText).startsWith("Updated ")
        assertThat(state.stale).isFalse()
    }

    @Test fun fullList_hasEveryItem() {
        val state = viewModel().state.value

        assertThat(state.fullRows.map { it.key }).containsAtLeast(
            "location", "vehicle", "lock", "hood", "door_fl", "door_fr", "door_rl", "door_rr", "trunk",
            "window_fl", "window_fr", "window_rl", "window_rr", "climate", "tires",
            "tire_fl", "tire_fr", "tire_rl", "tire_rr", "level", "range", "charging", "odometer", "aux12v",
        )
        assertThat(state.full("lock").value).isEqualTo("Locked")
        assertThat(state.full("hood").value).isEqualTo("Closed")
        assertThat(state.full("window_fl").value).isEqualTo("Closed")
        assertThat(state.full("charging").value).isEqualTo("Charging")
        assertThat(state.fullRows.map { it.key }).containsNoDuplicates()
    }

    @Test fun doorAjar_isRed_onDiagramAndRow() {
        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.door-ajar"))

        val state = viewModel().state.value

        assertThat(state.openings.rearLeft).isTrue()
        assertThat(state.openings.frontLeft).isFalse()
        assertThat(state.locked).isFalse()
        assertThat(state.full("door_rl").value).isEqualTo("Open")
        assertThat(state.full("door_rl").tone).isEqualTo(Tone.Alert)
        assertThat(state.full("door_fl").tone).isEqualTo(Tone.Good)
        assertThat(state.full("lock").value).isEqualTo("Unlocked")
    }

    @Test fun tireLow_highlightsTheLowWheel() {
        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.tire-low"))

        val state = viewModel().state.value

        assertThat(state.quick("tires").value).isEqualTo("Low")
        assertThat(state.quick("tires").tone).isEqualTo(Tone.Alert)
        assertThat(state.tires.filter { it.low }.map { it.position }).containsExactly("rl")
        assertThat(state.tires.first { it.position == "rl" }.text).isEqualTo("24 psi")
        assertThat(state.full("tire_rl").tone).isEqualTo(Tone.Alert)
        assertThat(state.full("tire_fl").tone).isEqualTo(Tone.Normal)
    }

    @Test fun vehicleOffline_showsStaleWithAge() {
        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.vehicle-offline"))

        val state = viewModel().state.value

        assertThat(state.hasData).isTrue()
        assertThat(state.stale).isTrue()
        assertThat(state.staleAge).isNotNull()
    }

    @Test fun updatedText_usesTheAgeOfTheStatus() = runTest {
        h.garage.refresh()

        val state = statusUiState(h.garage.state.value, now = Instant.parse("2026-10-02T19:00:00Z"))

        assertThat(state.updatedText).isEqualTo("Updated 2 hr ago")
    }

    @Test fun gasCar_showsFuel_andNoChargingRow() {
        h.config.vin.value = SOLACE

        val state = viewModel().state.value

        assertThat(state.isEv).isFalse()
        assertThat(state.quick("level").label).isEqualTo("Fuel")
        assertThat(state.quick("level").value).startsWith("62% · ")
        assertThat(state.fullRows.map { it.key }).doesNotContain("charging")
        assertThat(state.full("oil").value).isEqualTo("41%")
    }

    @Test fun kilometers_convertRangeAndOdometer() {
        h.account.user = Outcome.Ok(Examples.user.copy(units = UserUnits(DistanceUnit.KM, TemperatureUnit.C)))

        val state = viewModel().state.value

        assertThat(state.quick("level").value).isEqualTo("72% · 364 km")
        assertThat(state.quick("odometer").value).isEqualTo("29,696 km")
    }

    @Test fun noWindowsInThePayload_meansNoWindowRows() {
        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.default").copy(windows = null))

        val state = viewModel().state.value

        assertThat(state.fullRows.map { it.key }.none { it.startsWith("window_") }).isTrue()
    }

    @Test fun networkError_showsErrorWithoutData_thenRetryLoads() {
        h.vehicles.status[AURORA] = Outcome.Err(AppError.Network(timeout = true, correlationId = "cid-9"))
        val vm = viewModel()

        val failed = vm.state.value
        assertThat(failed.hasData).isFalse()
        assertThat(failed.error?.correlationId).isEqualTo("cid-9")

        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.default"))
        vm.refresh()

        assertThat(vm.state.value.hasData).isTrue()
        assertThat(vm.state.value.error).isNull()
    }

    @Test fun parseError_isShown() {
        h.vehicles.status[AURORA] = Outcome.Err(AppError.Parse("rangeMi", "cid-p"))

        val state = viewModel().state.value

        assertThat(state.hasData).isFalse()
        assertThat(state.error).isInstanceOf(AppError.Parse::class.java)
    }

    @Test fun refreshError_keepsTheOldData_withTheError() {
        val vm = viewModel()
        h.vehicles.status[AURORA] = Outcome.Err(AppError.Server(500, "cid-5"))

        vm.refresh()

        val state = vm.state.value
        assertThat(state.hasData).isTrue()
        assertThat(state.error?.correlationId).isEqualTo("cid-5")
    }

    @Test fun unauthorized_endsSession() {
        h.vehicles.vehicles = Outcome.Err(AppError.Unauthorized("cid-401"))

        viewModel()

        assertThat(h.sessions.session.value).isNull()
        assertThat(h.garage.signInNotice.value).isEqualTo("Your session has expired. Sign in again.")
    }

    @Test fun rateLimitMessage_includesRetryAfter() {
        assertThat(AppError.RateLimited(30, "c").displayMessage())
            .isEqualTo("Too many requests. Try again in 30 seconds.")
        assertThat(AppError.RateLimited(null, "c").displayMessage()).isEqualTo("Too many requests. Wait and try again.")
    }

    @Test fun emptyGarage_givesEmptyState() {
        val state = statusUiState(GarageState())

        assertThat(state.hasData).isFalse()
        assertThat(state.quickRows).isEmpty()
    }
}
