package com.drivelink.demo.core

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Core suite: ten UI flows of the DriveLink demo app against a live endpoint (BlazeMeter service
 * virtualization on Perfecto, or the local reference mock). Select it by package:
 * `-Pandroid.testInstrumentationRunnerArguments.package=com.drivelink.demo.core` plus the
 * argument `baseUrl`. Without `baseUrl` every test is skipped.
 *
 * The tests are independent and order-free: each starts signed out, without a PIN, sets its own
 * scenario in code, and the tear-down restores the scenario. The tests do not use the Maps tab.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CoreSuiteTest : CoreSuiteBase() {

    /** a. Fresh sign-in and PIN setup end on Home. */
    @Test fun login_pinSetup_showsHome() {
        launch()
        signInAndSetPin()

        compose.onNodeWithTag("screen_home").assertIsDisplayed()
        waitFor("tile_lock")
        assertThat(pins.hasPin.value).isTrue()
        assertThat(sessions.session.value).isNotNull()
    }

    /** b. The dashboard shows level, range and the lock tile of the default EV. */
    @Test fun dashboard_showsLevelRangeAndLockState() {
        launch()
        signInAndSetPin()

        waitFor("stat_level")
        compose.onNodeWithTag("stat_level").assertTextContains("72", substring = true)
        compose.onNodeWithTag("stat_range").assertTextContains("226", substring = true)
        waitFor("tile_lock")
        compose.onNode(tileShowing("tile_lock", "Locked")).assertIsDisplayed()
        waitFor("status_chip")

        val status = requireNotNull(garage.state.value.status)
        assertThat(status.vin).isEqualTo(EV_VIN)
        assertThat(status.batteryPct).isEqualTo(72)
        assertThat(status.rangeMi).isEqualTo(226)
        assertThat(status.locked).isTrue()
    }

    /** c. Lock: the EV is unlocked in `door-ajar`, so the tile sends LOCK; Done, then the tile shows Locked. */
    @Test fun lock_succeeds_andTileShowsLocked() {
        useScenario("door-ajar")
        launch()
        signInAndSetPin()

        waitFor("tile_lock")
        compose.onNode(tileShowing("tile_lock", "Unlocked")).assertIsDisplayed()
        sendFromTile("tile_lock")

        waitForStage(timeoutMs = 60_000) { it == "Done" }
        waitUntilGone("command_progress") // the card goes away after the success hold
        compose.onNode(tileShowing("tile_lock", "Locked")).assertIsDisplayed()

        val body = requireNotNull(lastCall("POST", "/vehicles/$EV_VIN/commands")?.requestBody).compact()
        assertThat(body).contains("\"type\":\"LOCK\"")
        assertThat(body).contains("\"pin\":\"$PIN\"")
        assertThat(garage.state.value.status?.locked).isTrue()
    }

    /** d. Remote start with climate: 72 degrees, front defrost on, send, Done. */
    @Test fun remoteStart_withClimate_reachesDone() {
        launch()
        signInAndSetPin()

        tap("tile_climate")
        waitFor("screen_climate")
        waitFor("preset_0") // presets loaded
        // A preset can set other values, so move the form to 72 and front defrost on.
        repeat(30) {
            val temp = textOf("climate_temperature")
            if (temp == "72°") return@repeat
            val low = temp == "OFF" || temp == "LO" || (temp?.removeSuffix("°")?.toIntOrNull() ?: 0) < 72
            compose.onNodeWithContentDescription(if (low) "Raise temperature" else "Lower temperature").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("climate_temperature").assertTextEquals("72°")
        val toggle = compose.onNodeWithTag("toggle_front_defrost")
        runCatching { toggle.performScrollTo() }
        val on = toggle.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == ToggleableState.On
        if (!on) toggle.performClick()
        compose.onNodeWithTag("toggle_front_defrost").assertIsOn()

        tap("climate_start")
        waitFor("screen_pin")
        typePin(PIN)
        waitFor("screen_home")

        waitForStage { it == "Done" }
        val body = requireNotNull(lastCall("POST", "/vehicles/$EV_VIN/commands")?.requestBody).compact()
        assertThat(body).contains("\"type\":\"START\"")
        assertThat(body).contains("\"tempF\":72")
        assertThat(body).contains("\"frontDefrost\":true")
    }

    /** e. `slow-vehicle`: the command waits for the vehicle (8 pending polls), then reaches Done. */
    @Test fun slowVehicle_showsWaiting_thenDone() {
        useScenario("slow-vehicle")
        launch()
        signInAndSetPin()

        waitFor("tile_lock")
        sendFromTile("tile_lock")

        val waiting = waitForStage(timeoutMs = 30_000) { it.startsWith("Waiting for vehicle") }
        assertThat(waiting).startsWith("Waiting for vehicle")
        waitForStage(timeoutMs = 90_000) { it == "Done" }
        compose.onNodeWithTag("command_stage").assertIsDisplayed()
    }

    /** f. `command-fails`: the card shows the failure with the reason DOOR_OPEN and a Retry button. */
    @Test fun commandFails_showsReason() {
        useScenario("command-fails")
        launch()
        signInAndSetPin()

        waitFor("tile_lock")
        sendFromTile("tile_lock")

        val stage = waitForStage { it.startsWith("Command failed") }
        assertThat(stage).contains("A door is open")
        waitFor("command_retry")
        compose.onNodeWithTag("command_retry").assertIsDisplayed()
    }

    /** g. `vehicle-offline`: stale banner on Home, and a command attempt gets 409 and the offline message. */
    @Test fun vehicleOffline_showsStaleBanner_andOfflineCommandError() {
        useScenario("vehicle-offline")
        launch()
        signInAndSetPin()

        waitFor("banner_stale")
        compose.onNodeWithTag("banner_stale").assertIsDisplayed()
        assertThat(garage.state.value.stale).isTrue()

        sendFromTile("tile_lock")
        val stage = waitForStage { it.contains("offline", ignoreCase = true) }
        assertThat(stage).contains("offline")
        assertThat(lastCall("POST", "/vehicles/$EV_VIN/commands")?.status).isEqualTo(409)
    }

    /** h. `auth-expired` set after sign-in: the next load gets 401, the refresh fails, the app returns to Login. */
    @Test fun authExpired_returnsToLogin() {
        launch()
        signInAndSetPin()
        waitFor("stat_level")
        assertThat(sessions.session.value).isNotNull()

        useScenario("auth-expired")
        tap("row_status") // opens Status, which reloads for the new scenario

        waitFor("screen_login")
        waitFor("login_notice")
        compose.onNodeWithTag("login_notice").assertIsDisplayed()
        assertThat(sessions.session.value).isNull()
        assertThat(lastCall("POST", "/auth/refresh")?.status).isEqualTo(401)
    }

    /** i. EV charge limit: move the AC slider, the app saves it and shows Saved. */
    @Test fun chargeLimit_update_isSavedAndConfirmed() {
        launch()
        signInAndSetPin()

        tap("tile_charge")
        waitFor("screen_charging")
        waitFor("limit_ac", timeoutMs = 30_000)
        val current = requireNotNull(textOf("limit_ac_value")).removeSuffix("%").trim().toInt()
        val target = if (current == 90) 70 else 90

        val slider = compose.onNodeWithTag("limit_ac")
        runCatching { slider.performScrollTo() }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(target.toFloat()) }
        compose.onNodeWithTag("limit_ac_value").assertTextEquals("$target%")

        // The app saves 1 s after the last move; the live call adds its own delay.
        compose.waitUntil(30_000) { textOf("limits_status") == "Saved" }
        compose.onNodeWithTag("limits_status").performScrollTo().assertIsDisplayed()

        // The mock answers with fixed bodies, so check what the app sent, not a reload.
        val body = requireNotNull(lastCall("PUT", "/vehicles/$EV_VIN/charge-settings")?.requestBody).compact()
        assertThat(body).contains("\"acTargetPct\":$target")
    }

    /** j. The Demo console Network Inspector lists the calls of the session, and a row shows the correlation id. */
    @Test fun networkInspector_listsCalls_andShowsCorrelationId() {
        launch()
        signInAndSetPin()
        waitFor("stat_level") // status loaded

        tap("tab_menu")
        tap("menu_demo_console")
        waitFor("inspector.list")
        compose.onNodeWithTag("inspector.list").assertIsDisplayed()
        waitFor("inspector.row.0")

        val statusRow = hasContentDescription("/v1/vehicles/$EV_VIN/status", substring = true)
        compose.waitUntil(TIMEOUT_MS) { compose.onAllNodes(statusRow).fetchSemanticsNodes().isNotEmpty() }
        assertThat(lastCall("GET", "/vehicles/$EV_VIN/status")?.status).isEqualTo(200)

        compose.onAllNodes(statusRow)[0].performClick()
        waitFor("inspector.detail")
        compose.onNodeWithTag("inspector.detail.url").assertTextContains("/v1/vehicles/$EV_VIN/status", substring = true)
        compose.onNodeWithTag("inspector.detail.status").assertIsDisplayed()
        compose.onNodeWithText("Correlation id").performScrollTo().assertIsDisplayed()
        assertThat(lastCall("GET", "/vehicles/$EV_VIN/status")?.correlationId).isNotEmpty()
    }

    /** The tile column with the tag [tag] that holds the exact label [label]. */
    private fun tileShowing(tag: String, label: String): SemanticsMatcher =
        hasTestTag(tag) and hasAnyDescendant(hasText(label))

    private fun String.compact(): String = filterNot { it.isWhitespace() }
}
