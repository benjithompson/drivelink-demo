package com.drivelink.demo.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.PinStore
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A sign-in failure, ready to show. [correlationId] is null for a local check. */
data class LoginError(val message: String, val correlationId: String?)

data class LoginUiState(
    val email: String = DEMO_EMAIL,
    val password: String = DEMO_PASSWORD,
    val loading: Boolean = false,
    val error: LoginError? = null,
    /** Why the user is here again, for example "Your session has expired. Sign in again." */
    val notice: String? = null,
) {
    companion object {
        /** The demo credentials from the createToken example in api/openapi.yaml. */
        const val DEMO_EMAIL = "alex.rivera@drivelink.test"
        const val DEMO_PASSWORD = "demo-password"
    }
}

sealed interface LoginEvent {
    /** Signed in. [needsPin] is true on the first sign-in. */
    data class SignedIn(val needsPin: Boolean) : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val pinStore: PinStore,
    private val garage: GarageRepository,
) : ViewModel() {

    private val form = MutableStateFlow(LoginUiState())
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)

    val state: StateFlow<LoginUiState> = form.asStateFlow()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            garage.signInNotice.collect { notice -> form.update { it.copy(notice = notice) } }
        }
    }

    fun onEmail(value: String) = form.update { it.copy(email = value, error = null) }

    fun onPassword(value: String) = form.update { it.copy(password = value, error = null) }

    fun signIn() {
        val current = form.value
        if (current.loading) return
        if (current.email.isBlank() || current.password.isEmpty()) {
            form.update { it.copy(error = LoginError("Enter your email and password.", null)) }
            return
        }
        form.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = auth.login(current.email.trim(), current.password)) {
                is Outcome.Ok -> {
                    garage.consumeNotice()
                    form.update { it.copy(loading = false, notice = null) }
                    _events.send(LoginEvent.SignedIn(needsPin = !pinStore.hasPin.value))
                }
                is Outcome.Err -> form.update { it.copy(loading = false, error = result.error.toLoginError()) }
            }
        }
    }

    private fun AppError.toLoginError() = LoginError(message, correlationId)
}
