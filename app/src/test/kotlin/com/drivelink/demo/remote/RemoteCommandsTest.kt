package com.drivelink.demo.remote

import com.drivelink.core.designsystem.component.CommandStage
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ClimateParams
import com.drivelink.core.domain.model.CommandReason
import com.drivelink.core.domain.model.CommandStatus
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.TempMode
import com.drivelink.demo.AURORA
import com.drivelink.demo.FakeCommandRepository
import com.drivelink.demo.FakeCommandRepository.Companion.command
import com.drivelink.demo.GarageHarness
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteCommandsTest {

    private val h = GarageHarness()
    private val repo = FakeCommandRepository()

    private fun TestScope.remote(): RemoteCommands {
        val useCase = RunCommandUseCase(repo, TimingLog { _, _, _ -> }, testScheduler.timeSource)
        return RemoteCommands(useCase, h.garage, this, 2500.milliseconds)
    }

    private suspend fun loadGarage() = h.garage.refresh()

    @Test fun confirm_withoutPendingCommand_returnsFalse() = runTest {
        loadGarage()
        assertThat(remote().confirm("1234")).isFalse()
    }

    @Test fun lock_showsSending_thenWaiting_thenDone_thenClears() = runTest {
        loadGarage()
        repo.polls = listOf(Outcome.Ok(command(CommandStatus.PENDING)), Outcome.Ok(command(CommandStatus.SUCCEEDED)))
        val remote = remote()
        remote.request(CommandType.LOCK)
        assertThat(remote.pending.value?.type).isEqualTo(CommandType.LOCK)

        assertThat(remote.confirm("1234")).isTrue()
        assertThat(remote.pending.value).isNull()
        runCurrent()
        assertThat(remote.current.value?.stage).isEqualTo(CommandStage.Waiting)
        assertThat(remote.current.value?.label).isEqualTo("Lock")
        assertThat(remote.current.value?.running).isTrue()

        advanceTimeBy(4.seconds + 1.milliseconds) // polls 1 and 2, 2 s apart
        runCurrent()
        assertThat(remote.current.value?.stage).isEqualTo(CommandStage.Succeeded)
        assertThat(remote.current.value?.running).isFalse()
        assertThat(h.vehicles.statusCalls).isGreaterThan(1) // reloaded after success

        advanceTimeBy(2500.milliseconds)
        runCurrent()
        assertThat(remote.current.value).isNull()
        assertThat(repo.requests.single().pin).isEqualTo("1234")
        assertThat(repo.requests.single().type).isEqualTo(CommandType.LOCK)
    }

    @Test fun waking_showsWakingStage() = runTest {
        loadGarage()
        repo.sendResult = Outcome.Ok(command(CommandStatus.PENDING, CommandReason.WAKING))
        val remote = remote()
        remote.request(CommandType.UNLOCK)
        remote.confirm("1234")
        runCurrent()

        assertThat(remote.current.value?.stage).isEqualTo(CommandStage.Waking)
    }

    @Test fun failure_staysUntilDismissed_withReasonText() = runTest {
        loadGarage()
        repo.polls = listOf(Outcome.Ok(command(CommandStatus.FAILED, CommandReason.DOOR_OPEN)))
        val remote = remote()
        remote.request(CommandType.LOCK)
        remote.confirm("1234")
        advanceUntilIdle()

        val card = remote.current.value!!
        assertThat(card.stage).isEqualTo(CommandStage.Failed)
        assertThat(card.detail).isEqualTo("Command failed · A door is open. Close all doors and try again.")
        assertThat(card.canRetry).isTrue()

        advanceTimeBy(30.seconds)
        assertThat(remote.current.value).isNotNull()

        remote.dismiss()
        assertThat(remote.current.value).isNull()
    }

    @Test fun retry_sendsTheSameCommandWithTheSamePin() = runTest {
        loadGarage()
        repo.polls = listOf(Outcome.Ok(command(CommandStatus.FAILED, CommandReason.UNKNOWN)))
        val remote = remote()
        remote.request(CommandType.HORN_LIGHTS)
        remote.confirm("4321")
        advanceUntilIdle()

        remote.retry()
        advanceUntilIdle()

        assertThat(repo.requests.map { it.type }).containsExactly(CommandType.HORN_LIGHTS, CommandType.HORN_LIGHTS)
        assertThat(repo.requests.map { it.pin }).containsExactly("4321", "4321")
    }

    @Test fun neverFinishing_timesOutAfter60Seconds() = runTest {
        loadGarage()
        repo.polls = listOf(Outcome.Ok(command(CommandStatus.PENDING)))
        val remote = remote()
        remote.request(CommandType.LOCK)
        remote.confirm("1234")

        advanceTimeBy(59.seconds)
        assertThat(remote.current.value?.running).isTrue()
        advanceTimeBy(2.seconds)
        runCurrent()

        val card = remote.current.value!!
        assertThat(card.stage).isEqualTo(CommandStage.TimedOut)
        assertThat(card.stage.label).isEqualTo("Vehicle did not respond")
        assertThat(card.canRetry).isTrue()
    }

    @Test fun vehicleOffline_showsErrorMessage() = runTest {
        loadGarage()
        repo.sendResult = Outcome.Err(AppError.VehicleOffline("cid-409"))
        val remote = remote()
        remote.request(CommandType.LOCK)
        remote.confirm("1234")
        advanceUntilIdle()

        val card = remote.current.value!!
        assertThat(card.stage).isEqualTo(CommandStage.Failed)
        assertThat(card.detail).isEqualTo("The vehicle is offline. Try again when it has a connection.")
        assertThat(card.canRetry).isTrue()
    }

    @Test fun unauthorized_endsSession() = runTest {
        loadGarage()
        repo.sendResult = Outcome.Err(AppError.Unauthorized("cid-401"))
        val remote = remote()
        remote.request(CommandType.LOCK)
        remote.confirm("1234")
        advanceUntilIdle()

        assertThat(h.sessions.session.value).isNull()
        assertThat(h.garage.signInNotice.value).isNotNull()
    }

    @Test fun start_sendsClimateParams() = runTest {
        loadGarage()
        val params = ClimateParams(TempMode.SET, tempF = 70, heatedSeats = 2, durationMin = 7)
        val remote = remote()
        remote.request(CommandType.START, params)
        remote.confirm("1234")
        advanceUntilIdle()

        assertThat(repo.requests.single().params).isEqualTo(params)
        assertThat(h.config.vin.value).isEqualTo(AURORA)
    }

    @Test fun commandText_coversEveryReason() {
        CommandReason.entries.forEach { assertThat(CommandText.reason(it)).isNotEmpty() }
        assertThat(CommandText.reason(null)).isEqualTo("The vehicle could not complete the command.")
    }

    private fun TestScope.runCurrent() = testScheduler.runCurrent()
}
