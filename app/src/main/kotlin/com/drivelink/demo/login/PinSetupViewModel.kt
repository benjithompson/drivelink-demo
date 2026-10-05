package com.drivelink.demo.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.domain.config.PinStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Digits typed so far on a PIN pad. The pad completes at [LENGTH] digits. */
class PinBuffer {
    private val digits = StringBuilder()

    val size: Int get() = digits.length

    /** Adds [digit]. Returns the PIN when this was the last digit, else null. */
    fun add(digit: Int): String? {
        if (digits.length >= LENGTH) return null
        digits.append(digit)
        return if (digits.length == LENGTH) digits.toString() else null
    }

    fun delete() {
        if (digits.isNotEmpty()) digits.deleteCharAt(digits.length - 1)
    }

    fun clear() = digits.clear()

    companion object {
        const val LENGTH = 4
    }
}

data class PinSetupUiState(
    val confirming: Boolean = false,
    val entered: Int = 0,
    val error: String? = null,
) {
    val title: String get() = if (confirming) "Confirm your PIN" else "Create a PIN"
}

/** First sign-in: the user chooses a 4-digit PIN and types it twice. */
@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val pinStore: PinStore,
) : ViewModel() {

    private val buffer = PinBuffer()
    private var first: String? = null
    private val ui = MutableStateFlow(PinSetupUiState())
    private val _done = Channel<Unit>(Channel.BUFFERED)

    val state: StateFlow<PinSetupUiState> = ui.asStateFlow()

    /** Emits once, when the PIN is stored. */
    val done = _done.receiveAsFlow()

    fun onDigit(digit: Int) {
        val pin = buffer.add(digit)
        if (pin == null) {
            ui.update { it.copy(entered = buffer.size, error = null) }
            return
        }
        val firstPin = first
        if (firstPin == null) {
            first = pin
            buffer.clear()
            ui.value = PinSetupUiState(confirming = true)
        } else if (firstPin == pin) {
            viewModelScope.launch {
                pinStore.set(pin)
                ui.update { it.copy(entered = PinBuffer.LENGTH) }
                _done.send(Unit)
            }
        } else {
            first = null
            buffer.clear()
            ui.value = PinSetupUiState(error = "The PINs do not match. Try again.")
        }
    }

    fun onDelete() {
        buffer.delete()
        ui.update { it.copy(entered = buffer.size, error = null) }
    }
}
