package com.drivelink.core.data.garage

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ChargeState
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.Powertrain
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GarageRepositoryTest {
    private val f = GarageFixture()
    private val garage = f.garage

    @Test fun refresh_loadsVehiclesUserStatusAndRows() = runTest {
        garage.refresh()

        val s = garage.state.value
        assertThat(s.vehicles.map { it.vin }).containsExactly(AURORA, SOLACE).inOrder()
        assertThat(s.selected?.vin).isEqualTo(AURORA)
        assertThat(s.status?.batteryPct).isEqualTo(72)
        assertThat(s.user).isNotNull()
        assertThat(s.locationLabel).isEqualTo("Harbor District")
        assertThat(s.lastTrip?.id).isEqualTo("t1")
        assertThat(s.unreadAlerts).isEqualTo(f.alerts.alerts.count { !it.read })
        assertThat(s.loaded).isTrue()
        assertThat(s.loading).isFalse()
        assertThat(s.error).isNull()
        assertThat(f.config.vin.value).isEqualTo(AURORA)
    }

    @Test fun ensureLoaded_loadsOnce_andAgainAfterScenarioChange() = runTest {
        garage.ensureLoaded()
        garage.ensureLoaded()
        assertThat(f.vehicles.statusCalls).isEqualTo(1)

        f.config.setScenario("low-battery")
        f.vehicles.status[AURORA] = Outcome.Ok(statusExample("200.low-battery"))
        garage.ensureLoaded()

        assertThat(f.vehicles.statusCalls).isEqualTo(2)
        assertThat(garage.state.value.status?.batteryPct).isEqualTo(8)
    }

    @Test fun savedVin_isSelected() = runTest {
        f.config.vin.value = SOLACE

        garage.refresh()

        assertThat(garage.state.value.selected?.vin).isEqualTo(SOLACE)
        assertThat(garage.state.value.status?.fuelPct).isNotNull()
    }

    @Test fun selectVehicle_switchesAndLoadsItsStatus() = runTest {
        garage.refresh()

        garage.selectVehicle(SOLACE)

        val s = garage.state.value
        assertThat(s.selected?.vin).isEqualTo(SOLACE)
        assertThat(s.status?.powertrain).isEqualTo(Powertrain.ICE)
        assertThat(f.config.vin.value).isEqualTo(SOLACE)
        assertThat(s.loading).isFalse()
    }

    @Test fun selectVehicle_sameOrUnknownVin_doesNothing() = runTest {
        garage.refresh()
        val calls = f.vehicles.statusCalls

        garage.selectVehicle(AURORA)
        garage.selectVehicle("NOPE")

        assertThat(f.vehicles.statusCalls).isEqualTo(calls)
        assertThat(garage.state.value.selected?.vin).isEqualTo(AURORA)
    }

    @Test fun listError_setsErrorAndKeepsLoadedFlag() = runTest {
        f.vehicles.vehicles = Outcome.Err(AppError.Server(500, "cid-1"))

        garage.refresh()

        val s = garage.state.value
        assertThat(s.error).isInstanceOf(AppError.Server::class.java)
        assertThat(s.loaded).isTrue()
        assertThat(s.loading).isFalse()
        assertThat(s.status).isNull()
    }

    @Test fun setUnreadAlerts_updatesTheCount() {
        garage.setUnreadAlerts(7)

        assertThat(garage.state.value.unreadAlerts).isEqualTo(7)
    }

    @Test fun vehicleOffline_marksStatusStale() = runTest {
        f.vehicles.status[AURORA] = Outcome.Ok(statusExample("200.vehicle-offline"))

        garage.refresh()

        assertThat(garage.state.value.stale).isTrue()
    }

    // --- Commanded states ---

    @Test fun lock_overrideSurvivesRefresh_andAlternates() = runTest {
        garage.refresh()
        assertThat(garage.state.value.status?.locked).isTrue()

        garage.commandSucceeded(AURORA, CommandType.UNLOCK)
        // The fake answers locked = true every time, like the mock.
        assertThat(garage.state.value.status?.locked).isFalse()
        garage.refreshStatus()
        garage.refresh()
        assertThat(garage.state.value.status?.locked).isFalse()

        garage.commandSucceeded(AURORA, CommandType.LOCK)
        assertThat(garage.state.value.status?.locked).isTrue()
    }

    @Test fun lock_overrideEnds_whenFetchedValueDiffersFromCommandTime() = runTest {
        garage.refresh()
        garage.commandSucceeded(AURORA, CommandType.UNLOCK)
        assertThat(garage.state.value.status?.locked).isFalse()

        // The vehicle now reports unlocked: the override has done its job.
        f.vehicles.status[AURORA] = Outcome.Ok(statusExample("200.door-ajar"))
        garage.refreshStatus()
        assertThat(garage.state.value.status?.locked).isFalse()

        // A later fetch shows locked again. Nothing holds the old override.
        f.vehicles.status[AURORA] = Outcome.Ok(statusExample("200.default"))
        garage.refreshStatus()
        assertThat(garage.state.value.status?.locked).isTrue()
    }

    @Test fun start_andStop_overrideClimateAndEngine() = runTest {
        garage.refresh()

        garage.commandSucceeded(AURORA, CommandType.START)
        assertThat(garage.state.value.status?.climate?.on).isTrue()
        assertThat(garage.state.value.status?.engineOn).isTrue()
        garage.refresh()
        assertThat(garage.state.value.status?.climate?.on).isTrue()

        garage.commandSucceeded(AURORA, CommandType.STOP)
        assertThat(garage.state.value.status?.climate?.on).isFalse()
        assertThat(garage.state.value.status?.engineOn).isFalse()
    }

    @Test fun chargeStop_andStart_overrideCharging() = runTest {
        garage.refresh()
        assertThat(garage.state.value.status?.charging?.state).isEqualTo(ChargeState.CHARGING)

        garage.commandSucceeded(AURORA, CommandType.CHARGE_STOP)
        val stopped = garage.state.value.status?.charging
        assertThat(stopped?.active).isFalse()
        assertThat(stopped?.state).isEqualTo(ChargeState.SCHEDULED)
        garage.refreshStatus()
        assertThat(garage.state.value.status?.charging?.active).isFalse()

        garage.commandSucceeded(AURORA, CommandType.CHARGE_START)
        assertThat(garage.state.value.status?.charging?.active).isTrue()
        assertThat(garage.state.value.status?.charging?.state).isEqualTo(ChargeState.CHARGING)
    }

    @Test fun hornAndLights_doNotOverride_butReload() = runTest {
        garage.refresh()
        val calls = f.vehicles.statusCalls

        garage.commandSucceeded(AURORA, CommandType.HORN_LIGHTS)

        assertThat(f.vehicles.statusCalls).isEqualTo(calls + 1)
        assertThat(garage.state.value.status?.locked).isTrue()
    }

    @Test fun commandForAnotherVehicle_isIgnored() = runTest {
        garage.refresh()

        garage.commandSucceeded(SOLACE, CommandType.UNLOCK)

        assertThat(garage.state.value.status?.locked).isTrue()
    }

    @Test fun overrides_clearOnVehicleSwitch() = runTest {
        garage.refresh()
        garage.commandSucceeded(AURORA, CommandType.UNLOCK)

        garage.selectVehicle(SOLACE)
        garage.selectVehicle(AURORA)

        assertThat(garage.state.value.status?.locked).isTrue()
    }

    @Test fun overrides_clearOnScenarioChange() = runTest {
        garage.refresh()
        garage.commandSucceeded(AURORA, CommandType.UNLOCK)

        f.config.setScenario("tire-low")
        garage.ensureLoaded()

        assertThat(garage.state.value.status?.locked).isTrue()
    }

    @Test fun overrides_clearOnSignOut() = runTest {
        garage.refresh()
        garage.commandSucceeded(AURORA, CommandType.UNLOCK)

        garage.signOut()
        garage.refresh()

        assertThat(f.auth.logouts).isEqualTo(1)
        assertThat(garage.state.value.status?.locked).isTrue()
    }

    // --- Session end seen by the auth gate ---

    @Test fun noteSessionEnded_afterInterceptorClearedSession_setsNoticeAndDropsData() = runTest {
        garage.refresh()

        garage.noteSessionEnded()

        assertThat(garage.signInNotice.value).isEqualTo("Your session has expired. Sign in again.")
        assertThat(garage.state.value.status).isNull()
    }

    @Test fun noteSessionEnded_afterUserSignOut_setsNoNotice() = runTest {
        garage.refresh()
        garage.signOut()

        garage.noteSessionEnded()

        assertThat(garage.signInNotice.value).isNull()
    }

    @Test fun noteSessionEnded_afterNewSignIn_setsNoticeAgain() = runTest {
        garage.signOut()
        garage.consumeNotice() // a new sign-in

        garage.noteSessionEnded()

        assertThat(garage.signInNotice.value).isNotNull()
    }

    // --- Race: a load that ends after a reset ---

    @Test fun refreshThatEndsAfterSignOut_doesNotRepopulate() = runTest {
        val gate = CompletableDeferred<Unit>()
        f.account.gate = gate
        val job = launch { garage.refresh() }
        runCurrent()
        assertThat(garage.state.value.loading).isTrue()

        garage.signOut()
        gate.complete(Unit)
        job.join()

        assertThat(garage.state.value).isEqualTo(GarageState())
    }

    @Test fun statusLoadThatEndsAfterSignOut_doesNotRepopulate() = runTest {
        val gate = CompletableDeferred<Unit>()
        f.vehicles.statusGate = gate
        val job = launch { garage.refresh() }
        runCurrent()

        garage.signOut()
        gate.complete(Unit)
        job.join()

        assertThat(garage.state.value).isEqualTo(GarageState())
    }

    @Test fun failureThatEndsAfterSignOut_doesNotSetError() = runTest {
        f.vehicles.vehicles = Outcome.Err(AppError.Unauthorized("cid-401"))
        val gate = CompletableDeferred<Unit>()
        f.account.gate = gate
        val job = launch { garage.refresh() }
        runCurrent()

        garage.signOut()
        gate.complete(Unit)
        job.join()

        // A late Unauthorized would make a screen end the session a second time.
        assertThat(garage.state.value.error).isNull()
    }

    @Test fun refreshThatEndsAfterScenarioChange_dropsItsData_andNextLoadReloads() = runTest {
        val gate = CompletableDeferred<Unit>()
        f.vehicles.statusGate = gate
        val job = launch { garage.refresh() }
        runCurrent()

        f.config.setScenario("low-battery")
        f.vehicles.status[AURORA] = Outcome.Ok(statusExample("200.low-battery"))
        gate.complete(Unit)
        job.join()

        // The late result carried the data of the old scenario. It must not show.
        assertThat(garage.state.value.status).isNull()

        f.vehicles.statusGate = null
        garage.ensureLoaded()
        assertThat(garage.state.value.status?.batteryPct).isEqualTo(8)
    }

    @Test fun statusRefreshThatEndsAfterSignOut_doesNotRepopulate() = runTest {
        garage.refresh()
        val gate = CompletableDeferred<Unit>()
        f.vehicles.statusGate = gate
        val job = launch { garage.refreshStatus() }
        runCurrent()

        garage.signOut()
        gate.complete(Unit)
        job.join()

        assertThat(garage.state.value.status).isNull()
    }

    // --- Charge settings ---

    @Test fun chargeSettings_loadOnce_thenKept_andForceReloads() = runTest {
        garage.refresh()

        val first = garage.loadChargeSettings()
        val second = garage.loadChargeSettings()
        assertThat(first).isInstanceOf(Outcome.Ok::class.java)
        assertThat(second).isInstanceOf(Outcome.Ok::class.java)
        assertThat(f.vehicles.chargeGets).isEqualTo(1)
        assertThat(garage.state.value.chargeSettings?.acTargetPct).isEqualTo(80)

        garage.loadChargeSettings(force = true)
        assertThat(f.vehicles.chargeGets).isEqualTo(2)
    }

    @Test fun chargeSettings_gasCar_answersNotFoundWithoutRequest() = runTest {
        f.config.vin.value = SOLACE
        garage.refresh()

        val result = garage.loadChargeSettings()

        assertThat((result as Outcome.Err).error).isInstanceOf(AppError.NotFound::class.java)
        assertThat(f.vehicles.chargeGets).isEqualTo(0)
    }

    @Test fun saveChargeSettings_keepsTheSentValues_notTheAnswer() = runTest {
        garage.refresh()
        val settings = (garage.loadChargeSettings() as Outcome.Ok).value

        val result = garage.saveChargeSettings(settings.copy(acTargetPct = 60))

        // The fake answers 80 like the mock. The app keeps 60.
        assertThat((result as Outcome.Ok).value.acTargetPct).isEqualTo(60)
        assertThat(garage.state.value.chargeSettings?.acTargetPct).isEqualTo(60)
        assertThat(f.vehicles.chargePuts.single().acTargetPct).isEqualTo(60)
    }

    @Test fun saveChargeSettings_error_keepsTheOldSettings() = runTest {
        garage.refresh()
        val settings = (garage.loadChargeSettings() as Outcome.Ok).value
        f.vehicles.chargePut = Outcome.Err(AppError.Server(500, "cid-5"))

        val result = garage.saveChargeSettings(settings.copy(acTargetPct = 60))

        assertThat((result as Outcome.Err).error.correlationId).isEqualTo("cid-5")
        assertThat(garage.state.value.chargeSettings?.acTargetPct).isEqualTo(80)
    }

    @Test fun chargeSettings_clearOnVehicleSwitch() = runTest {
        garage.refresh()
        garage.loadChargeSettings()

        garage.selectVehicle(SOLACE)

        assertThat(garage.state.value.chargeSettings).isNull()
    }
}
