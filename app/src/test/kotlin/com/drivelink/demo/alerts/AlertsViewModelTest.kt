package com.drivelink.demo.alerts

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.Alert
import com.drivelink.core.domain.model.AlertSeverity
import com.drivelink.core.domain.model.AlertType
import com.drivelink.demo.AURORA
import com.drivelink.demo.MainDispatcherRule
import com.drivelink.demo.screenload.StageBExamples
import com.drivelink.demo.screenload.StageBHarness
import com.drivelink.demo.screenload.networkError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class AlertsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = StageBHarness()

    private fun viewModel() = AlertsViewModel(h.garage, h.alerts, h.config)

    private fun alert(id: String, read: Boolean, at: String = "2026-10-02T06:00:00Z", severity: AlertSeverity = AlertSeverity.INFO) =
        Alert(id, AURORA, AlertType.CHARGE_COMPLETE, severity, "Title $id", "Body $id", at, read)

    private val three = listOf(alert("a1", false, "2026-10-02T06:00:00Z"), alert("a2", false, "2026-10-02T08:00:00Z"), alert("a3", true, "2026-10-01T08:00:00Z"))

    @Test fun loadsAllAlerts_newestFirst_withUnreadFlags() {
        val state = viewModel().state.value

        assertThat(state.error).isNull()
        assertThat(state.loading).isFalse()
        assertThat(state.rows.map { it.id }).containsExactly("alr-901", "alr-900").inOrder()
        assertThat(state.rows.map { it.unread }).containsExactly(true, false).inOrder()
        assertThat(state.unreadCount).isEqualTo(1)
        assertThat(state.rows[0].title).isEqualTo("Charge complete")
        assertThat(state.rows[0].vehicle).isEqualTo("2026 Aurora EV")
        assertThat(state.rows[1].vehicle).isEqualTo("2025 Solace")
        assertThat(state.rows[0].severity).isEqualTo(AlertSeverity.INFO)
        assertThat(h.alerts.listCalls).isAtLeast(1)
    }

    @Test fun afterTheLoad_theHomeBellShowsTheUnreadCount() {
        h.alerts.alerts = Outcome.Ok(three)

        viewModel()

        assertThat(h.garage.state.value.unreadAlerts).isEqualTo(2)
    }

    @Test fun scenarioAlerts_showWarnings() {
        listOf("low-battery" to "alr-910", "door-ajar" to "alr-920", "tire-low" to "alr-930").forEach { (scenario, id) ->
            val harness = StageBHarness()
            harness.alerts.alerts = Outcome.Ok(StageBExamples.alerts("200.$scenario"))

            val state = AlertsViewModel(harness.garage, harness.alerts, harness.config).state.value

            assertThat(state.rows.map { it.id }).containsExactly(id)
            assertThat(state.rows.single().severity).isEqualTo(AlertSeverity.WARN)
            assertThat(state.rows.single().unread).isTrue()
            assertThat(harness.garage.state.value.unreadAlerts).isEqualTo(1)
        }
    }

    @Test fun timeAgo_usesTheClock() {
        val rows = alertRows(listOf(alert("a1", false, "2026-10-02T06:12:00Z")), emptyList(), Instant.parse("2026-10-02T09:12:00Z"))

        assertThat(rows.single().timeAgo).isEqualTo("3 hr ago")
        assertThat(rows.single().vehicle).isNull()
        assertThat(alertRows(listOf(alert("a1", false, "not a time")), emptyList()).single().timeAgo).isEmpty()
    }

    @Test fun emptyList_isEmptyState() {
        h.alerts.alerts = Outcome.Ok(emptyList())

        val state = viewModel().state.value

        assertThat(state.empty).isTrue()
        assertThat(state.hasData).isTrue()
        assertThat(state.rows).isEmpty()
        assertThat(h.garage.state.value.unreadAlerts).isEqualTo(0)
    }

    @Test fun tapOnUnread_marksReadAtOnce_andUpdatesTheBell() {
        h.alerts.alerts = Outcome.Ok(three)
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        h.alerts.markGate = gate

        vm.markRead("a1")

        // The request has not finished: the row is already read.
        val during = vm.state.value
        assertThat(during.rows.single { it.id == "a1" }.unread).isFalse()
        assertThat(during.unreadCount).isEqualTo(1)
        assertThat(h.garage.state.value.unreadAlerts).isEqualTo(1)
        assertThat(h.alerts.marked).containsExactly("a1")

        gate.complete(Unit)

        assertThat(vm.state.value.rows.single { it.id == "a1" }.unread).isFalse()
        assertThat(vm.state.value.markError).isNull()
        assertThat(h.garage.state.value.unreadAlerts).isEqualTo(1)
    }

    @Test fun markReadFails_revertsTheRow_andTheBell_andShowsTheError() {
        h.alerts.alerts = Outcome.Ok(three)
        h.alerts.markResults["a1"] = Outcome.Err(AppError.Server(500, "cid-mark"))
        val vm = viewModel()

        vm.markRead("a1")

        val state = vm.state.value
        assertThat(state.rows.single { it.id == "a1" }.unread).isTrue()
        assertThat(state.unreadCount).isEqualTo(2)
        assertThat(h.garage.state.value.unreadAlerts).isEqualTo(2)
        assertThat(state.markError?.message).isEqualTo("Something went wrong on our side.")
        assertThat(state.markError?.correlationId).isEqualTo("cid-mark")
        assertThat(h.sessions.session.value).isNotNull()

        vm.dismissMarkError()
        assertThat(vm.state.value.markError).isNull()
    }

    @Test fun everyMarkReadErrorClass_reverts() {
        listOf<AppError>(
            networkError("c1"),
            AppError.RateLimited(30, "c2"),
            AppError.NotFound("c3"),
            AppError.Parse("x", "c4"),
        ).forEach { error ->
            val harness = StageBHarness()
            harness.alerts.alerts = Outcome.Ok(three)
            harness.alerts.markResults["a2"] = Outcome.Err(error)
            val vm = AlertsViewModel(harness.garage, harness.alerts, harness.config)

            vm.markRead("a2")

            assertThat(vm.state.value.rows.single { it.id == "a2" }.unread).isTrue()
            assertThat(vm.state.value.markError?.correlationId).isEqualTo(error.correlationId)
            assertThat(harness.garage.state.value.unreadAlerts).isEqualTo(2)
        }
    }

    @Test fun rateLimitedMarkRead_showsTheRetryAfter() {
        h.alerts.alerts = Outcome.Ok(three)
        h.alerts.markResults["a1"] = Outcome.Err(AppError.RateLimited(30, "cid-429"))
        val vm = viewModel()

        vm.markRead("a1")

        assertThat(vm.state.value.markError?.message).isEqualTo("Too many requests. Try again in 30 seconds.")
    }

    @Test fun markReadUnauthorized_endsSession_andReverts() {
        h.alerts.alerts = Outcome.Ok(three)
        h.alerts.markResults["a1"] = Outcome.Err(AppError.Unauthorized("cid-401"))
        val vm = viewModel()

        vm.markRead("a1")

        assertThat(h.sessions.session.value).isNull()
        assertThat(vm.state.value.rows.single { it.id == "a1" }.unread).isTrue()
    }

    @Test fun tapOnReadAlert_sendsNothing() {
        h.alerts.alerts = Outcome.Ok(three)
        val vm = viewModel()

        vm.markRead("a3")
        vm.markRead("unknown")

        assertThat(h.alerts.marked).isEmpty()
        assertThat(h.garage.state.value.unreadAlerts).isEqualTo(2)
    }

    @Test fun secondTapWhileSending_sendsOnce() {
        h.alerts.alerts = Outcome.Ok(three)
        val vm = viewModel()
        h.alerts.markGate = CompletableDeferred()

        vm.markRead("a1")
        vm.markRead("a1")

        assertThat(h.alerts.marked).containsExactly("a1")
    }

    @Test fun markAllRead_marksEveryUnreadAlert() {
        h.alerts.alerts = Outcome.Ok(three)
        val vm = viewModel()

        vm.markAllRead()

        assertThat(h.alerts.marked).containsExactly("a1", "a2")
        assertThat(vm.state.value.unreadCount).isEqualTo(0)
        assertThat(h.garage.state.value.unreadAlerts).isEqualTo(0)
    }

    @Test fun markAllRead_revertsOnlyTheFailedAlert() {
        h.alerts.alerts = Outcome.Ok(three)
        h.alerts.markResults["a2"] = Outcome.Err(AppError.Server(500, "cid-2"))
        val vm = viewModel()

        vm.markAllRead()

        assertThat(vm.state.value.rows.filter { it.unread }.map { it.id }).containsExactly("a2")
        assertThat(h.garage.state.value.unreadAlerts).isEqualTo(1)
        assertThat(vm.state.value.markError?.correlationId).isEqualTo("cid-2")
    }

    @Test fun networkError_showsError_thenRetryLoads() {
        h.alerts.alerts = Outcome.Err(networkError("cid-9"))
        val vm = viewModel()

        val failed = vm.state.value
        assertThat(failed.hasData).isFalse()
        assertThat(failed.loading).isFalse()
        assertThat(failed.error).isInstanceOf(AppError.Network::class.java)
        assertThat(failed.error?.correlationId).isEqualTo("cid-9")

        h.alerts.alerts = Outcome.Ok(three)
        vm.refresh()

        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.rows).hasSize(3)
        assertThat(h.garage.state.value.unreadAlerts).isEqualTo(2)
    }

    @Test fun serverRateLimitAndParseErrors_keepTheirType() {
        listOf<AppError>(
            AppError.Server(500, "c1"),
            AppError.RateLimited(30, "c2"),
            AppError.Parse("alerts", "c3"),
        ).forEach { error ->
            val harness = StageBHarness().also { it.alerts.alerts = Outcome.Err(error) }
            val state = AlertsViewModel(harness.garage, harness.alerts, harness.config).state.value
            assertThat(state.error).isEqualTo(error)
            assertThat(state.hasData).isFalse()
            assertThat(harness.sessions.session.value).isNotNull()
        }
    }

    @Test fun failedRefresh_keepsTheOldRows() {
        h.alerts.alerts = Outcome.Ok(three)
        val vm = viewModel()
        h.alerts.alerts = Outcome.Err(AppError.Server(500, "cid-5"))

        vm.refresh()

        assertThat(vm.state.value.rows).hasSize(3)
        assertThat(vm.state.value.error?.correlationId).isEqualTo("cid-5")
    }

    @Test fun unauthorized_endsSession_andSetsLoginNotice() {
        h.alerts.alerts = Outcome.Err(AppError.Unauthorized("cid-401"))

        viewModel()

        assertThat(h.sessions.session.value).isNull()
        assertThat(h.garage.signInNotice.value).isEqualTo("Your session has expired. Sign in again.")
    }
}
