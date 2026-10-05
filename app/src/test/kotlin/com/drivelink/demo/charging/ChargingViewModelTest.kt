package com.drivelink.demo.charging

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.UserUnits
import com.drivelink.demo.AURORA
import com.drivelink.demo.Examples
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.SOLACE
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChargingViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val c = ChargeHarness()

    private fun viewModel() = ChargingViewModel(c.garage, c.remote)

    @Test fun loadsStatusAndSettings() {
        val state = viewModel().state.value

        assertThat(state.available).isTrue()
        assertThat(state.hasData).isTrue()
        assertThat(state.levelPct).isEqualTo(72)
        assertThat(state.limitPct).isEqualTo(80)
        assertThat(state.stateLabel).isEqualTo("AC charging")
        assertThat(state.active).isTrue()
        assertThat(state.limits).isEqualTo(Limits(80, 90))
        assertThat(state.stats.associate { it.key to it.value }).containsExactly(
            "battery", "72%",
            "range", "226 mi",
            "time", "0hr 42min",
            "rate", "6.6 kW",
            "cost", "$4.80",
            "energy", "+12 kWh",
        )
        assertThat(state.stats.first { it.key == "time" }.label).isEqualTo("Time Remaining to 80%")
        assertThat(state.scheduleText).isEqualTo("Active")
        assertThat(state.departureText).isEqualTo("7:30 AM")
        assertThat(state.limitsText).isEqualTo("AC 80% · DC 90%")
        assertThat(state.updatedText).startsWith("Last updated ")
    }

    @Test fun kilometers_convertRange() {
        c.h.account.user = Outcome.Ok(Examples.user.copy(units = UserUnits(DistanceUnit.KM, TemperatureUnit.C)))

        val range = viewModel().state.value.stats.first { it.key == "range" }.value

        assertThat(range).isEqualTo("364 km")
    }

    @Test fun gasCar_isNotAvailable_andSendsNoChargingRequest() {
        c.h.config.vin.value = SOLACE
        val vm = viewModel()

        val state = vm.state.value
        assertThat(state.available).isFalse()
        assertThat(state.limits).isNull()
        assertThat(state.stats).isEmpty()
        assertThat(c.vehicles.gets).isEqualTo(0)
    }

    @Test fun switchingToTheGasCar_hidesCharging() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.available).isTrue()

        c.garage.selectVehicle(SOLACE)
        runCurrent()

        assertThat(vm.state.value.available).isFalse()
        assertThat(vm.state.value.limits).isNull()
    }

    @Test fun notPlugged_showsDashes_andStartIsNotPossible() {
        c.h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.low-battery"))

        val state = viewModel().state.value

        assertThat(state.active).isFalse()
        assertThat(state.pluggedIn).isFalse()
        assertThat(state.stateLabel).isEqualTo("Not plugged in")
        assertThat(state.stats.first { it.key == "rate" }.value).isEqualTo("--")
        assertThat(state.stats.first { it.key == "time" }.value).isEqualTo("--")
    }

    @Test fun limitChange_savesAfterTheDelay() = runTest {
        val vm = viewModel()

        vm.setAcLimit(60)
        assertThat(vm.state.value.limits).isEqualTo(Limits(60, 90))
        assertThat(vm.state.value.limitPct).isEqualTo(60)
        advanceTimeBy(ChargingViewModel.SAVE_DELAY_MS - 1)
        runCurrent()
        assertThat(c.vehicles.puts).isEmpty()

        advanceTimeBy(2)
        runCurrent()

        assertThat(c.vehicles.puts.single().acTargetPct).isEqualTo(60)
        assertThat(c.vehicles.puts.single().dcTargetPct).isEqualTo(90)
        assertThat(vm.state.value.saveStatus).isEqualTo(SaveStatus.Saved)
        assertThat(vm.state.value.limits).isEqualTo(Limits(60, 90))
        assertThat(c.garage.state.value.chargeSettings?.acTargetPct).isEqualTo(60)
    }

    @Test fun fastMoves_saveOnce_withTheLastValue() = runTest {
        val vm = viewModel()

        vm.setAcLimit(60)
        advanceTimeBy(200)
        vm.setAcLimit(70)
        vm.setDcLimit(100)
        advanceTimeBy(ChargingViewModel.SAVE_DELAY_MS + 1)
        runCurrent()

        assertThat(c.vehicles.puts).hasSize(1)
        assertThat(c.vehicles.puts.single().acTargetPct).isEqualTo(70)
        assertThat(c.vehicles.puts.single().dcTargetPct).isEqualTo(100)
    }

    @Test fun limit_snapsToTenPercentSteps_andStaysInRange() = runTest {
        val vm = viewModel()

        vm.setAcLimit(63)
        assertThat(vm.state.value.limits?.ac).isEqualTo(60)
        vm.setAcLimit(20)
        assertThat(vm.state.value.limits?.ac).isEqualTo(50)
        vm.setDcLimit(140)
        assertThat(vm.state.value.limits?.dc).isEqualTo(100)
    }

    @Test fun movingBackToTheSavedValue_sendsNothing() = runTest {
        val vm = viewModel()

        vm.setAcLimit(60)
        vm.setAcLimit(80)
        advanceTimeBy(ChargingViewModel.SAVE_DELAY_MS + 1)
        runCurrent()

        assertThat(c.vehicles.puts).isEmpty()
    }

    @Test fun failedSave_revertsTheSliders_andShowsTheError() = runTest {
        c.vehicles.putResult = Outcome.Err(AppError.Server(500, "cid-put"))
        val vm = viewModel()

        vm.setAcLimit(60)
        advanceTimeBy(ChargingViewModel.SAVE_DELAY_MS + 1)
        runCurrent()

        val state = vm.state.value
        assertThat(c.vehicles.puts).hasSize(1)
        assertThat(state.limits).isEqualTo(Limits(80, 90))
        assertThat(state.saveStatus).isEqualTo(SaveStatus.Error)
        assertThat(state.saveText).contains("Something went wrong on our side.")
        assertThat(state.saveText).contains("cid-put")
        assertThat(c.garage.state.value.chargeSettings?.acTargetPct).isEqualTo(80)
    }

    @Test fun startAndStop_setThePendingCommand() = runTest {
        val vm = viewModel()
        vm.requestToggle()
        assertThat(c.remote.pending.value?.type).isEqualTo(CommandType.CHARGE_STOP)

        c.remote.cancelPending()
        c.h.vehicles.status[AURORA] = Outcome.Ok(Examples.status("200.low-battery"))
        c.garage.refresh()
        runCurrent()
        vm.requestToggle()

        assertThat(c.remote.pending.value?.type).isEqualTo(CommandType.CHARGE_START)
    }

    @Test fun settingsError_showsInTheLimitsSection_andRetryLoads() = runTest {
        c.vehicles.settings = Outcome.Err(AppError.Network(timeout = true, correlationId = "cid-s"))
        val vm = viewModel()

        val failed = vm.state.value
        assertThat(failed.hasData).isTrue()
        assertThat(failed.limits).isNull()
        assertThat(failed.settingsError?.correlationId).isEqualTo("cid-s")

        c.vehicles.settings = Outcome.Ok(Examples.decode("getChargeSettings/200.default.json", com.drivelink.core.domain.model.ChargeSettings.serializer()))
        vm.retrySettings()
        runCurrent()

        assertThat(vm.state.value.settingsError).isNull()
        assertThat(vm.state.value.limits).isEqualTo(Limits(80, 90))
    }

    @Test fun statusError_withoutData_showsError() {
        c.h.vehicles.status[AURORA] = Outcome.Err(AppError.RateLimited(30, "cid-r"))

        val state = viewModel().state.value

        assertThat(state.hasData).isFalse()
        assertThat(state.error).isInstanceOf(AppError.RateLimited::class.java)
    }

    @Test fun unauthorized_endsTheSession() {
        c.h.vehicles.vehicles = Outcome.Err(AppError.Unauthorized("cid-401"))

        viewModel()

        assertThat(c.h.sessions.session.value).isNull()
        assertThat(c.garage.signInNotice.value).isNotNull()
    }

    @Test fun formatTime_andDays() {
        assertThat(formatTime("07:30")).isEqualTo("7:30 AM")
        assertThat(formatTime("00:05")).isEqualTo("12:05 AM")
        assertThat(formatTime("23:00")).isEqualTo("11:00 PM")
        assertThat(formatTime("12:00")).isEqualTo("12:00 PM")
        assertThat(formatDays(com.drivelink.core.domain.model.DayOfWeek.entries)).isEqualTo("Every day")
    }
}
