package com.drivelink.demo

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
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

/** The Demo console UI: Test connection fills the inspector, and a row opens the detail. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DemoConsoleTest {

    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Inject lateinit var demoConfig: DemoConfig

    private val server = MockWebServer()
    private lateinit var initialProfileId: String
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
        hilt.inject()
        server.start()
        initialProfileId = demoConfig.activeProfile.value.id
        runBlocking {
            demoConfig.upsertProfile(EndpointProfile(ID, "Device mock", server.url("/").toString().trimEnd('/')))
            demoConfig.setActiveProfile(ID)
        }
    }

    @After fun tearDown() {
        scenario?.close()
        runBlocking {
            demoConfig.setActiveProfile(initialProfileId)
            demoConfig.deleteProfile(ID)
        }
        server.close()
    }

    @Test fun testConnection_showsResult_andInspectorRow_andDetail() {
        server.enqueue(
            MockResponse.Builder().code(200).setHeader("Content-Type", "application/json")
                .body("""{"status":"UP","version":"0.0.0-device"}""").build(),
        )
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("screen", "console")
        scenario = ActivityScenario.launch(intent)

        compose.onNodeWithTag("console.profile").assertIsDisplayed()
        compose.onNodeWithTag("console.health").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTagExists("inspector.row.0")
        }
        compose.onNodeWithTag("console.result.message").assertIsDisplayed()
        compose.onNodeWithText("UP · version 0.0.0-device", substring = true).assertIsDisplayed()

        compose.onNodeWithTag("inspector.row.0").performClick()
        compose.onNodeWithTag("inspector.detail").assertIsDisplayed()
        compose.onNodeWithText("Request Detail").assertIsDisplayed()
    }

    private companion object {
        const val ID = "device-mock-console"
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagExists(tag: String): Boolean =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
