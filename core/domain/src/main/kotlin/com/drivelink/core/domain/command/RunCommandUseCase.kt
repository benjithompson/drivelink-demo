package com.drivelink.core.domain.command

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.Command
import com.drivelink.core.domain.model.CommandReason
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.model.CommandStatus
import com.drivelink.core.domain.repository.CommandRepository
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/** Progress of one remote command. [Succeeded], [Failed], [TimedOut] and [Error] are terminal. */
sealed interface CommandProgress {
    /** The POST is in flight. */
    data object Sending : CommandProgress

    /**
     * The vehicle has not finished the command. [attempt] is the last poll (0 = accepted, no poll yet).
     * [waking] is true while the command reason is WAKING.
     */
    data class Waiting(val attempt: Int, val waking: Boolean) : CommandProgress

    data class Succeeded(val command: Command) : CommandProgress

    /** The vehicle rejected the command. [reason] is the cause, for example DOOR_OPEN. */
    data class Failed(val reason: CommandReason?, val command: Command) : CommandProgress

    /** No terminal state within [RunCommandUseCase.HARD_STOP]. */
    data object TimedOut : CommandProgress

    /** The send or a poll returned an error. Polling stops. */
    data class Error(val error: AppError) : CommandProgress
}

/**
 * Sends a command and polls it to a terminal state (docs/source-plan.md, "Remote command pattern").
 *
 * - sendCommand, then getCommand with X-Poll-Attempt 1, 2, 3 ...
 * - Wait 2 s before polls 1 to 5, 4 s before poll 6 and later. The wait starts when the previous call returns.
 * - Hard stop [HARD_STOP] after the start of the send. An in-flight call is cancelled.
 * - The first error stops the flow. No retry here: the network layer already retries a GET once.
 * - Each terminal state logs the timing mark `command_ms` with tags `type` and `result`.
 *
 * [timeSource] measures `command_ms`. Tests pass the test scheduler's time source.
 */
class RunCommandUseCase(
    private val repository: CommandRepository,
    private val timing: TimingLog,
    private val timeSource: TimeSource,
) {
    @Inject
    constructor(repository: CommandRepository, timing: TimingLog) :
        this(repository, timing, TimeSource.Monotonic)

    operator fun invoke(vin: String, request: CommandRequest): Flow<CommandProgress> = flow {
        val start = timeSource.markNow()
        emit(CommandProgress.Sending)
        val terminal = withTimeoutOrNull(HARD_STOP) { sendAndPoll(vin, request) }
            ?: CommandProgress.TimedOut
        timing.mark(
            TIMING_NAME,
            start.elapsedNow().inWholeMilliseconds,
            mapOf("type" to request.type.name, "result" to resultTag(terminal)),
        )
        emit(terminal)
    }

    /** Emits [CommandProgress.Waiting] states and returns the terminal state. */
    private suspend fun FlowCollector<CommandProgress>.sendAndPoll(
        vin: String,
        request: CommandRequest,
    ): CommandProgress {
        var command = when (val sent = repository.send(vin, request)) {
            is Outcome.Ok -> sent.value
            is Outcome.Err -> return CommandProgress.Error(sent.error)
        }
        var attempt = 0
        while (true) {
            when (command.status) {
                CommandStatus.SUCCEEDED -> return CommandProgress.Succeeded(command)
                CommandStatus.FAILED -> return CommandProgress.Failed(command.reason, command)
                CommandStatus.QUEUED, CommandStatus.PENDING ->
                    emit(CommandProgress.Waiting(attempt, waking = command.reason == CommandReason.WAKING))
            }
            attempt++
            delay(pollDelay(attempt))
            command = when (val polled = repository.get(command.commandId, attempt)) {
                is Outcome.Ok -> polled.value
                is Outcome.Err -> return CommandProgress.Error(polled.error)
            }
        }
    }

    companion object {
        /** Total time limit, from the start of the send. */
        val HARD_STOP: Duration = 60.seconds

        /** Wait before polls 1 to [FAST_POLLS]. */
        val FAST_INTERVAL: Duration = 2.seconds

        /** Wait before each later poll. */
        val SLOW_INTERVAL: Duration = 4.seconds

        const val FAST_POLLS: Int = 5

        const val TIMING_NAME: String = "command_ms"

        /** The wait before poll [attempt] (1-based). */
        fun pollDelay(attempt: Int): Duration = if (attempt <= FAST_POLLS) FAST_INTERVAL else SLOW_INTERVAL

        private fun resultTag(terminal: CommandProgress): String = when (terminal) {
            is CommandProgress.Succeeded -> "SUCCEEDED"
            is CommandProgress.Failed -> "FAILED"
            is CommandProgress.TimedOut -> "TIMED_OUT"
            is CommandProgress.Error -> "ERROR"
            is CommandProgress.Sending, is CommandProgress.Waiting -> error("Not terminal: $terminal")
        }
    }
}
