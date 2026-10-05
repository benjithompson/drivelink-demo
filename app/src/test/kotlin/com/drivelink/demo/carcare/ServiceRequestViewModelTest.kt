package com.drivelink.demo.carcare

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.ServiceRequest
import com.drivelink.core.domain.model.ServiceRequestStatus
import com.drivelink.demo.AURORA
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.SOLACE
import com.drivelink.demo.screenload.StageBExamples
import com.drivelink.demo.screenload.StageBHarness
import com.drivelink.demo.screenload.networkError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ServiceRequestViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = StageBHarness()
    private val clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)

    private fun viewModel() = ServiceRequestViewModel(h.garage, h.vehicles, h.config, clock)

    private val nextWeek = LocalDate.of(2026, 10, 10)

    @Test fun loadsTheCenter_andTheItemsWithDueItemsFirst() {
        h.config.vin.value = SOLACE

        val state = viewModel().state.value

        assertThat(state.loading).isFalse()
        assertThat(state.center?.name).isEqualTo("Harborview Auto Service")
        assertThat(state.options.map { it.id }).containsExactly("svc-21", "svc-22", "svc-23").inOrder()
        assertThat(state.options.map { it.checked }).containsExactly(false, false, false)
        assertThat(state.minDate).isEqualTo(LocalDate.of(2026, 10, 4))
        assertThat(state.maxDate).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(state.canSubmit).isTrue()
    }

    @Test fun dueItemsComeBeforeUpcomingItems() {
        val base = StageBExamples.maintenance("200.default-ice")
        h.vehicles.maintenance[AURORA] = Outcome.Ok(base.copy(items = base.items.reversed()))

        assertThat(viewModel().state.value.options.map { it.id }).containsExactly("svc-22", "svc-21", "svc-23").inOrder()
    }

    @Test fun submitWithNothing_showsBothErrors_andSendsNothing() {
        val vm = viewModel()

        vm.submit()

        val state = vm.state.value
        assertThat(state.errors.date).isEqualTo("Choose a date.")
        assertThat(state.errors.services).isEqualTo("Choose at least one service.")
        assertThat(h.vehicles.serviceRequests).isEmpty()
        assertThat(state.confirmation).isNull()
    }

    @Test fun dateWithoutService_andServiceWithoutDate_eachShowOneError() {
        val vm = viewModel()
        vm.setDate(nextWeek)
        vm.submit()
        assertThat(vm.state.value.errors.date).isNull()
        assertThat(vm.state.value.errors.services).isNotNull()

        val other = viewModel()
        other.toggleService("svc-11")
        other.submit()
        assertThat(other.state.value.errors.date).isNotNull()
        assertThat(other.state.value.errors.services).isNull()
        assertThat(h.vehicles.serviceRequests).isEmpty()
    }

    @Test fun editingAField_clearsItsError() {
        val vm = viewModel()
        vm.submit()

        vm.setDate(nextWeek)
        assertThat(vm.state.value.errors.date).isNull()
        assertThat(vm.state.value.errors.services).isNotNull()

        vm.toggleOther()
        assertThat(vm.state.value.errors.services).isNull()
    }

    @Test fun dateOutsideTomorrowToThirtyDays_isIgnored() {
        val vm = viewModel()

        vm.setDate(LocalDate.of(2026, 10, 3))
        assertThat(vm.state.value.date).isNull()
        vm.setDate(LocalDate.of(2026, 11, 3))
        assertThat(vm.state.value.date).isNull()
        vm.setDate(LocalDate.of(2026, 10, 4))
        assertThat(vm.state.value.date).isEqualTo(LocalDate.of(2026, 10, 4))
        vm.setDate(LocalDate.of(2026, 11, 2))
        assertThat(vm.state.value.date).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(vm.state.value.dateText).isEqualTo("Mon, Nov 2, 2026")
    }

    @Test fun submit_sendsTheRequest_andShowsTheConfirmation() {
        val vm = viewModel()
        vm.setDate(nextWeek)
        vm.toggleService("svc-12")
        vm.setSlot(TimeSlot.Morning)
        vm.setNotes("Please check the brakes.")

        vm.submit()

        val (vin, request) = h.vehicles.serviceRequests.single()
        assertThat(vin).isEqualTo(AURORA)
        assertThat(request.serviceCenterId).isEqualTo("sc-204")
        assertThat(request.preferredDate).isEqualTo("2026-10-10")
        assertThat(request.itemIds).containsExactly("svc-12")
        assertThat(request.notes).isEqualTo("Preferred time: Morning (8-11 am)\nPlease check the brakes.")
        val confirmation = requireNotNull(vm.state.value.confirmation)
        assertThat(confirmation.requestId).isEqualTo("srq-8812")
        assertThat(confirmation.statusLabel).isEqualTo("Requested")
        assertThat(confirmation.dateText).isEqualTo("Sat, Oct 10, 2026")
        assertThat(confirmation.centerName).isEqualTo("Harborview Auto Service")
        assertThat(vm.state.value.submitting).isFalse()
        assertThat(vm.state.value.canSubmit).isFalse()
    }

    @Test fun otherOnly_sendsNoItemIds_andMarksTheNotes() {
        val vm = viewModel()
        vm.setDate(nextWeek)
        vm.toggleOther()

        vm.submit()

        val request = h.vehicles.serviceRequests.single().second
        assertThat(request.itemIds).isNull()
        assertThat(request.notes).isEqualTo("Other service requested")
    }

    @Test fun noNotesAndNoSlot_sendNullNotes() {
        val vm = viewModel()
        vm.setDate(nextWeek)
        vm.toggleService("svc-11")

        vm.submit()

        assertThat(h.vehicles.serviceRequests.single().second.notes).isNull()
    }

    @Test fun slot_togglesOffOnASecondTap() {
        val vm = viewModel()
        vm.setSlot(TimeSlot.Midday)
        assertThat(vm.state.value.slot).isEqualTo(TimeSlot.Midday)
        vm.setSlot(TimeSlot.Afternoon)
        assertThat(vm.state.value.slot).isEqualTo(TimeSlot.Afternoon)
        vm.setSlot(TimeSlot.Afternoon)
        assertThat(vm.state.value.slot).isNull()
    }

    @Test fun notes_stopAt400Characters() {
        val vm = viewModel()
        vm.setNotes("x".repeat(450))

        assertThat(vm.state.value.notes).hasLength(NOTES_LIMIT)
        // The slot line and the "Other" line stay under the API limit of 500 characters.
        val notes = buildNotes(TimeSlot.Midday, true, vm.state.value.notes)
        assertThat(notes!!.length).isAtMost(500)
    }

    @Test fun everyErrorClass_keepsTheForm_andShowsMessageAndCorrelationId() {
        listOf<Pair<AppError, String>>(
            AppError.Server(500, "c1") to "Something went wrong on our side.",
            AppError.RateLimited(30, "c2") to "Too many requests. Try again in 30 seconds.",
            networkError("c3") to "Cannot reach the server. Check the connection.",
            AppError.InvalidRequest(400, "c4", "preferredDate is not allowed.") to "preferredDate is not allowed.",
            AppError.Parse("requestId", "c5") to "The server sent data that the app cannot read.",
            AppError.NotFound("c6") to "Not found.",
        ).forEach { (error, message) ->
            val harness = StageBHarness()
            harness.vehicles.serviceResult = Outcome.Err(error)
            val vm = ServiceRequestViewModel(harness.garage, harness.vehicles, harness.config, clock)
            vm.setDate(nextWeek)
            vm.toggleService("svc-11")
            vm.setNotes("Keep me")

            vm.submit()

            val state = vm.state.value
            assertThat(state.submitError?.message).isEqualTo(message)
            assertThat(state.submitError?.correlationId).isEqualTo(error.correlationId)
            assertThat(state.confirmation).isNull()
            assertThat(state.submitting).isFalse()
            assertThat(state.date).isEqualTo(nextWeek)
            assertThat(state.options.single { it.id == "svc-11" }.checked).isTrue()
            assertThat(state.notes).isEqualTo("Keep me")
            assertThat(harness.sessions.session.value).isNotNull()
        }
    }

    @Test fun retryAfterAnError_succeeds_andClearsTheError() {
        val vm = viewModel()
        vm.setDate(nextWeek)
        vm.toggleOther()
        h.vehicles.serviceResult = Outcome.Err(AppError.Server(500, "c1"))
        vm.submit()
        assertThat(vm.state.value.submitError).isNotNull()

        h.vehicles.serviceResult = Outcome.Ok(ServiceRequest("srq-1", ServiceRequestStatus.CONFIRMED))
        vm.submit()

        assertThat(vm.state.value.submitError).isNull()
        assertThat(vm.state.value.confirmation?.requestId).isEqualTo("srq-1")
        assertThat(vm.state.value.confirmation?.statusLabel).isEqualTo("Confirmed")
        assertThat(h.vehicles.serviceRequests).hasSize(2)
    }

    @Test fun unauthorized_endsSession_andSetsLoginNotice() {
        h.vehicles.serviceResult = Outcome.Err(AppError.Unauthorized("cid-401"))
        val vm = viewModel()
        vm.setDate(nextWeek)
        vm.toggleOther()

        vm.submit()

        assertThat(h.sessions.session.value).isNull()
        assertThat(h.garage.signInNotice.value).isEqualTo("Your session has expired. Sign in again.")
    }

    @Test fun secondTapWhileSending_sendsOnlyOneRequest() {
        val gate = CompletableDeferred<Unit>()
        h.vehicles.serviceGate = gate
        val vm = viewModel()
        vm.setDate(nextWeek)
        vm.toggleOther()

        vm.submit()
        assertThat(vm.state.value.submitting).isTrue()
        assertThat(vm.state.value.canSubmit).isFalse()
        vm.submit()
        gate.complete(Unit)

        assertThat(h.vehicles.serviceRequests).hasSize(1)
        assertThat(vm.state.value.confirmation).isNotNull()
    }

    @Test fun afterTheConfirmation_aSubmitDoesNothing() {
        val vm = viewModel()
        vm.setDate(nextWeek)
        vm.toggleOther()
        vm.submit()

        vm.submit()

        assertThat(h.vehicles.serviceRequests).hasSize(1)
    }

    @Test fun noPreferredCenter_blocksTheForm() {
        val base = StageBExamples.maintenance()
        h.vehicles.maintenance[AURORA] = Outcome.Ok(base.copy(preferredServiceCenter = null))
        val vm = viewModel()
        vm.setDate(nextWeek)
        vm.toggleOther()

        assertThat(vm.state.value.center).isNull()
        assertThat(vm.state.value.canSubmit).isFalse()
        vm.submit()

        assertThat(vm.state.value.submitError?.message).isEqualTo("No preferred service center is set for this vehicle.")
        assertThat(h.vehicles.serviceRequests).isEmpty()
    }

    @Test fun loadError_showsErrorOnly_thenReloadFixesIt() {
        h.vehicles.maintenance[AURORA] = Outcome.Err(AppError.Server(500, "cid-load"))
        val vm = viewModel()

        assertThat(vm.state.value.loadError?.correlationId).isEqualTo("cid-load")
        assertThat(vm.state.value.center).isNull()

        h.vehicles.maintenance[AURORA] = Outcome.Ok(StageBExamples.maintenance())
        vm.reload()

        assertThat(vm.state.value.loadError).isNull()
        assertThat(vm.state.value.center).isNotNull()
    }

    @Test fun loadUnauthorized_endsSession() {
        h.vehicles.maintenance[AURORA] = Outcome.Err(AppError.Unauthorized("cid-401"))

        viewModel()

        assertThat(h.sessions.session.value).isNull()
    }

    @Test fun buildNotes_joinsTheParts() {
        assertThat(buildNotes(null, false, "  ")).isNull()
        assertThat(buildNotes(TimeSlot.Afternoon, false, "")).isEqualTo("Preferred time: Afternoon (2-5 pm)")
        assertThat(buildNotes(TimeSlot.Morning, true, " Hi ")).isEqualTo("Preferred time: Morning (8-11 am)\nOther service requested\nHi")
    }
}
