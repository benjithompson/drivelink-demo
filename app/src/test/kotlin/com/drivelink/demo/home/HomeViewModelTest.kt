package com.drivelink.demo.home

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.UserUnits
import com.drivelink.demo.AURORA
import com.drivelink.demo.Examples
import com.drivelink.demo.FakeCommandRepository
import com.drivelink.demo.GarageHarness
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.SOLACE
import com.drivelink.demo.remote.RemoteCommands
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()
    private val remote = RemoteCommands(
        RunCommandUseCase(FakeCommandRepository(), TimingLog { _, _, _ -> }),
        h.garage,
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
        2500.milliseconds,
    )

    private fun viewModel() = HomeViewModel(h.garage, remote)

    @Test fun loadsVehicleStatusAndRows() {
        val state = viewModel().state.value

        assertThat(state.error).isNull()
        assertThat(state.title).isEqualTo("2026 Aurora EV")
        assertThat(state.trim).isEqualTo("Limited AWD · Long Range")
        assertThat(state.isEv).isTrue()
        assertThat(state.level).isEqualTo(72)
        assertThat(state.range).isEqualTo(226)
        assertThat(state.rangeUnit).isEqualTo("mi")
        assertThat(state.locked).isTrue()
        assertThat(state.lowLevel).isFalse()
        assertThat(state.stale).isFalse()
        assertThat(state.chip?.label).isEqualTo("Charging")
        assertThat(state.locationText).isEqualTo("Harbor District")
        assertThat(state.tripText).isEqualTo("Last trip 12.4 mi")
        assertThat(state.vehicleStatusText).isEqualTo("Parked")
        assertThat(state.healthText).isEqualTo("All Systems Normal")
        assertThat(state.unreadAlerts).isEqualTo(Examples.alerts.count { !it.read })
        assertThat(state.vehicles.map { it.vin }).containsExactly(AURORA, SOLACE).inOrder()
        assertThat(h.config.vin.value).isEqualTo(AURORA)
    }

    @Test fun gasVehicle_showsFuel_andNoChargeChip() {
        val vm = viewModel()
        vm.selectVehicle(SOLACE)

        val state = vm.state.value
        assertThat(state.isEv).isFalse()
        assertThat(state.title).isEqualTo("2025 Solace")
        assertThat(state.level).isEqualTo(Examples.status("200.default-ice").fuelPct)
        assertThat(state.chip?.label).isEqualTo("Doors Locked")
        assertThat(h.config.vin.value).isEqualTo(SOLACE)
    }

    @Test fun savedVehicle_isRestored() {
        h.config.vin.value = SOLACE

        assertThat(viewModel().state.value.title).isEqualTo("2025 Solace")
    }

    @Test fun vehicleOffline_marksStatusStale_withAge() {
        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.vehicle-offline"))

        val state = viewModel().state.value
        assertThat(state.stale).isTrue()
        assertThat(state.staleAge).isNotNull()
        assertThat(state.hasData).isTrue()
    }

    @Test fun lowBattery_isRedWithWarning() {
        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.low-battery"))

        val state = viewModel().state.value
        assertThat(state.level).isEqualTo(8)
        assertThat(state.range).isEqualTo(19)
        assertThat(state.lowLevel).isTrue()
    }

    @Test fun doorAjar_andTireLow_showInRows() {
        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.door-ajar"))
        val door = viewModel().state.value
        assertThat(door.locked).isFalse()
        assertThat(door.doorOpen).isTrue()
        assertThat(door.vehicleStatusText).isEqualTo("Door Open")

        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.tire-low"))
        val tire = HomeViewModel(GarageHarness().also { it.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.tire-low")) }.garage, remote).state.value
        assertThat(tire.tireLow).isTrue()
        assertThat(tire.healthText).isEqualTo("Tire Pressure Low")
    }

    @Test fun kilometers_convertRangeAndTrip() {
        h.account.user = Outcome.Ok(Examples.user.copy(units = UserUnits(DistanceUnit.KM, TemperatureUnit.C)))

        val state = viewModel().state.value
        assertThat(state.rangeUnit).isEqualTo("km")
        assertThat(state.range).isEqualTo(364)
        assertThat(state.tripText).isEqualTo("Last trip 20.0 km")
    }

    @Test fun networkError_showsError_thenRetryLoads() {
        h.vehicles.status[AURORA] = Outcome.Err(AppError.Network(timeout = true, correlationId = "cid-9"))
        val vm = viewModel()

        val failed = vm.state.value
        assertThat(failed.hasData).isFalse()
        assertThat(failed.error).isInstanceOf(AppError.Network::class.java)
        assertThat(failed.error?.correlationId).isEqualTo("cid-9")
        assertThat(failed.title).isEqualTo("2026 Aurora EV")

        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.default"))
        vm.refresh()

        val loaded = vm.state.value
        assertThat(loaded.error).isNull()
        assertThat(loaded.level).isEqualTo(72)
    }

    @Test fun serverRateLimitAndParseErrors_keepTheirMessage() {
        listOf<AppError>(
            AppError.Server(500, "c1"),
            AppError.RateLimited(30, "c2"),
            AppError.Parse("rangeMi", "c3"),
        ).forEach { error ->
            val harness = GarageHarness().also { it.vehicles.vehicles = Outcome.Err(error) }
            val state = HomeViewModel(harness.garage, remote).state.value
            assertThat(state.error).isEqualTo(error)
            assertThat(state.hasData).isFalse()
            assertThat(harness.sessions.session.value).isNotNull()
        }
    }

    @Test fun unauthorized_endsSession_andSetsLoginNotice() {
        h.vehicles.vehicles = Outcome.Err(AppError.Unauthorized("cid-401"))

        viewModel()

        assertThat(h.sessions.session.value).isNull()
        assertThat(h.auth.logouts).isEqualTo(1)
        assertThat(h.garage.signInNotice.value).isEqualTo("Your session has expired. Sign in again.")
    }

    @Test fun lockTile_requestsLock_orUnlockWhenLocked() {
        val vm = viewModel()
        assertThat(vm.state.value.locked).isTrue()
        vm.requestLockToggle()
        assertThat(remote.pending.value?.type).isEqualTo(CommandType.UNLOCK)

        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.door-ajar"))
        vm.refresh()
        vm.requestLockToggle()
        assertThat(remote.pending.value?.type).isEqualTo(CommandType.LOCK)
    }

    @Test fun refreshOnResume_whenScenarioChanged() = runTest {
        val vm = viewModel()
        val calls = h.vehicles.statusCalls

        vm.onShown()
        assertThat(h.vehicles.statusCalls).isEqualTo(calls)

        h.config.setScenario("low-battery")
        h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.low-battery"))
        vm.onShown()

        assertThat(vm.state.value.level).isEqualTo(8)
    }
}
