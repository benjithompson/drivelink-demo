package com.drivelink.demo

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.config.AppPreferences
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.domain.config.PinStore
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.domain.config.ThemeMode
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
 * Phase 5 stage 2C screens on a device: Maps, Menu, Profile, Settings and the Demo console tools.
 * The map tiles need the internet; the tests check the marker, the card and the data only.
 * A MockWebServer on the device serves the contract examples ([ExampleServer]).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MapsMenuFlowTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var demoConfig: DemoConfig
    @Inject lateinit var sessions: SessionStore
    @Inject lateinit var pins: PinStore
    @Inject lateinit var garage: GarageRepository
    @Inject lateinit var preferences: AppPreferences

    private val server = ExampleServer()
    private lateinit var initialProfileId: String
    private lateinit var initialScenario: String
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
        hilt.inject()
        grantLocalNetworkAccess()
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
            preferences.setThemeMode(ThemeMode.LIGHT)
            // A PIN is set, so signing in skips PIN setup.
            pins.set("1234")
        }
    }

    @After fun tearDown() {
        scenario?.close()
        runBlocking {
            garage.signOut()
            pins.clear()
            preferences.setThemeMode(ThemeMode.SYSTEM)
            demoConfig.setActiveProfile(initialProfileId)
            demoConfig.setScenario(initialScenario)
            demoConfig.setVin(null)
            demoConfig.deleteProfile(PROFILE)
            runCatching { demoConfig.deleteProfile("stage-2c-profile") }
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
        // Scrolls when the node sits in a scrolling column; a node outside one needs no scroll.
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun signIn() {
        waitFor("screen_login")
        tap("login_submit")
        waitFor("screen_home")
    }

    private fun typePin(pin: String) = pin.forEach { tap("pin_key_$it") }

    @Test fun maps_showsTheMarker_theCard_andTheDistance() {
        launch()
        signIn()

        tap("tab_maps")
        waitFor("map_vehicle_card")
        compose.onNodeWithTag("map_vehicle_marker").assertIsDisplayed()
        compose.onNodeWithTag("map_vehicle_address").assertTextContains("120 Marina Way", substring = true)
        compose.onNodeWithTag("map_vehicle_distance").assertTextContains("0.3 mi", substring = true)
        tap("map_recenter")
        tap("map_refresh")
        waitFor("map_vehicle_card")
        assertThat(server.count("GET", "/v1/vehicles/${ExampleServer.AURORA_VIN}/location")).isAtLeast(2)
    }

    @Test fun maps_serverError_showsTheErrorCardWithRetry() {
        server.overrides["GET /v1/vehicles/${ExampleServer.AURORA_VIN}/location"] =
            500 to server.asset("getVehicleLocation/500.server-error.json")
        launch()
        signIn()

        tap("tab_maps")
        waitFor("error_state")
        compose.onNodeWithTag("error_correlation_id").assertIsDisplayed()
        server.overrides.remove("GET /v1/vehicles/${ExampleServer.AURORA_VIN}/location")
        tap("error_retry")
        waitFor("map_vehicle_card")
    }

    @Test fun menu_showsTheAccount_stubsOpenADialog_andSignOutAsksFirst() {
        launch()
        signIn()

        tap("tab_menu")
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("menu_user_name"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("menu_user_name", useUnmergedTree = true).assertTextContains("Alex Rivera")
        compose.onNodeWithTag("menu_user_email", useUnmergedTree = true).assertTextContains("alex.rivera@drivelink.test")
        tap("menu_digital_key")
        waitFor("menu_stub_dialog")
        tap("menu_stub_ok")

        tap("menu_sign_out")
        waitFor("sign_out_confirm")
        tap("sign_out_cancel")
        assertThat(sessions.session.value).isNotNull()
        tap("menu_sign_out")
        tap("sign_out_confirm_yes")
        waitFor("screen_login")
        assertThat(sessions.session.value).isNull()
        assertThat(pins.hasPin.value).isTrue()
    }

    @Test fun profile_showsTheAccountAndTheVehicle() {
        launch()
        signIn()

        tap("tab_menu")
        tap("menu_profile")
        waitFor("screen_profile")
        waitFor("profile_name")
        compose.onNodeWithTag("profile_name").assertTextContains("Alex Rivera")
        compose.onNodeWithTag("profile_units").assertTextContains("Miles", substring = true)
        compose.onNodeWithTag("profile_vehicle_vin").assertTextContains(ExampleServer.AURORA_VIN, substring = true)
    }

    @Test fun settings_themePersists_unitsShow_andChangePinWorks() {
        launch()
        signIn()

        tap("tab_menu")
        tap("menu_settings")
        waitFor("screen_settings")
        tap("settings_theme_dark")
        compose.waitUntil(5_000) { preferences.themeMode.value == ThemeMode.DARK }
        compose.onNodeWithTag("settings_units").assertIsDisplayed()

        tap("settings_change_pin")
        waitFor("settings_pin_step_current")
        typePin("1234")
        waitFor("settings_pin_step_new")
        typePin("4321")
        waitFor("settings_pin_step_confirm")
        typePin("4321")
        waitFor("settings_pin_changed")
        assertThat(pins.verify("4321")).isTrue()
    }

    @Test fun console_addsAndDeletesAProfile_andResetsTheSession() {
        launch("screen" to "console")

        tap("console.tools")
        tap("console.profile.add")
        waitFor("console.profile.editor")
        compose.onNodeWithTag("console.profile.field.name").performTextInput("Stage 2C profile")
        compose.onNodeWithTag("console.profile.field.baseUrl").performTextInput("http://10.0.2.2:9090")
        tap("console.profile.save")
        waitFor("screen_console")
        assertThat(demoConfig.profiles.value.map { it.id }).contains("stage-2c-profile")

        demoConfig.profiles.value.first { it.id == "stage-2c-profile" }.let {
            runBlocking { demoConfig.setActiveProfile(it.id) }
        }
        tap("console.profile.edit")
        waitFor("console.profile.editor")
        tap("console.profile.delete")
        tap("console.profile.delete.yes")
        waitFor("screen_console")
        assertThat(demoConfig.profiles.value.map { it.id }).doesNotContain("stage-2c-profile")

        runBlocking { demoConfig.setActiveProfile(PROFILE) }
        runBlocking { demoConfig.setScenario("server-error") }
        tap("console.reset_session")
        compose.waitUntil(10_000) { demoConfig.scenario.value == "default" }
        assertThat(pins.hasPin.value).isTrue()
    }

    private companion object {
        const val PROFILE = "stage-2c-device-examples"
    }
}
