package com.drivelink.demo.login

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.CommandType
import com.drivelink.demo.FakeCommandRepository
import com.drivelink.demo.FakePinStore
import com.drivelink.demo.GarageHarness
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.remote.PinEntryViewModel
import com.drivelink.demo.remote.RemoteCommands
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()
    private var pins = FakePinStore()

    private fun viewModel() = LoginViewModel(h.auth, pins, h.garage)

    @Test fun form_startsWithTheDemoCredentials() {
        val s = viewModel().state.value
        assertThat(s.email).isEqualTo("alex.rivera@drivelink.test")
        assertThat(s.password).isEqualTo("demo-password")
    }

    @Test fun firstSignIn_asksForPinSetup() = runBlocking {
        val vm = viewModel()
        vm.signIn()

        assertThat(vm.events.first()).isEqualTo(LoginEvent.SignedIn(needsPin = true))
        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.error).isNull()
        assertThat(h.sessions.session.value?.email).isEqualTo("alex.rivera@drivelink.test")
    }

    @Test fun laterSignIn_skipsPinSetup() = runBlocking {
        pins.set("1234")
        val vm = viewModel()
        vm.signIn()

        assertThat(vm.events.first()).isEqualTo(LoginEvent.SignedIn(needsPin = false))
    }

    @Test fun invalidCredentials_showsMessageAndCorrelationId_withoutEvent() {
        h.auth.loginResult = Outcome.Err(AppError.InvalidCredentials("cid-401"))
        val vm = viewModel()
        vm.signIn()

        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.error).isEqualTo(LoginError("The email or password is incorrect.", "cid-401"))
        assertThat(h.sessions.session.value?.accessToken).isEqualTo("at") // unchanged: no new session
    }

    @Test fun networkAndServerErrors_showTheirMessage() {
        listOf(
            AppError.Network(timeout = false, correlationId = "c1"),
            AppError.Server(500, "c2"),
        ).forEach { error ->
            h.auth.loginResult = Outcome.Err(error)
            val vm = viewModel()
            vm.signIn()
            assertThat(vm.state.value.error).isEqualTo(LoginError(error.message, error.correlationId))
        }
    }

    @Test fun editingTheForm_clearsTheError() {
        h.auth.loginResult = Outcome.Err(AppError.InvalidCredentials("c"))
        val vm = viewModel()
        vm.signIn()
        vm.onPassword("other")

        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.password).isEqualTo("other")
    }

    @Test fun blankFields_doNotCallTheServer() {
        val vm = viewModel()
        vm.onEmail("  ")
        vm.signIn()

        assertThat(h.auth.logins).isEqualTo(0)
        assertThat(vm.state.value.error?.message).isEqualTo("Enter your email and password.")
    }

    @Test fun expiredSession_notice_showsOnLogin() = runBlocking {
        h.garage.expireSession("Your session has expired. Sign in again.")
        val vm = viewModel()

        assertThat(vm.state.value.notice).isEqualTo("Your session has expired. Sign in again.")
        vm.signIn()
        vm.events.first()
        assertThat(vm.state.value.notice).isNull()
    }

    // PIN setup

    @Test fun pinSetup_createThenConfirm_storesThePin() = runBlocking {
        val vm = PinSetupViewModel(pins)
        assertThat(vm.state.value.title).isEqualTo("Create a PIN")
        listOf(1, 2, 3, 4).forEach(vm::onDigit)
        assertThat(vm.state.value.title).isEqualTo("Confirm your PIN")
        assertThat(vm.state.value.entered).isEqualTo(0)
        listOf(1, 2, 3, 4).forEach(vm::onDigit)

        vm.done.first()
        assertThat(pins.hasPin.value).isTrue()
        assertThat(pins.verify("1234")).isTrue()
    }

    @Test fun pinSetup_mismatch_startsOver() {
        val vm = PinSetupViewModel(pins)
        listOf(1, 2, 3, 4).forEach(vm::onDigit)
        listOf(4, 3, 2, 1).forEach(vm::onDigit)

        assertThat(vm.state.value.error).isEqualTo("The PINs do not match. Try again.")
        assertThat(vm.state.value.title).isEqualTo("Create a PIN")
        assertThat(pins.hasPin.value).isFalse()
    }

    @Test fun pinSetup_deleteRemovesTheLastDigit() {
        val vm = PinSetupViewModel(pins)
        vm.onDigit(1)
        vm.onDigit(2)
        vm.onDelete()
        assertThat(vm.state.value.entered).isEqualTo(1)
    }

    // PIN before a command

    private fun pinEntry(): Pair<PinEntryViewModel, RemoteCommands> {
        val remote = RemoteCommands(
            RunCommandUseCase(FakeCommandRepository(), TimingLog { _, _, _ -> }),
            h.garage, CoroutineScope(Dispatchers.Unconfined), 2500.milliseconds,
        )
        remote.request(CommandType.LOCK)
        return PinEntryViewModel(pins, remote) to remote
    }

    @Test fun commandPin_wrongPin_showsError_andKeepsTheCommandPending() {
        pins = FakePinStore("1234")
        val (vm, remote) = pinEntry()
        listOf(1, 1, 1, 1).forEach(vm::onDigit)

        assertThat(vm.state.value.error).isEqualTo("The PIN is incorrect.")
        assertThat(vm.state.value.entered).isEqualTo(0)
        assertThat(remote.pending.value).isNotNull()
        assertThat(remote.current.value).isNull()
    }

    @Test fun commandPin_correctPin_startsTheCommand() = runBlocking {
        pins = FakePinStore("1234")
        val (vm, remote) = pinEntry()
        runBlocking { h.garage.refresh() }
        listOf(1, 2, 3, 4).forEach(vm::onDigit)

        vm.done.first()
        assertThat(remote.pending.value).isNull()
        assertThat(remote.current.value?.label).isEqualTo("Lock")
    }
}
