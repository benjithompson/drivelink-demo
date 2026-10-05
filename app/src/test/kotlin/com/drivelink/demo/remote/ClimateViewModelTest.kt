package com.drivelink.demo.remote

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.TempMode
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.UserUnits
import com.drivelink.demo.Examples
import com.drivelink.demo.FakeCommandRepository
import com.drivelink.demo.GarageHarness
import com.drivelink.demo.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class ClimateViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()
    private val remote = RemoteCommands(
        RunCommandUseCase(FakeCommandRepository(), TimingLog { _, _, _ -> }),
        h.garage, CoroutineScope(Dispatchers.Unconfined), 2500.milliseconds,
    )

    private fun viewModel(units: UserUnits? = null): ClimateViewModel {
        if (units != null) h.account.user = Outcome.Ok(Examples.user.copy(units = units))
        runBlocking { h.garage.refresh() }
        return ClimateViewModel(h.vehicles, h.garage, remote)
    }

    @Test fun firstPreset_isSelected_andFillsTheForm() {
        val vm = viewModel()

        val s = vm.state.value
        assertThat(s.presets.map { it.name }).containsExactly("Summer", "Winter", "Preset 3", "Preset 4").inOrder()
        assertThat(s.selectedPreset).isEqualTo(0)
        assertThat(s.temperature).isEqualTo("68°")
        assertThat(s.durationMin).isEqualTo(10)
        assertThat(vm.buildParams().presetId).isEqualTo("pre-1")
    }

    @Test fun winterPreset_setsHeatedSeatLevel3_andOtherOptions() {
        val vm = viewModel()
        vm.selectPreset(1)

        val p = vm.buildParams()
        assertThat(p.tempMode).isEqualTo(TempMode.SET)
        assertThat(p.tempF).isEqualTo(76)
        assertThat(p.frontDefrost).isTrue()
        assertThat(p.rearDefrost).isTrue()
        assertThat(p.heatedSteeringWheel).isTrue()
        assertThat(p.heatedSeats).isEqualTo(3)
        assertThat(p.presetId).isEqualTo("pre-2")
    }

    @Test fun heatedSeats_isALevel0to3_andEditDropsThePresetId() {
        val vm = viewModel()
        vm.selectPreset(1)

        vm.setHeatedSeats(2)
        assertThat(vm.buildParams().heatedSeats).isEqualTo(2)
        assertThat(vm.buildParams().presetId).isNull()
        assertThat(vm.state.value.selectedPreset).isNull()

        vm.setHeatedSeats(9)
        assertThat(vm.buildParams().heatedSeats).isEqualTo(3)
        vm.setHeatedSeats(0)
        assertThat(vm.buildParams().heatedSeats).isEqualTo(0)
    }

    @Test fun stepper_walksOffLoValuesHi_andOnlySetSendsTempF() {
        val vm = viewModel()
        vm.selectPreset(0)
        assertThat(vm.state.value.steps.first().display).isEqualTo("OFF")
        assertThat(vm.state.value.steps[1].display).isEqualTo("LO")
        assertThat(vm.state.value.steps[2].display).isEqualTo("62°")
        assertThat(vm.state.value.steps.last().display).isEqualTo("HI")

        repeat(40) { vm.raise() }
        assertThat(vm.state.value.temperature).isEqualTo("HI")
        assertThat(vm.buildParams().tempMode).isEqualTo(TempMode.HI)
        assertThat(vm.buildParams().tempF).isNull()

        repeat(40) { vm.lower() }
        assertThat(vm.state.value.temperature).isEqualTo("OFF")
        assertThat(vm.buildParams().tempMode).isEqualTo(TempMode.OFF)

        vm.raise()
        assertThat(vm.buildParams().tempMode).isEqualTo(TempMode.LO)
        vm.raise()
        assertThat(vm.buildParams().tempMode).isEqualTo(TempMode.SET)
        assertThat(vm.buildParams().tempF).isEqualTo(62)
    }

    @Test fun celsius_showsRange17to28_andSendsFahrenheit() {
        val vm = viewModel(UserUnits(DistanceUnit.KM, TemperatureUnit.C))

        val values = vm.state.value.steps.drop(2).dropLast(1).map { it.display }
        assertThat(values.first()).isEqualTo("17°")
        assertThat(values.last()).isEqualTo("28°")
        assertThat(values).hasSize(12)
        // Summer preset: 68 °F is 20 °C.
        assertThat(vm.state.value.temperature).isEqualTo("20°")
        assertThat(vm.buildParams().tempF).isEqualTo(68)

        vm.raise()
        vm.raise()
        assertThat(vm.state.value.temperature).isEqualTo("22°")
        assertThat(vm.buildParams().tempF).isEqualTo(72)

        repeat(20) { vm.raise() }
        assertThat(vm.state.value.temperature).isEqualTo("HI")
    }

    @Test fun duration_isClamped1to10() {
        val vm = viewModel()
        vm.setDuration(0)
        assertThat(vm.buildParams().durationMin).isEqualTo(1)
        vm.setDuration(15)
        assertThat(vm.buildParams().durationMin).isEqualTo(10)
    }

    @Test fun requestStart_remembersStartWithParams() {
        val vm = viewModel()
        vm.selectPreset(1)
        vm.requestStart()

        val pending = remote.pending.value!!
        assertThat(pending.type).isEqualTo(CommandType.START)
        assertThat(pending.params?.heatedSeats).isEqualTo(3)
        assertThat(pending.params?.durationMin).isEqualTo(10)
    }

    @Test fun presetError_isShown() {
        h.vehicles.presets = Outcome.Err(com.drivelink.core.domain.error.AppError.Server(500, "c"))
        val vm = viewModel()

        assertThat(vm.state.value.presetError).isNotNull()
        assertThat(vm.state.value.presets).isEmpty()
    }
}
