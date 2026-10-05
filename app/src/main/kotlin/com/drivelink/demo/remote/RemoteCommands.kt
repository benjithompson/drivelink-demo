package com.drivelink.demo.remote

import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.designsystem.component.CommandStage
import com.drivelink.core.domain.command.CommandProgress
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ClimateParams
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.model.CommandType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A command that waits for the PIN. */
data class PendingCommand(val type: CommandType, val params: ClimateParams? = null)

/** The command card on Home. */
data class CommandUi(
    val type: CommandType,
    val label: String,
    val stage: CommandStage,
    /** Replaces the stage text, for example the failure reason. */
    val detail: String? = null,
) {
    val running: Boolean get() = stage == CommandStage.Sending || stage == CommandStage.Waiting || stage == CommandStage.Waking
    val canRetry: Boolean get() = stage == CommandStage.Failed || stage == CommandStage.TimedOut
}

/**
 * Runs remote commands for the whole app. A screen calls [request]; the PIN screen calls [confirm]
 * with the checked PIN; Home shows [current]. The state lives here, not in a screen ViewModel,
 * because the command goes on while the user moves from the PIN screen back to Home.
 *
 * - Success: the card shows "Done" for [successHold], then goes away. The vehicle status reloads,
 *   and the commanded lock, climate or charging state stays on screen (D-30).
 * - Failure, timeout and error: the card stays until the user dismisses it or starts a command.
 * - [retry] sends the same command again with the same PIN (kept in memory until the card is gone).
 */
@Singleton
class RemoteCommands internal constructor(
    private val runCommand: RunCommandUseCase,
    private val garage: GarageRepository,
    private val scope: CoroutineScope,
    private val successHold: Duration,
) {
    @Inject
    constructor(runCommand: RunCommandUseCase, garage: GarageRepository) :
        this(runCommand, garage, CoroutineScope(SupervisorJob() + Dispatchers.Default), SUCCESS_HOLD)

    private val _pending = MutableStateFlow<PendingCommand?>(null)
    private val _current = MutableStateFlow<CommandUi?>(null)
    private var job: Job? = null
    private var last: Pair<PendingCommand, String>? = null

    val pending: StateFlow<PendingCommand?> = _pending.asStateFlow()
    val current: StateFlow<CommandUi?> = _current.asStateFlow()

    /** Remembers the command until the PIN screen confirms it. */
    fun request(type: CommandType, params: ClimateParams? = null) {
        _pending.value = PendingCommand(type, params)
    }

    fun cancelPending() {
        _pending.value = null
    }

    /** Starts the pending command. Returns false when nothing is pending or a command runs. */
    fun confirm(pin: String): Boolean {
        val command = _pending.value ?: return false
        if (_current.value?.running == true) return false
        _pending.value = null
        start(command, pin)
        return true
    }

    fun retry() {
        val (command, pin) = last ?: return
        if (_current.value?.running == true) return
        start(command, pin)
    }

    fun dismiss() {
        if (_current.value?.running == true) return
        job?.cancel()
        _current.value = null
        last = null
    }

    /** Clears everything, for sign-out. */
    fun reset() {
        job?.cancel()
        _pending.value = null
        _current.value = null
        last = null
    }

    private fun start(command: PendingCommand, pin: String) {
        last = command to pin
        job?.cancel()
        val label = CommandText.label(command.type)
        val vin = garage.state.value.selected?.vin
        job = scope.launch {
            if (vin == null) {
                _current.value = CommandUi(command.type, label, CommandStage.Failed, "Command failed · No vehicle selected.")
                return@launch
            }
            runCommand(vin, CommandRequest(command.type, pin, command.params)).collect { progress ->
                _current.value = when (progress) {
                    CommandProgress.Sending -> CommandUi(command.type, label, CommandStage.Sending)
                    is CommandProgress.Waiting ->
                        CommandUi(command.type, label, if (progress.waking) CommandStage.Waking else CommandStage.Waiting)
                    is CommandProgress.Succeeded -> CommandUi(command.type, label, CommandStage.Succeeded)
                    is CommandProgress.Failed -> CommandUi(
                        command.type, label, CommandStage.Failed,
                        "Command failed · " + CommandText.reason(progress.reason),
                    )
                    CommandProgress.TimedOut -> CommandUi(command.type, label, CommandStage.TimedOut)
                    is CommandProgress.Error -> CommandUi(command.type, label, CommandStage.Failed, progress.error.message)
                }
                when (progress) {
                    is CommandProgress.Succeeded -> {
                        garage.commandSucceeded(vin, command.type)
                        delay(successHold)
                        _current.value = null
                        last = null
                    }
                    is CommandProgress.Error ->
                        if (progress.error is AppError.Unauthorized) garage.expireSession(progress.error.message)
                    else -> Unit
                }
            }
        }
    }

    companion object {
        /** How long "Done" stays on Home. */
        val SUCCESS_HOLD: Duration = 2500.milliseconds
    }
}
