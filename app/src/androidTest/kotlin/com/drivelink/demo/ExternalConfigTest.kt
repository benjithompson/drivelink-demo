package com.drivelink.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.BuiltInProfiles
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.repository.AccountRepository
import com.drivelink.core.settings.PendingExternalConfig
import com.drivelink.core.testing.DriveLinkTestRunner
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
 * The path that DriveLinkTestRunner uses for the `baseUrl` and `scenario` arguments, without
 * real arguments: the values go to [PendingExternalConfig.Default] before the Hilt component
 * exists, and the settings store applies them when the component creates it.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExternalConfigTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var demoConfig: DemoConfig
    @Inject lateinit var account: AccountRepository

    private val server = MockWebServer()

    @Before fun setUp() {
        server.start()
        PendingExternalConfig.Default.set(server.url("/").toString(), "fast")
        hilt.inject()
    }

    @After fun tearDown() {
        // Put back what the real instrumentation arguments set, or the defaults.
        val args = InstrumentationRegistry.getArguments()
        runBlocking {
            val baseUrl = args.getString(DriveLinkTestRunner.ARG_BASE_URL)
            if (baseUrl != null) {
                demoConfig.applyExternal(baseUrl, args.getString(DriveLinkTestRunner.ARG_SCENARIO) ?: "default")
            } else {
                demoConfig.setActiveProfile(BuiltInProfiles.CLOUD_ID)
                demoConfig.setScenario("default")
                demoConfig.deleteProfile("external")
            }
        }
        server.close()
    }

    @Test fun pendingValues_activateExternalProfile_andScenario() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).setHeader("Content-Type", "application/json")
                .body("""{"status":"UP","version":"0.0.0-device"}""").build(),
        )

        assertThat(PendingExternalConfig.Default.lastError).isNull()
        assertThat(demoConfig.activeProfile.value.id).isEqualTo("external")
        assertThat(demoConfig.activeProfile.value.baseUrl).isEqualTo(server.url("/").toString().trimEnd('/'))
        assertThat(demoConfig.scenario.value).isEqualTo("fast")

        assertThat(account.getHealth()).isInstanceOf(Outcome.Ok::class.java)
        assertThat(server.takeRequest().headers["X-Scenario"]).isEqualTo("fast")
    }
}
