package com.drivelink.demo.carcare

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.RecallStatus
import com.drivelink.core.domain.model.ServiceItem
import com.drivelink.core.domain.model.ServiceItemStatus
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.UserUnits
import com.drivelink.demo.AURORA
import com.drivelink.demo.Examples
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.SOLACE
import com.drivelink.demo.screenload.StageBExamples
import com.drivelink.demo.screenload.StageBHarness
import com.drivelink.demo.screenload.networkError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class CarCareViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = StageBHarness()

    private fun viewModel() = CarCareViewModel(h.garage, h.vehicles, h.config)

    @Test fun loadsTheMaintenanceOfTheSelectedVehicle() {
        val state = viewModel().state.value

        assertThat(state.error).isNull()
        assertThat(state.loading).isFalse()
        val content = requireNotNull(state.content)
        assertThat(content.headline).isEqualTo(CarCareHeadline.Good)
        assertThat(content.summary).isNull()
        assertThat(content.odometer).isEqualTo("18,452mi")
        assertThat(content.lastMiles).isEqualTo("15,020mi")
        assertThat(content.lastDate).isEqualTo("06/12/26")
        assertThat(content.nextMiles).isEqualTo("22,520mi")
        assertThat(content.nextDate).isEqualTo("12/12/26")
        assertThat(content.intervalText).isEqualTo("Based on your driving habits and conditions, your service interval is 7,500 miles.")
        assertThat(content.items.map { it.id }).containsExactly("svc-11", "svc-12").inOrder()
        assertThat(content.items[0].due).isEqualTo("Due at 19,500mi")
        assertThat(content.items[1].due).isEqualTo("Due by 12/01/26")
        assertThat(content.items.map { it.statusLabel }).containsExactly("Upcoming", "Upcoming")
        assertThat(content.recalls).isEmpty()
        val center = requireNotNull(content.center)
        assertThat(center.name).isEqualTo("Harborview Auto Service")
        assertThat(center.address).isEqualTo("120 Marina Way, Harbor District")
        assertThat(center.phone).isEqualTo("+1 555 0142")
        assertThat(center.dialNumber).isEqualTo("+15550142")
        assertThat(center.distance).isEqualTo("2.4mi")
        assertThat(center.openNow).isTrue()
    }

    @Test fun gasCar_hasItsOwnData_withDueItemsAndAnOpenRecall() {
        h.config.vin.value = SOLACE

        val content = requireNotNull(viewModel().state.value.content)
        assertThat(content.headline).isEqualTo(CarCareHeadline.Due)
        assertThat(content.summary).isEqualTo("2 items due, 1 open recall")
        assertThat(content.odometer).isEqualTo("27,310mi")
        assertThat(content.items.map { it.statusLabel }).containsExactly("Due", "Due", "Upcoming").inOrder()
        assertThat(content.recalls.single().open).isTrue()
        assertThat(content.recalls.single().campaign).isEqualTo("DL-26-014")
        assertThat(content.center?.distance).isEqualTo("3.1mi")
    }

    @Test fun headline_followsTheWorstItem() {
        val base = StageBExamples.maintenance("200.default")
        val overdue = base.copy(items = base.items + ServiceItem("svc-x", "Brake fluid", dueMi = 18000, status = ServiceItemStatus.OVERDUE))
        h.vehicles.maintenance[AURORA] = Outcome.Ok(overdue)
        assertThat(viewModel().state.value.content?.headline).isEqualTo(CarCareHeadline.Overdue)

        val recallOnly = StageBExamples.maintenance("200.default-ice")
            .let { it.copy(items = it.items.map { item -> item.copy(status = ServiceItemStatus.UPCOMING) }) }
        assertThat(carCareContent(recallOnly).headline).isEqualTo(CarCareHeadline.Recall)
        assertThat(carCareContent(recallOnly).summary).isEqualTo("1 open recall")

        val remedied = recallOnly.copy(recalls = recallOnly.recalls.map { it.copy(status = RecallStatus.REMEDIED) })
        assertThat(carCareContent(remedied).headline).isEqualTo(CarCareHeadline.Good)
    }

    @Test fun kilometers_convertEveryDistance() {
        h.account.user = Outcome.Ok(Examples.user.copy(units = UserUnits(DistanceUnit.KM, TemperatureUnit.C)))

        val content = requireNotNull(viewModel().state.value.content)
        assertThat(content.odometer).isEqualTo("29,696km")
        assertThat(content.lastMiles).isEqualTo("24,172km")
        assertThat(content.items[0].due).isEqualTo("Due at 31,382km")
        assertThat(content.intervalText).endsWith("12,070 kilometers.")
        assertThat(content.center?.distance).isEqualTo("3.9km")
    }

    @Test fun missingParts_areNull() {
        val bare = StageBExamples.maintenance().copy(lastService = null, nextService = null, intervalMi = null, preferredServiceCenter = null)

        val content = carCareContent(bare)
        assertThat(content.lastMiles).isNull()
        assertThat(content.nextDate).isNull()
        assertThat(content.intervalText).isNull()
        assertThat(content.center).isNull()
    }

    @Test fun networkError_showsError_thenRetryLoads() {
        h.vehicles.maintenance[AURORA] = Outcome.Err(networkError("cid-9"))
        val vm = viewModel()

        val failed = vm.state.value
        assertThat(failed.content).isNull()
        assertThat(failed.loading).isFalse()
        assertThat(failed.error).isInstanceOf(AppError.Network::class.java)
        assertThat(failed.error?.correlationId).isEqualTo("cid-9")

        h.vehicles.maintenance[AURORA] = Outcome.Ok(StageBExamples.maintenance())
        vm.refresh()

        val loaded = vm.state.value
        assertThat(loaded.error).isNull()
        assertThat(loaded.content?.odometer).isEqualTo("18,452mi")
    }

    @Test fun serverRateLimitParseAndNotFoundErrors_keepTheirType() {
        listOf<AppError>(
            AppError.Server(500, "c1"),
            AppError.RateLimited(30, "c2"),
            AppError.Parse("odometerMi", "c3"),
            AppError.NotFound("c4"),
        ).forEach { error ->
            val harness = StageBHarness().also { it.vehicles.maintenance[AURORA] = Outcome.Err(error) }
            val state = CarCareViewModel(harness.garage, harness.vehicles, harness.config).state.value
            assertThat(state.error).isEqualTo(error)
            assertThat(state.content).isNull()
            assertThat(harness.sessions.session.value).isNotNull()
        }
    }

    @Test fun failedRefresh_keepsTheOldData() {
        val vm = viewModel()
        h.vehicles.maintenance[AURORA] = Outcome.Err(AppError.Server(500, "cid-5"))

        vm.refresh()

        val state = vm.state.value
        assertThat(state.content?.odometer).isEqualTo("18,452mi")
        assertThat(state.error?.correlationId).isEqualTo("cid-5")
    }

    @Test fun unauthorized_endsSession_andSetsLoginNotice() {
        h.vehicles.maintenance[AURORA] = Outcome.Err(AppError.Unauthorized("cid-401"))

        viewModel()

        assertThat(h.sessions.session.value).isNull()
        assertThat(h.garage.signInNotice.value).isEqualTo("Your session has expired. Sign in again.")
    }

    @Test fun vehicleListError_showsErrorWithoutAFetch() {
        h.vehicles.base.vehicles = Outcome.Err(AppError.Server(500, "cid-list"))

        val state = viewModel().state.value

        assertThat(state.error?.correlationId).isEqualTo("cid-list")
        assertThat(state.content).isNull()
        assertThat(h.vehicles.maintenanceCalls).isEqualTo(0)
    }

    @Test fun vehicleSwitch_loadsTheOtherCar() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.content?.odometer).isEqualTo("18,452mi")

        h.garage.selectVehicle(SOLACE)

        assertThat(vm.state.value.content?.odometer).isEqualTo("27,310mi")
    }

    @Test fun scenarioChange_reloadsOnResume_butTheSameSourceDoesNot() = runTest {
        val vm = viewModel()
        val calls = h.vehicles.maintenanceCalls

        vm.onShown()
        assertThat(h.vehicles.maintenanceCalls).isEqualTo(calls)

        h.vehicles.maintenance[AURORA] = Outcome.Ok(StageBExamples.maintenance("200.default-ice"))
        h.config.setScenario("low-battery")
        vm.onShown()

        assertThat(vm.state.value.content?.odometer).isEqualTo("27,310mi")
    }
}
