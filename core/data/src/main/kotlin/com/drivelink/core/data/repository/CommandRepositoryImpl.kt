package com.drivelink.core.data.repository

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.model.Command
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.repository.CommandRepository
import com.drivelink.core.network.api.DriveLinkApi
import com.drivelink.core.network.apiCall
import javax.inject.Inject

class CommandRepositoryImpl @Inject constructor(
    private val api: DriveLinkApi,
) : CommandRepository {

    override suspend fun send(vin: String, request: CommandRequest): Outcome<Command> =
        apiCall(Command.serializer()) { api.sendCommand(vin, request) }

    override suspend fun get(commandId: String, attempt: Int): Outcome<Command> =
        apiCall(Command.serializer()) { api.getCommand(commandId, attempt) }
}
