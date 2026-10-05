package com.drivelink.demo

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.domain.repository.AccountRepository
import com.drivelink.core.network.inspector.NetworkInspector
import com.drivelink.core.settings.PendingExternalConfig
import com.drivelink.core.testing.DriveLinkTestRunner
import com.drivelink.demo.console.LocalNetworkAccess
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * The Phase 4 network hooks on a device, with the real Hilt graph (settings, network, data).
 * Two MockWebServer instances run on the device. Each test restores the active profile and
 * the scenario that it found, so the instrumentation arguments stay in effect.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NetworkHooksTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var demoConfig: DemoConfig
    @Inject lateinit var account: AccountRepository
    @Inject lateinit var inspector: NetworkInspector

    private val serverA = MockWebServer()
    private val serverB = MockWebServer()
    private lateinit var initialProfileId: String
    private lateinit var initialScenario: String

    @Before fun setUp() {
        hilt.inject()
        serverA.start()
        serverB.start()
        initialProfileId = demoConfig.activeProfile.value.id
        initialScenario = demoConfig.scenario.value
        runBlocking {
            demoConfig.upsertProfile(EndpointProfile(ID_A, "Device mock A", baseUrl(serverA)))
            demoConfig.upsertProfile(EndpointProfile(ID_B, "Device mock B", baseUrl(serverB)))
        }
    }

    @After fun tearDown() {
        runBlocking {
            demoConfig.setActiveProfile(initialProfileId)
            demoConfig.setScenario(initialScenario)
            demoConfig.deleteProfile(ID_A)
            demoConfig.deleteProfile(ID_B)
        }
        serverA.close()
        serverB.close()
    }

    @Test fun testConnection_isRecordedInInspector() = runBlocking {
        demoConfig.setActiveProfile(ID_A)
        serverA.enqueue(health())

        val result = account.getHealth()

        assertThat(result).isInstanceOf(Outcome.Ok::class.java)
        val entry = inspector.entries.value.first()
        assertThat(entry.method).isEqualTo("GET")
        assertThat(entry.url).isEqualTo(baseUrl(serverA) + "/v1/health")
        assertThat(entry.status).isEqualTo(200)
        assertThat(entry.profileName).isEqualTo("Device mock A")
        assertThat(entry.correlationId).isEqualTo(serverA.takeRequest().headers["X-Correlation-Id"])
    }

    @Test fun profileSwitch_redirectsNextCall_withoutRestart() = runBlocking {
        serverA.enqueue(health())
        serverB.enqueue(health())

        demoConfig.setActiveProfile(ID_A)
        account.getHealth()
        demoConfig.setActiveProfile(ID_B)
        account.getHealth()

        assertThat(serverA.requestCount).isEqualTo(1)
        assertThat(serverB.requestCount).isEqualTo(1)
        val urls = inspector.entries.value.map { it.url }
        assertThat(urls).containsExactly(baseUrl(serverB) + "/v1/health", baseUrl(serverA) + "/v1/health").inOrder()
    }

    @Test fun scenario_isSentAsHeader_andDefaultSendsNone() = runBlocking {
        demoConfig.setActiveProfile(ID_A)
        serverA.enqueue(health())
        serverA.enqueue(health())

        demoConfig.setScenario("slow-vehicle")
        account.getHealth()
        demoConfig.setScenario("default")
        account.getHealth()

        assertThat(serverA.takeRequest().headers["X-Scenario"]).isEqualTo("slow-vehicle")
        assertThat(serverA.takeRequest().headers["X-Scenario"]).isNull()
        assertThat(inspector.entries.value.map { it.scenario }).containsExactly("default", "slow-vehicle").inOrder()
    }

    /**
     * Checks only when the instrumentation argument `baseUrl` is set (for example the host
     * reference mock, `-Pandroid.testInstrumentationRunnerArguments.baseUrl=http://10.0.2.2:8080`).
     * Without it the test passes with no checks. (An Assume skip shows as a failure in the AGP
     * XML report.) [ExternalConfigTest] covers the mechanism without arguments.
     */
    @Test fun instrumentationArguments_activateExternalProfile_andReachHost() {
        val args = InstrumentationRegistry.getArguments()
        val baseUrl = args.getString(DriveLinkTestRunner.ARG_BASE_URL)
        if (baseUrl == null) {
            Log.i("NetworkHooksTest", "No baseUrl instrumentation argument: nothing to check.")
            return
        }
        // API 37+: a host on the local network (for example 10.0.2.2) needs ACCESS_LOCAL_NETWORK.
        if (LocalNetworkAccess.enforced()) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, LocalNetworkAccess.PERMISSION)
        }
        runBlocking { checkExternal(args.getString(DriveLinkTestRunner.ARG_SCENARIO), baseUrl) }
    }

    private suspend fun checkExternal(scenario: String?, baseUrl: String) {
        assertThat(PendingExternalConfig.Default.lastError).isNull()

        val active = demoConfig.activeProfile.value
        assertThat(active.id).isEqualTo("external")
        assertThat(active.baseUrl).isEqualTo(baseUrl.trimEnd('/'))
        scenario?.let { assertThat(demoConfig.scenario.value).isEqualTo(it) }

        val result = account.getHealth()
        assertThat(result).isInstanceOf(Outcome.Ok::class.java)
        assertThat(inspector.entries.value.first().url).startsWith(active.baseUrl + "/v1/health")
    }

    private fun baseUrl(server: MockWebServer) = server.url("/").toString().trimEnd('/')

    private fun health() = MockResponse.Builder()
        .code(200)
        .setHeader("Content-Type", "application/json")
        .body("""{"status":"UP","version":"0.0.0-device","service":"device-mock"}""")
        .build()

    private companion object {
        const val ID_A = "device-mock-a"
        const val ID_B = "device-mock-b"
    }
}
