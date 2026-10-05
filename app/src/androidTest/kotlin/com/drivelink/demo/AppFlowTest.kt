package com.drivelink.demo

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.domain.config.PinStore
import com.drivelink.core.domain.config.SessionStore
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The main screens on a device, with the real Hilt graph and the real network stack.
 * A MockWebServer on the device serves the contract examples ([ExampleServer]).
 * Each test starts signed out, without a PIN, and puts the demo settings back at the end.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var demoConfig: DemoConfig
    @Inject lateinit var sessions: SessionStore
    @Inject lateinit var pins: PinStore
    @Inject lateinit var garage: GarageRepository

    private val server = ExampleServer()
    private lateinit var initialProfileId: String
    private lateinit var initialScenario: String
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
        hilt.inject()
        server.start()
        initialProfileId = demoConfig.activeProfile.value.id
        initialScenario = demoConfig.scenario.value
        runBlocking {
            garage.signOut()
            pins.clear()
            demoConfig.upsertProfile(EndpointProfile(PROFILE, "Device examples", server.baseUrl()))
            demoConfig.setActiveProfile(PROFILE)
            demoConfig.setScenario("default")
            demoConfig.setVin(null)
        }
    }

    @After fun tearDown() {
        scenario?.close()
        runBlocking {
            garage.signOut()
            pins.clear()
            demoConfig.setActiveProfile(initialProfileId)
            demoConfig.setScenario(initialScenario)
            demoConfig.setVin(null)
            demoConfig.deleteProfile(PROFILE)
        }
        server.close()
    }

    private fun launch(vararg extras: Pair<String, String>) {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
        extras.forEach { (k, v) -> intent.putExtra(k, v) }
        scenario = ActivityScenario.launch(intent)
    }

    private fun waitFor(tag: String, timeoutMs: Long = 15_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun tap(tag: String) {
        waitFor(tag)
        val node = compose.onNodeWithTag(tag)
        // Scrolls when the node sits in a scrolling column (the Menu tab); a node outside one needs no scroll.
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun typePin(pin: String) = pin.forEach { tap("pin_key_$it") }

    private fun signInAndSetPin() {
        waitFor("screen_login")
        tap("login_submit")
        waitFor("screen_pin_setup")
        typePin("1234") // create
        typePin("1234") // confirm
        waitFor("screen_home")
    }

    @Test fun signIn_pinSetup_home_lock_reachesDone() {
        // The vehicle is unlocked, so the Lock tile sends LOCK.
        server.overrides["GET /v1/vehicles/${ExampleServer.AURORA_VIN}/status"] =
            200 to server.asset("getVehicleStatus/200.default.json").replace("\"locked\": true", "\"locked\": false")
        launch()

        signInAndSetPin()
        waitFor("stat_level")
        compose.onNodeWithTag("stat_level").assertTextContains("72", substring = true)
        compose.onNodeWithTag("stat_range").assertTextContains("226", substring = true)
        assertThat(pins.hasPin.value).isTrue()

        tap("tile_lock")
        waitFor("screen_pin")
        typePin("1234")

        waitFor("command_progress")
        waitFor("screen_home")
        compose.waitUntil(20_000) { stageText() == "Done" }
        compose.onNodeWithTag("command_stage").assertIsDisplayed()

        val body = requireNotNull(server.bodyOf("POST", "/v1/vehicles/${ExampleServer.AURORA_VIN}/commands"))
        assertThat(body).contains("\"LOCK\"")
        assertThat(body).contains("\"1234\"")
        // The status reloads after the command succeeded.
        assertThat(server.count("GET", "/v1/vehicles/${ExampleServer.AURORA_VIN}/status")).isAtLeast(2)
    }

    @Test fun wrongPassword_showsErrorWithCorrelationId() {
        server.overrides["POST /v1/auth/token"] = 401 to server.asset("createToken/401.invalid-credentials.json")
        launch()

        waitFor("screen_login")
        tap("login_submit")
        waitFor("login_error")
        compose.onNodeWithTag("login_correlation_id").assertIsDisplayed()
        assertThat(sessions.session.value).isNull()
    }

    @Test fun secondSignIn_skipsPinSetup() {
        launch()
        signInAndSetPin()

        tap("tab_menu")
        tap("menu_sign_out")
        tap("sign_out_confirm_yes")
        waitFor("screen_login")
        assertThat(sessions.session.value).isNull()

        tap("login_submit")
        waitFor("screen_home")
        assertThat(pins.hasPin.value).isTrue()
    }

    @Test fun vehicleSwitcher_listsBothCars_andShowsFuelForTheGasCar() {
        launch()
        signInAndSetPin()
        waitFor("tile_charge")

        tap("vehicle_switcher")
        waitFor("vehicle_picker")
        compose.onNodeWithTag("vehicle_option_${ExampleServer.AURORA_VIN}").assertIsDisplayed()
        compose.onNodeWithTag("vehicle_option_${ExampleServer.ICE_VIN}").assertIsDisplayed()
        tap("vehicle_option_${ExampleServer.ICE_VIN}")

        waitFor("tile_fuel")
        assertThat(compose.onAllNodes(hasTestTag("tile_charge")).fetchSemanticsNodes()).isEmpty()
        compose.onNodeWithTag("stat_level").assertTextContains("62", substring = true)
        assertThat(demoConfig.vin.value).isEqualTo(ExampleServer.ICE_VIN)
    }

    private fun stageText(): String? {
        val nodes = compose.onAllNodes(hasTestTag("command_stage")).fetchSemanticsNodes()
        val node = nodes.firstOrNull() ?: return null
        return node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    }

    private companion object {
        const val PROFILE = "device-examples"
    }
}
