package com.drivelink.demo.trips

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.DistanceUnit
import com.drivelink.core.domain.model.TemperatureUnit
import com.drivelink.core.domain.model.UserUnits
import com.drivelink.demo.AURORA
import com.drivelink.demo.Examples
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.SOLACE
import com.drivelink.demo.screenload.ExampleData
import com.drivelink.demo.screenload.ScreenHarness
import com.drivelink.demo.screenload.networkError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TripsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = ScreenHarness()
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-03T09:00:00Z")

    private fun viewModel() = TripsViewModel(h.garage, h.vehicles, h.config)

    @Test fun loads20Trips_ofTheSelectedVehicle() {
        val state = viewModel().state.value

        assertThat(h.vehicles.tripLimits).contains(TRIPS_LIMIT)
        assertThat(TRIPS_LIMIT).isEqualTo(20)
        assertThat(state.error).isNull()
        assertThat(state.loading).isFalse()
        assertThat(state.summary?.countText).isEqualTo("3 trips")
        assertThat(state.summary?.distanceText).isEqualTo("49.4 mi")
        assertThat(state.days.flatMap { it.trips }.map { it.id }).containsExactly("trp-3021", "trp-3020", "trp-3019").inOrder()
    }

    @Test fun rows_showTimes_duration_distance_andEvEfficiency() {
        val (summary, days) = tripsContent(ExampleData.trips(), now = now, zone = utc)

        assertThat(summary.count).isEqualTo(3)
        assertThat(days.map { it.key }).containsExactly("2026-10-02", "2026-10-01").inOrder()
        assertThat(days.map { it.title }).containsExactly("Yesterday", "Thu, Oct 1").inOrder()
        assertThat(days[0].trips.single()).isEqualTo(
            TripRowUi("trp-3021", "2:05 PM – 2:31 PM", "26 min", "12.4 mi", "3.9 mi/kWh"),
        )
        assertThat(days[1].trips.map { it.id }).containsExactly("trp-3020", "trp-3019").inOrder()
        assertThat(days[1].trips[0].time).isEqualTo("11:40 PM – 12:22 AM")
    }

    @Test fun today_isNamed() {
        val (_, days) = tripsContent(ExampleData.trips(), now = Instant.parse("2026-10-02T20:00:00Z"), zone = utc)

        assertThat(days[0].title).isEqualTo("Today")
        assertThat(days[1].title).isEqualTo("Yesterday")
    }

    @Test fun daysFollowTheTimeZone() {
        val (_, days) = tripsContent(ExampleData.trips(), now = now, zone = ZoneId.of("Asia/Tokyo"))

        // In Tokyo (UTC+9) all three trips fall on Oct 2: 15:10 UTC on Oct 1 is 00:10 JST on Oct 2.
        assertThat(days.map { it.key }).containsExactly("2026-10-02")
        assertThat(days.single().trips.map { it.id }).containsExactly("trp-3021", "trp-3020", "trp-3019").inOrder()
        assertThat(days.single().trips.last().time).isEqualTo("12:10 AM – 12:24 AM")
    }

    @Test fun gasCar_showsMpg() {
        h.config.vin.value = SOLACE

        val state = viewModel().state.value

        assertThat(state.summary?.countText).isEqualTo("3 trips")
        assertThat(state.days.flatMap { it.trips }.map { it.efficiency }.distinct()).containsExactly("34.2 MPG", "31.4 MPG", "35.8 MPG")
    }

    @Test fun kilometers_convertDistanceAndEfficiency() {
        h.account.user = Outcome.Ok(Examples.user.copy(units = UserUnits(DistanceUnit.KM, TemperatureUnit.C)))

        val state = viewModel().state.value
        val first = state.days.flatMap { it.trips }.first()
        assertThat(first.distance).isEqualTo("20.0 km")
        assertThat(first.efficiency).isEqualTo("6.3 km/kWh")
        assertThat(state.summary?.distanceText).isEqualTo("79.5 km")

        val (_, gas) = tripsContent(
            ExampleData.trips("200.default-ice"), UserUnits(DistanceUnit.KM, TemperatureUnit.C), utc, now,
        )
        assertThat(gas.flatMap { it.trips }.first().efficiency).isEqualTo("6.9 L/100 km")
    }

    @Test fun tripWithoutEfficiency_hasNone() {
        val trip = ExampleData.trips().first().copy(efficiency = null, efficiencyUnit = null)

        val (_, days) = tripsContent(listOf(trip), now = now, zone = utc)

        assertThat(days.single().trips.single().efficiency).isNull()
    }

    @Test fun emptyList_isEmptyState() {
        h.vehicles.trips[AURORA] = Outcome.Ok(emptyList())

        val state = viewModel().state.value

        assertThat(state.empty).isTrue()
        assertThat(state.hasData).isTrue()
        assertThat(state.summary?.countText).isEqualTo("0 trips")
        assertThat(state.summary?.distanceText).isEqualTo("0.0 mi")
        assertThat(state.days).isEmpty()
        assertThat(state.error).isNull()
    }

    @Test fun oneTrip_isSingular() {
        h.vehicles.trips[AURORA] = Outcome.Ok(ExampleData.trips().take(1))

        assertThat(viewModel().state.value.summary?.countText).isEqualTo("1 trip")
    }

    @Test fun networkError_showsError_thenRetryLoads() {
        h.vehicles.trips[AURORA] = Outcome.Err(networkError("cid-9"))
        val vm = viewModel()

        val failed = vm.state.value
        assertThat(failed.hasData).isFalse()
        assertThat(failed.loading).isFalse()
        assertThat(failed.error).isInstanceOf(AppError.Network::class.java)
        assertThat(failed.error?.correlationId).isEqualTo("cid-9")

        h.vehicles.trips[AURORA] = Outcome.Ok(ExampleData.trips())
        vm.refresh()

        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.summary?.count).isEqualTo(3)
    }

    @Test fun serverRateLimitAndParseErrors_keepTheirType() {
        listOf<AppError>(
            AppError.Server(500, "c1"),
            AppError.RateLimited(30, "c2"),
            AppError.Parse("trips", "c3"),
        ).forEach { error ->
            val harness = ScreenHarness().also { it.vehicles.trips[AURORA] = Outcome.Err(error) }
            val state = TripsViewModel(harness.garage, harness.vehicles, harness.config).state.value
            assertThat(state.error).isEqualTo(error)
            assertThat(state.hasData).isFalse()
            assertThat(harness.sessions.session.value).isNotNull()
        }
    }

    @Test fun failedRefresh_keepsTheOldTrips() {
        val vm = viewModel()
        h.vehicles.trips[AURORA] = Outcome.Err(AppError.Server(500, "cid-5"))

        vm.refresh()

        assertThat(vm.state.value.summary?.count).isEqualTo(3)
        assertThat(vm.state.value.error?.correlationId).isEqualTo("cid-5")
    }

    @Test fun unauthorized_endsSession() {
        h.vehicles.trips[AURORA] = Outcome.Err(AppError.Unauthorized("cid-401"))

        viewModel()

        assertThat(h.sessions.session.value).isNull()
        assertThat(h.garage.signInNotice.value).isEqualTo("Your session has expired. Sign in again.")
    }

    @Test fun vehicleSwitch_loadsTheOtherCarsTrips() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.days.flatMap { it.trips }.first().id).isEqualTo("trp-3021")

        h.garage.selectVehicle(SOLACE)

        assertThat(vm.state.value.days.flatMap { it.trips }.first().id).isEqualTo("trp-4112")
    }
}
