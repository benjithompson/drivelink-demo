package com.drivelink.demo.charging

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.DayOfWeek
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.SOLACE
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class ScheduleViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val c = ChargeHarness()

    private fun viewModel() = ScheduleViewModel(c.garage, c.remote)

    @Test fun loadsSchedulesAndDeparture() {
        val state = viewModel().state.value

        assertThat(state.loaded).isTrue()
        assertThat(state.schedules).hasSize(1)
        assertThat(state.schedules.single().start).isEqualTo("23:00")
        assertThat(state.schedules.single().end).isEqualTo("06:00")
        assertThat(state.schedules.single().enabled).isTrue()
        assertThat(state.schedules.single().days).hasSize(5)
        assertThat(state.departure?.time).isEqualTo("07:30")
        assertThat(state.departure?.enabled).isTrue()
    }

    @Test fun edits_changeTheForm_notTheSavedSettings() {
        val vm = viewModel()

        vm.setScheduleEnabled(0, false)
        vm.setScheduleStart(0, "22:00")
        vm.setScheduleEnd(0, "05:30")
        vm.toggleScheduleDay(0, DayOfWeek.SAT)
        vm.toggleScheduleDay(0, DayOfWeek.MON)
        vm.setDepartureTime("08:15")
        vm.setDepartureEnabled(false)

        val state = vm.state.value
        assertThat(state.schedules.single().enabled).isFalse()
        assertThat(state.schedules.single().start).isEqualTo("22:00")
        assertThat(state.schedules.single().end).isEqualTo("05:30")
        assertThat(state.schedules.single().days).contains(DayOfWeek.SAT)
        assertThat(state.schedules.single().days).doesNotContain(DayOfWeek.MON)
        assertThat(state.departure?.time).isEqualTo("08:15")
        assertThat(state.departure?.enabled).isFalse()
        assertThat(c.vehicles.puts).isEmpty()
        assertThat(c.garage.state.value.chargeSettings?.schedules?.single()?.startTime).isEqualTo("23:00")
    }

    @Test fun save_putsTheWholeSettings_andFinishes() {
        val vm = viewModel()
        vm.setScheduleStart(0, "22:00")
        vm.toggleScheduleDay(0, DayOfWeek.SAT)
        vm.setDepartureTime("08:15")

        vm.save()

        val sent = c.vehicles.puts.single()
        assertThat(sent.acTargetPct).isEqualTo(80)
        assertThat(sent.dcTargetPct).isEqualTo(90)
        assertThat(sent.schedules.single().startTime).isEqualTo("22:00")
        assertThat(sent.schedules.single().days).containsExactly(
            DayOfWeek.MON, DayOfWeek.TUE, DayOfWeek.WED, DayOfWeek.THU, DayOfWeek.FRI, DayOfWeek.SAT,
        ).inOrder()
        assertThat(sent.departure?.time).isEqualTo("08:15")
        assertThat(sent.departure?.climate?.setTempF).isEqualTo(70)
        assertThat(vm.state.value.saved).isTrue()
        assertThat(vm.state.value.saving).isFalse()
        assertThat(c.garage.state.value.chargeSettings?.schedules?.single()?.startTime).isEqualTo("22:00")
    }

    @Test fun failedSave_keepsTheForm_andShowsTheError() {
        c.vehicles.putResult = Outcome.Err(AppError.Server(500, "cid-p"))
        val vm = viewModel()
        vm.setScheduleStart(0, "22:00")

        vm.save()

        val state = vm.state.value
        assertThat(state.saved).isFalse()
        assertThat(state.saving).isFalse()
        assertThat(state.saveError?.correlationId).isEqualTo("cid-p")
        assertThat(state.schedules.single().start).isEqualTo("22:00")
        assertThat(c.garage.state.value.chargeSettings?.schedules?.single()?.startTime).isEqualTo("23:00")

        // A new edit clears the error; a second try can work.
        c.vehicles.putResult = null
        vm.setScheduleEnd(0, "07:00")
        assertThat(vm.state.value.saveError).isNull()
        vm.save()
        assertThat(vm.state.value.saved).isTrue()
    }

    @Test fun enabledScheduleWithoutDays_isInvalid_andDoesNotSave() {
        val vm = viewModel()
        DayOfWeek.entries.forEach { day -> if (day in vm.state.value.schedules.single().days) vm.toggleScheduleDay(0, day) }

        vm.save()

        assertThat(vm.state.value.valid).isFalse()
        assertThat(c.vehicles.puts).isEmpty()

        vm.setScheduleEnabled(0, false)
        assertThat(vm.state.value.valid).isTrue()
    }

    @Test fun loadError_isShown_andRetryFillsTheForm() {
        c.vehicles.settings = Outcome.Err(AppError.Network(timeout = false, correlationId = "cid-l"))
        val vm = viewModel()

        assertThat(vm.state.value.loaded).isFalse()
        assertThat(vm.state.value.loadError?.correlationId).isEqualTo("cid-l")

        c.vehicles.settings = ChargeVehicleRepository(c.h.vehicles).settings
        vm.load()

        assertThat(vm.state.value.loaded).isTrue()
        assertThat(vm.state.value.loadError).isNull()
    }

    @Test fun gasCar_isNotAvailable() {
        c.h.config.vin.value = SOLACE

        val state = viewModel().state.value

        assertThat(state.available).isFalse()
        assertThat(c.vehicles.gets).isEqualTo(0)
    }
}
