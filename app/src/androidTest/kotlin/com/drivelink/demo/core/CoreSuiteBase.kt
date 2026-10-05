package com.drivelink.demo.core

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.PinStore
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.network.inspector.InspectorEntry
import com.drivelink.core.network.inspector.NetworkInspector
import com.drivelink.core.testing.DriveLinkTestRunner
import com.drivelink.demo.MainActivity
import com.drivelink.demo.grantLocalNetworkAccess
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Shared setup for the core suite: UI tests against a live endpoint, not a MockWebServer.
 *
 * The endpoint comes from the instrumentation argument `baseUrl`. [DriveLinkTestRunner] activates
 * the endpoint profile "External" with it. Without the argument every test is skipped (JUnit
 * assumption in an outermost rule), so `connectedCheck` without arguments still passes.
 *
 * Each test starts signed out, without a PIN, on the "default" scenario. A test sets its own
 * scenario with [useScenario]. The tear-down signs out and puts the scenario back.
 *
 * The cloud virtual service keeps state per VIN (mock/proto/README.md): a command in one test
 * changes the status in the next. So each test first puts the EV back to the seed state
 * ([resetVehicleState]). The local mock has no state and accepts the same calls.
 *
 * Timeouts are generous: a live service adds 300 to 800 ms per call and can stall.
 */
abstract class CoreSuiteBase {

    /** Outermost rule, so the skip reaches the runner as a plain assumption failure. */
    @get:Rule(order = 0) val requireEndpoint = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val baseUrl = InstrumentationRegistry.getArguments().getString(DriveLinkTestRunner.ARG_BASE_URL)
                assumeTrue("Core suite needs the instrumentation argument baseUrl", !baseUrl.isNullOrBlank())
                base.evaluate()
            }
        }
    }
    @get:Rule(order = 1) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 2) val compose = createEmptyComposeRule()

    @Inject lateinit var demoConfig: DemoConfig
    @Inject lateinit var sessions: SessionStore
    @Inject lateinit var pins: PinStore
    @Inject lateinit var garage: GarageRepository
    @Inject lateinit var inspector: NetworkInspector

    private var ready = false
    private lateinit var initialScenario: String
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUpSuite() {
        grantLocalNetworkAccess()
        hilt.inject()
        initialScenario = demoConfig.scenario.value
        runBlocking {
            garage.signOut()
            pins.clear()
            demoConfig.setScenario("default")
            demoConfig.setVin(null)
        }
        assertThat(demoConfig.activeProfile.value.id).isEqualTo("external")
        resetVehicleState(demoConfig.activeProfile.value.baseUrl)
        inspector.clear()
        ready = true
    }

    @After fun tearDownSuite() {
        if (!ready) return
        scenario?.close()
        runBlocking {
            runCatching { garage.signOut() }
            pins.clear()
            demoConfig.setScenario(initialScenario)
            demoConfig.setVin(null)
        }
    }


    /** Locks the EV and sets its charge limits to the seed values (80 / 90), outside the app. */
    private fun resetVehicleState(baseUrl: String) {
        send("POST", "$baseUrl/v1/vehicles/$EV_VIN/commands", """{"type":"LOCK","pin":"$PIN"}""")
        send("PUT", "$baseUrl/v1/vehicles/$EV_VIN/charge-settings", """{"acTargetPct":80,"dcTargetPct":90}""")
    }

    private fun send(method: String, url: String, body: String) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
            assertThat(connection.responseCode).isIn(200..299)
        } finally {
            connection.disconnect()
        }
    }

    protected fun useScenario(name: String) = runBlocking { demoConfig.setScenario(name) }

    protected fun launch() {
        scenario = ActivityScenario.launch(Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java))
    }

    protected fun waitFor(tag: String, timeoutMs: Long = TIMEOUT_MS) =
        compose.waitUntil(timeoutMs) { exists(tag) }

    protected fun waitUntilGone(tag: String, timeoutMs: Long = TIMEOUT_MS) =
        compose.waitUntil(timeoutMs) { !exists(tag) }

    protected fun exists(tag: String): Boolean = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    protected fun tap(tag: String) {
        waitFor(tag)
        val node = compose.onNodeWithTag(tag)
        // Scrolls when the node sits in a scrolling column; a node outside one needs no scroll.
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    protected fun typePin(pin: String) = pin.forEach { tap("pin_key_$it") }

    /** Fresh sign-in with the prefilled demo credentials, then PIN setup (1234). Ends on Home. */
    protected fun signInAndSetPin() {
        waitFor("screen_login")
        tap("login_submit")
        waitFor("screen_pin_setup")
        typePin(PIN) // create
        typePin(PIN) // confirm
        waitFor("screen_home")
    }

    /** The text of the node with [tag], or null when the node does not exist. */
    protected fun textOf(tag: String): String? {
        val node = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull() ?: return null
        return node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
    }

    /** The stage line of the command card on Home: "Sending command…", "Waiting for vehicle…", "Done", a failure. */
    protected fun stageText(): String? = textOf("command_stage")

    protected fun waitForStage(timeoutMs: Long = TIMEOUT_MS, accept: (String) -> Boolean): String {
        var seen: String? = null
        compose.waitUntil(timeoutMs) {
            seen = stageText()?.takeIf(accept)
            seen != null
        }
        return seen!!
    }

    /** Taps a Home tile, enters the PIN and waits for the command card on Home. */
    protected fun sendFromTile(tileTag: String) {
        tap(tileTag)
        waitFor("screen_pin")
        typePin(PIN)
        waitFor("command_progress")
        waitFor("screen_home")
    }

    /** The newest inspector entry that matches [method] and a URL path suffix. */
    protected fun lastCall(method: String, pathEnd: String): InspectorEntry? =
        inspector.entries.value.firstOrNull { it.method == method && it.url.substringBefore('?').endsWith(pathEnd) }

    companion object {
        /** Default wait for a screen or a call on a live endpoint. */
        const val TIMEOUT_MS = 30_000L
        const val PIN = "1234"
        const val EV_VIN = "DLEV26AURA0000101"
    }
}
