package com.drivelink.demo.home

import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.model.CommandType
import com.drivelink.demo.AURORA
import com.drivelink.demo.FakeCommandRepository
import com.drivelink.demo.GarageHarness
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.SOLACE
import com.drivelink.demo.remote.RemoteCommands
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The Lock tile and the picker, with the commanded states of D-30. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeCommandStateTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()
    private val repo = FakeCommandRepository()

    private fun TestScope.remote(): RemoteCommands {
        val useCase = RunCommandUseCase(repo, TimingLog { _, _, _ -> }, testScheduler.timeSource)
        return RemoteCommands(useCase, h.garage, this, 2500.milliseconds)
    }

    private suspend fun TestScope.runCommand(remote: RemoteCommands, type: CommandType) {
        remote.request(type)
        remote.confirm("1234")
        runCurrent()
        advanceTimeBy(6.seconds)
        runCurrent()
    }

    @Test fun lockTile_alternatesBetweenLockAndUnlock_whenTheMockAlwaysAnswersLocked() = runTest {
        val remote = remote()
        val vm = HomeViewModel(h.garage, remote)
        assertThat(vm.state.value.locked).isTrue()

        vm.requestLockToggle()
        assertThat(remote.pending.value?.type).isEqualTo(CommandType.UNLOCK)
        remote.cancelPending()
        runCommand(remote, CommandType.UNLOCK)
        assertThat(vm.state.value.locked).isFalse()

        vm.requestLockToggle()
        assertThat(remote.pending.value?.type).isEqualTo(CommandType.LOCK)
        remote.cancelPending()
        advanceTimeBy(3.seconds) // let the "Done" card of the first command go
        runCurrent()
        runCommand(remote, CommandType.LOCK)
        assertThat(vm.state.value.locked).isTrue()
    }

    @Test fun climateTile_showsTheCommandedState() = runTest {
        val remote = remote()
        val vm = HomeViewModel(h.garage, remote)
        assertThat(vm.state.value.climateOn).isFalse()

        runCommand(remote, CommandType.START)

        assertThat(vm.state.value.climateOn).isTrue()
        assertThat(vm.state.value.vehicleStatusText).isEqualTo("Running")
    }

    @Test fun chargeStop_changesTheChip() = runTest {
        val remote = remote()
        val vm = HomeViewModel(h.garage, remote)
        assertThat(vm.state.value.chip?.label).isEqualTo("Charging")

        runCommand(remote, CommandType.CHARGE_STOP)

        assertThat(vm.state.value.chip?.label).isEqualTo("Charge Schedule")
    }

    @Test fun pickerOptions_carryThumbnailAndVinTail() {
        val options = HomeViewModel(h.garage, RemoteCommands(
            RunCommandUseCase(repo, TimingLog { _, _, _ -> }),
            h.garage,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            2500.milliseconds,
        )).state.value.vehicles

        assertThat(options.map { it.vin }).containsExactly(AURORA, SOLACE).inOrder()
        assertThat(options.map { it.vinTail }).containsExactly("000101", "000202").inOrder()
        assertThat(options.first { it.vin == AURORA }.selected).isTrue()
        assertThat(options.first { it.vin == SOLACE }.selected).isFalse()
        // The sedan and the crossover use different illustrations.
        assertThat(options.map { it.style }.distinct()).hasSize(2)
    }
}
