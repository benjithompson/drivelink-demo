package com.drivelink.core.domain.command

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.command.CommandProgress.Error
import com.drivelink.core.domain.command.CommandProgress.Failed
import com.drivelink.core.domain.command.CommandProgress.Sending
import com.drivelink.core.domain.command.CommandProgress.Succeeded
import com.drivelink.core.domain.command.CommandProgress.TimedOut
import com.drivelink.core.domain.command.CommandProgress.Waiting
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ClimateParams
import com.drivelink.core.domain.model.Command
import com.drivelink.core.domain.model.CommandReason
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.model.CommandStatus
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.TempMode
import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunCommandUseCaseTest {

    private data class Mark(val name: String, val ms: Long, val tags: Map<String, String>)

    /** The collected progress, the virtual time of each emission, the fake and the timing marks. */
    private data class Run(
        val progress: List<CommandProgress>,
        val emittedAtMs: List<Long>,
        val repo: FakeCommandRepository,
        val marks: List<Mark>,
    ) {
        val pollTimesMs get() = repo.polls.map { it.atMs }
        val attempts get() = repo.polls.map { it.attempt }
        val terminal get() = progress.last()
        val endMs get() = emittedAtMs.last()
    }

    private val lock = CommandRequest(type = CommandType.LOCK, pin = "1234")

    private suspend fun TestScope.run(
        request: CommandRequest = lock,
        makeRepo: (clock: () -> Long) -> FakeCommandRepository,
    ): Run {
        val repo = makeRepo { testScheduler.currentTime }
        val marks = mutableListOf<Mark>()
        val timing = TimingLog { name, ms, tags -> marks += Mark(name, ms, tags) }
        val useCase = RunCommandUseCase(repo, timing, testScheduler.timeSource)
        val times = mutableListOf<Long>()
        val progress = useCase(VIN, request).onEach { times += testScheduler.currentTime }.toList()
        return Run(progress, times, repo, marks)
    }

    private suspend fun TestScope.runScenario(
        scenario: String,
        request: CommandRequest = lock,
        latency: Duration = Duration.ZERO,
    ): Run = run(request) { clock -> FakeCommandRepository.forScenario(scenario, clock, latency) }

    private fun assertMark(run: Run, ms: Long, result: String, type: String = "LOCK") {
        assertThat(run.marks).containsExactly(
            Mark("command_ms", ms, mapOf("type" to type, "result" to result)),
        )
    }

    @Test
    fun `poll delay is 2 s for attempts 1 to 5 and 4 s after`() {
        assertThat((1..8).map { RunCommandUseCase.pollDelay(it) })
            .containsExactly(2.seconds, 2.seconds, 2.seconds, 2.seconds, 2.seconds, 4.seconds, 4.seconds, 4.seconds)
            .inOrder()
    }

    @Test
    fun `default - pending twice then succeeded on attempt 3`() = runTest {
        val run = runScenario("default")

        assertThat(run.progress.dropLast(1)).containsExactly(
            Sending,
            Waiting(attempt = 0, waking = false),
            Waiting(attempt = 1, waking = false),
            Waiting(attempt = 2, waking = false),
        ).inOrder()
        assertThat((run.terminal as Succeeded).command.status).isEqualTo(CommandStatus.SUCCEEDED)
        assertThat(run.repo.sendTimesMs).containsExactly(0L)
        assertThat(run.attempts).containsExactly(1, 2, 3).inOrder()
        assertThat(run.pollTimesMs).containsExactly(2_000L, 4_000L, 6_000L).inOrder()
        assertThat(run.repo.polls.map { it.commandId }.toSet()).containsExactly("cmd-5b1e7c40")
        assertThat(run.endMs).isEqualTo(6_000L)
        assertMark(run, ms = 6_000, result = "SUCCEEDED")
    }

    @Test
    fun `fast - succeeded on attempt 1`() = runTest {
        val run = runScenario("fast")

        assertThat(run.progress.dropLast(1)).containsExactly(Sending, Waiting(0, false)).inOrder()
        assertThat(run.terminal).isInstanceOf(Succeeded::class.java)
        assertThat(run.attempts).containsExactly(1)
        assertThat(run.pollTimesMs).containsExactly(2_000L)
        assertMark(run, ms = 2_000, result = "SUCCEEDED")
    }

    @Test
    fun `slow-vehicle - pending through attempt 8 with slow polls, then succeeded before the hard stop`() = runTest {
        val run = runScenario("slow-vehicle", latency = 2_500.milliseconds)

        assertThat(run.terminal).isInstanceOf(Succeeded::class.java)
        assertThat(run.attempts).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9).inOrder()
        // Send takes 2.5 s, each poll takes 2.5 s, the wait starts when the previous call returns.
        assertThat(run.pollTimesMs)
            .containsExactly(4_500L, 9_000L, 13_500L, 18_000L, 22_500L, 29_000L, 35_500L, 42_000L, 48_500L)
            .inOrder()
        assertThat(run.endMs).isEqualTo(51_000L)
        assertThat(run.endMs).isLessThan(60_000L)
        assertMark(run, ms = 51_000, result = "SUCCEEDED")
    }

    @Test
    fun `vehicle-asleep - waking on attempts 1 and 2, pending on 3 and 4, succeeded on 5`() = runTest {
        val run = runScenario("vehicle-asleep")

        assertThat(run.progress.dropLast(1)).containsExactly(
            Sending,
            Waiting(attempt = 0, waking = false),
            Waiting(attempt = 1, waking = true),
            Waiting(attempt = 2, waking = true),
            Waiting(attempt = 3, waking = false),
            Waiting(attempt = 4, waking = false),
        ).inOrder()
        assertThat(run.terminal).isInstanceOf(Succeeded::class.java)
        assertThat(run.pollTimesMs).containsExactly(2_000L, 4_000L, 6_000L, 8_000L, 10_000L).inOrder()
        assertMark(run, ms = 10_000, result = "SUCCEEDED")
    }

    @Test
    fun `command-fails - pending then failed with DOOR_OPEN`() = runTest {
        val run = runScenario("command-fails")

        val failed = run.terminal as Failed
        assertThat(failed.reason).isEqualTo(CommandReason.DOOR_OPEN)
        assertThat(failed.command.status).isEqualTo(CommandStatus.FAILED)
        assertThat(run.attempts).containsExactly(1, 2).inOrder()
        assertThat(run.endMs).isEqualTo(4_000L)
        assertMark(run, ms = 4_000, result = "FAILED")
    }

    @Test
    fun `command-timeout - always pending, timed out at exactly 60 s`() = runTest {
        val run = runScenario("command-timeout")

        assertThat(run.terminal).isEqualTo(TimedOut)
        assertThat(run.endMs).isEqualTo(60_000L)
        // 2 s backoff for attempts 1-5, then 4 s.
        val expected = listOf(2L, 4, 6, 8, 10) + (14L..58L step 4)
        assertThat(run.pollTimesMs).containsExactlyElementsIn(expected.map { it * 1_000 }).inOrder()
        assertThat(run.attempts).containsExactlyElementsIn(1..expected.size).inOrder()
        assertThat(run.progress.filterIsInstance<Waiting>().last()).isEqualTo(Waiting(expected.size, false))
        assertMark(run, ms = 60_000, result = "TIMED_OUT")
    }

    @Test
    fun `hard stop cancels an in-flight poll at 60 s`() = runTest {
        val run = runScenario("command-timeout", latency = 7.seconds)

        assertThat(run.terminal).isEqualTo(TimedOut)
        // Each call takes 7 s. Poll 6 starts at 56 s and would return at 63 s.
        assertThat(run.pollTimesMs).containsExactly(9_000L, 18_000L, 27_000L, 36_000L, 45_000L, 56_000L).inOrder()
        assertThat(run.repo.completedPolls).isEqualTo(5)
        assertThat(run.endMs).isEqualTo(60_000L)
        assertMark(run, ms = 60_000, result = "TIMED_OUT")
    }

    @Test
    fun `send error - vehicle offline stops with Error and no poll`() = runTest {
        val offline = AppError.VehicleOffline(correlationId = "corr-409")
        val run = run { clock ->
            FakeCommandRepository(clock, onSend = { Outcome.Err(offline) }, onGet = { error("No poll expected") })
        }

        assertThat(run.progress).containsExactly(Sending, Error(offline)).inOrder()
        assertThat(run.repo.polls).isEmpty()
        assertMark(run, ms = 0, result = "ERROR")
    }

    @Test
    fun `poll error stops with Error`() = runTest {
        val serverError = AppError.Server(status = 500, correlationId = "corr-500")
        val run = run { clock ->
            FakeCommandRepository(
                clock,
                onSend = { Outcome.Ok(command(CommandStatus.QUEUED)) },
                onGet = { attempt -> if (attempt == 1) Outcome.Ok(command(CommandStatus.PENDING)) else Outcome.Err(serverError) },
            )
        }

        assertThat(run.progress).containsExactly(
            Sending,
            Waiting(0, false),
            Waiting(1, false),
            Error(serverError),
        ).inOrder()
        assertThat(run.attempts).containsExactly(1, 2).inOrder()
        assertThat(run.endMs).isEqualTo(4_000L)
        assertMark(run, ms = 4_000, result = "ERROR")
    }

    @Test
    fun `timing mark carries the command type`() = runTest {
        val start = CommandRequest(
            type = CommandType.START,
            pin = "1234",
            params = ClimateParams(tempMode = TempMode.SET, tempF = 70, durationMin = 10),
        )
        val run = runScenario("fast", request = start)

        assertThat((run.terminal as Succeeded).command.type).isEqualTo(CommandType.START)
        assertMark(run, ms = 2_000, result = "SUCCEEDED", type = "START")
    }

    private fun command(status: CommandStatus, reason: CommandReason? = null) = Command(
        commandId = "cmd-test",
        type = CommandType.LOCK,
        status = status,
        reason = reason,
        createdAt = "2026-10-02T17:00:00Z",
        updatedAt = "2026-10-02T17:00:00Z",
    )

    private companion object {
        const val VIN = "DLEV26AURA0000101"
    }
}
