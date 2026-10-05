package com.drivelink.demo.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.domain.config.PinStore
import com.drivelink.demo.login.PinBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PinEntryUiState(
    val entered: Int = 0,
    val error: String? = null,
    /** Label of the pending command, for the screen reader, for example "Lock". */
    val commandLabel: String = "",
)

/**
 * The PIN prompt before a remote command. The app checks the PIN against the local PIN, then sends
 * it with the command (D-07). The server can still reject it (`INVALID_PIN`); that error shows on
 * the command card.
 */
@HiltViewModel
class PinEntryViewModel @Inject constructor(
    private val pinStore: PinStore,
    private val remote: RemoteCommands,
) : ViewModel() {

    private val buffer = PinBuffer()
    private val ui = MutableStateFlow(PinEntryUiState(commandLabel = remote.pending.value?.let { CommandText.label(it.type) }.orEmpty()))
    private val _done = Channel<Unit>(Channel.BUFFERED)

    val state: StateFlow<PinEntryUiState> = ui.asStateFlow()

    /** Emits once when the command has started and the screen can close. */
    val done = _done.receiveAsFlow()

    fun onDigit(digit: Int) {
        val pin = buffer.add(digit)
        if (pin == null) {
            ui.update { it.copy(entered = buffer.size, error = null) }
            return
        }
        // A PIN is set after the first sign-in. Without one (a direct launch), any 4 digits pass.
        if (pinStore.hasPin.value && !pinStore.verify(pin)) {
            buffer.clear()
            ui.update { it.copy(entered = 0, error = "The PIN is incorrect.") }
            return
        }
        ui.update { it.copy(entered = PinBuffer.LENGTH, error = null) }
        viewModelScope.launch {
            if (remote.confirm(pin)) _done.send(Unit) else {
                buffer.clear()
                ui.update { it.copy(entered = 0, error = "The command could not start. Try again.") }
            }
        }
    }

    fun onDelete() {
        buffer.delete()
        ui.update { it.copy(entered = buffer.size, error = null) }
    }

    /** Back from the PIN screen drops the pending command. */
    fun cancel() = remote.cancelPending()
}
