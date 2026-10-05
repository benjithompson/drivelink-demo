package com.drivelink.demo.maps

import com.drivelink.core.data.garage.GarageState
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.format.displayMessage
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.Location
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.UserUnits
import com.drivelink.core.domain.repository.VehicleRepository
import com.drivelink.demo.AURORA
import com.drivelink.demo.Examples
import com.drivelink.demo.GarageHarness
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.SOLACE
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** A vehicle repository whose location answers the test sets; everything else comes from [base]. */
private class LocationRepo(base: VehicleRepository) : VehicleRepository by base {
    val answers = mutableMapOf<String, Outcome<Location>>(
        AURORA to Outcome.Ok(Location(33.6189, -117.9298, 8, "120 Marina Way, Harbor District", "Harbor District", "2026-10-02T16:58:00Z")),
        SOLACE to Outcome.Ok(Location(33.6412, -117.9187, 12, "455 Cypress Avenue, Old Town", "Old Town", "2026-10-02T16:40:00Z")),
    )
    val calls = mutableListOf<String>()

    override suspend fun getLocation(vin: String): Outcome<Location> {
        calls += vin
        return answers.getValue(vin)
    }
}

class MapsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()
    private val repo = LocationRepo(h.vehicles)

    private fun viewModel() = MapsViewModel(h.garage, repo, h.config)

    // Distance

    @Test fun distanceMiles_isZeroForTheSamePoint() {
        assertThat(distanceMiles(33.6, -117.9, 33.6, -117.9)).isEqualTo(0.0)
    }

    @Test fun distanceMiles_oneDegreeOfLatitudeIsAbout69Miles() {
        assertThat(distanceMiles(33.0, -117.0, 34.0, -117.0)).isWithin(0.2).of(69.1)
    }

    @Test fun distanceMiles_auroraIsAboutThreeTenthsOfAMileFromTheDemoOrigin() {
        assertThat(distanceMiles(DemoOrigin.LAT, DemoOrigin.LON, 33.6189, -117.9298)).isWithin(0.02).of(0.3)
    }

    @Test fun formatDistance_usesOneDecimalAndTheUnit() {
        assertThat(formatDistance(0.3, DistanceUnit.MI)).isEqualTo("0.3 mi")
        assertThat(formatDistance(0.3, DistanceUnit.KM)).isEqualTo("0.5 km")
    }

    // State mapping

    @Test fun mapsUiState_describesTheLocation() {
        val garage = GarageState(
            vehicles = Examples.vehicles, selected = Examples.vehicles.first(), user = Examples.user,
        )
        val load = LocationLoad(AURORA, (repo.answers.getValue(AURORA) as Outcome.Ok).value)

        val ui = mapsUiState(garage, load, now = Instant.parse("2026-10-02T17:00:00Z"))

        assertThat(ui.vehicleTitle).isEqualTo("2026 Aurora EV")
        assertThat(ui.location?.address).isEqualTo("120 Marina Way, Harbor District")
        assertThat(ui.location?.distance).isEqualTo("0.3 mi away")
        assertThat(ui.location?.accuracy).isEqualTo("±8 m")
        assertThat(ui.location?.updated).isEqualTo("Updated 2 min ago")
        assertThat(ui.loading).isFalse()
        assertThat(ui.error).isNull()
    }

    @Test fun mapsUiState_addressFallsBackToLocalityThenText() {
        val garage = GarageState(vehicles = Examples.vehicles, selected = Examples.vehicles.first(), user = Examples.user)
        val base = Location(1.0, 2.0, 5, null, "Harbor District", "2026-10-02T16:58:00Z")

        assertThat(mapsUiState(garage, LocationLoad(AURORA, base)).location?.address).isEqualTo("Harbor District")
        assertThat(mapsUiState(garage, LocationLoad(AURORA, base.copy(locality = null))).location?.address)
            .isEqualTo("Address unavailable")
    }

    @Test fun mapsUiState_ignoresALocationOfAnotherVehicle() {
        val garage = GarageState(vehicles = Examples.vehicles, selected = Examples.vehicles.first(), user = Examples.user)
        val other = LocationLoad(SOLACE, (repo.answers.getValue(SOLACE) as Outcome.Ok).value)

        val ui = mapsUiState(garage, other)

        assertThat(ui.location).isNull()
        assertThat(ui.loading).isTrue()
    }

    @Test fun mapsUiState_withoutAVehicle_showsTheGarageError() {
        val error = AppError.Server(500, "cid-1")
        val ui = mapsUiState(GarageState(error = error, loaded = true), LocationLoad())

        assertThat(ui.error).isEqualTo(error)
        assertThat(ui.loading).isFalse()
    }

    // ViewModel

    @Test fun loadsTheLocationOfTheSelectedVehicle() {
        val state = viewModel().state.value

        assertThat(state.error).isNull()
        assertThat(state.loading).isFalse()
        assertThat(state.vehicleTitle).isEqualTo("2026 Aurora EV")
        assertThat(state.location?.lat).isEqualTo(33.6189)
        assertThat(state.location?.lon).isEqualTo(-117.9298)
        assertThat(state.location?.distance).isEqualTo("0.3 mi away")
        assertThat(repo.calls).containsExactly(AURORA)
    }

    @Test fun kilometers_convertTheDistance() {
        h.account.user = Outcome.Ok(Examples.user.copy(units = UserUnits(DistanceUnit.KM, TemperatureUnit.C)))

        assertThat(viewModel().state.value.location?.distance).isEqualTo("0.5 km away")
    }

    @Test fun switchingTheVehicle_loadsItsLocation() = runTest {
        val vm = viewModel()
        h.garage.selectVehicle(SOLACE)

        val state = vm.state.value
        assertThat(state.vehicleTitle).isEqualTo("2025 Solace")
        assertThat(state.location?.address).isEqualTo("455 Cypress Avenue, Old Town")
        assertThat(state.location?.distance).isEqualTo("1.4 mi away")
        assertThat(repo.calls).containsExactly(AURORA, SOLACE).inOrder()
    }

    @Test fun onShown_doesNotReloadWhenNothingChanged() {
        val vm = viewModel()
        vm.onShown()
        vm.onShown()

        assertThat(repo.calls).hasSize(1)
    }

    @Test fun aNewScenario_reloadsTheLocation() {
        val vm = viewModel()
        h.config.scenario.value = "tire-low"
        vm.onShown()

        assertThat(repo.calls).hasSize(2)
    }

    @Test fun refresh_reloadsTheLocation() {
        val vm = viewModel()
        repo.answers[AURORA] = Outcome.Ok(Location(33.62, -117.93, 20, "1 New Street", "Harbor District", "2026-10-02T16:59:00Z"))
        vm.refresh()

        assertThat(repo.calls).hasSize(2)
        assertThat(vm.state.value.location?.address).isEqualTo("1 New Street")
        assertThat(vm.state.value.location?.accuracy).isEqualTo("±20 m")
    }

    @Test fun serverError_showsTheErrorWithItsCorrelationId_thenRetryLoads() {
        repo.answers[AURORA] = Outcome.Err(AppError.Server(500, "cid-500"))
        val vm = viewModel()

        val failed = vm.state.value
        assertThat(failed.location).isNull()
        assertThat(failed.error).isInstanceOf(AppError.Server::class.java)
        assertThat(failed.error?.correlationId).isEqualTo("cid-500")
        assertThat(failed.loading).isFalse()

        repo.answers[AURORA] = Outcome.Ok(Location(33.6189, -117.9298, 8, "120 Marina Way", null, "2026-10-02T16:58:00Z"))
        vm.refresh()

        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.location?.address).isEqualTo("120 Marina Way")
    }

    @Test fun failedRefresh_keepsTheOldCard_andShowsTheError() {
        val vm = viewModel()
        repo.answers[AURORA] = Outcome.Err(AppError.Network(timeout = true, correlationId = "cid-net"))
        vm.refresh()

        val state = vm.state.value
        assertThat(state.location).isNotNull()
        assertThat(state.error?.correlationId).isEqualTo("cid-net")
    }

    @Test fun rateLimited_tellsTheWait() {
        val error = AppError.RateLimited(30, "cid-429")

        assertThat(error.displayMessage()).isEqualTo("Too many requests. Try again in 30 seconds.")
        assertThat(AppError.RateLimited(null, "cid-429").displayMessage()).isEqualTo("Too many requests. Wait and try again.")
    }

    @Test fun unauthorized_endsTheSession() {
        repo.answers[AURORA] = Outcome.Err(AppError.Unauthorized("cid-401"))
        viewModel()

        assertThat(h.auth.logouts).isEqualTo(1)
        assertThat(h.sessions.session.value).isNull()
        assertThat(h.garage.signInNotice.value).isEqualTo("Your session has expired. Sign in again.")
    }

    @Test fun vehicleListError_showsInsteadOfTheCard_andRetryReloadsTheList() {
        h.vehicles.vehicles = Outcome.Err(AppError.Server(503, "cid-list"))
        val vm = viewModel()

        assertThat(vm.state.value.error?.correlationId).isEqualTo("cid-list")
        assertThat(vm.state.value.location).isNull()

        h.vehicles.vehicles = Outcome.Ok(Examples.vehicles)
        vm.refresh()

        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.location).isNotNull()
    }

    @Test fun scenarioChip_isVisibleOnlyForANonDefaultScenario() {
        val vm = viewModel()
        assertThat(vm.state.value.scenarioChipVisible).isFalse()

        h.config.scenario.value = "door-ajar"

        assertThat(vm.state.value.scenarioChipVisible).isTrue()
    }
}
