package com.drivelink.core.domain.command

import com.drivelink.core.domain.ContractFiles
import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.IndexEntry
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.model.Command
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.repository.CommandRepository
import kotlin.time.Duration
import kotlinx.coroutines.delay

/**
 * Scriptable [CommandRepository]. It records the virtual time ([clock]) and the attempt of each call.
 * Each call takes [latency] of virtual time before it returns.
 */
class FakeCommandRepository(
    private val clock: () -> Long,
    private val latency: Duration = Duration.ZERO,
    private val onSend: (CommandRequest) -> Outcome<Command>,
    private val onGet: (attempt: Int) -> Outcome<Command>,
) : CommandRepository {

    /** One getCommand call: X-Poll-Attempt and the virtual time in ms when the call started. */
    data class Poll(val commandId: String, val attempt: Int, val atMs: Long)

    val sendTimesMs = mutableListOf<Long>()
    val polls = mutableListOf<Poll>()

    /** Polls that returned. A poll cancelled in flight is in [polls] but not counted here. */
    var completedPolls = 0
        private set

    override suspend fun send(vin: String, request: CommandRequest): Outcome<Command> {
        sendTimesMs += clock()
        delay(latency)
        return onSend(request)
    }

    override suspend fun get(commandId: String, attempt: Int): Outcome<Command> {
        polls += Poll(commandId, attempt, clock())
        delay(latency)
        completedPolls++
        return onGet(attempt)
    }

    companion object {
        /**
         * Emulates a command scenario of the virtual service with the examples in api/examples:
         * sendCommand returns the 202 default example. getCommand returns the 200 example of
         * [scenario] whose X-Poll-Attempt matcher matches the attempt, else the example without a matcher.
         */
        fun forScenario(scenario: String, clock: () -> Long, latency: Duration = Duration.ZERO): FakeCommandRepository {
            val index = ContractFiles.index
            val sent = decode(index.single { it.operationId == "sendCommand" && it.status == 202 && it.scenario == "default" })
            val polls = index.filter { it.operationId == "getCommand" && it.status == 200 && it.scenario == scenario }
            require(polls.isNotEmpty()) { "No getCommand examples for scenario $scenario" }
            val matched = polls.filter { "X-Poll-Attempt" in it.match }
            val fallback = polls.single { it.match.isEmpty() }
            var sentType = sent.type
            return FakeCommandRepository(
                clock = clock,
                latency = latency,
                onSend = { request ->
                    sentType = request.type
                    Outcome.Ok(sent.copy(type = request.type))
                },
                onGet = { attempt ->
                    val entry = matched.firstOrNull { Regex(it.match.getValue("X-Poll-Attempt")).matches(attempt.toString()) }
                        ?: fallback
                    Outcome.Ok(decode(entry).copy(type = sentType))
                },
            )
        }

        private fun decode(entry: IndexEntry): Command =
            DriveLinkJson.decodeFromString(Command.serializer(), ContractFiles.read(entry))
    }
}
