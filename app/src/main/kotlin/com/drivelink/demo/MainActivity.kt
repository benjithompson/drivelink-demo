package com.drivelink.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivelink.core.designsystem.theme.DriveLinkTheme
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.config.AppPreferences
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.ThemeMode
import com.drivelink.demo.launch.LaunchRequest
import com.drivelink.demo.launch.applyTo
import com.drivelink.demo.nav.AppNav
import com.drivelink.demo.timing.ColdStart
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Launch extras (all optional), for screenshots and device tests. `am start` on a running app
 * delivers them to [onNewIntent], so the app navigates without a restart.
 * - `screen`: home, carcare, maps, menu, controls, climate, pin, charging, status, gallery, console, login
 * - `scenario`: X-Scenario name; sets the demo scenario (the same setting as the Demo console)
 * - `vehicle`: aurora (EV) or solace (gas); selects the vehicle
 * - `dark`: true or false; overrides the theme setting until the user picks a theme in Settings
 *
 * Test tags are exposed as resource ids (testTagsAsResourceId) for UiAutomator and Perfecto.
 * The first frame logs `DL_TIMING cold_start_ms=<ms>` once per process.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var timing: TimingLog
    @Inject lateinit var demoConfig: DemoConfig
    @Inject lateinit var appPreferences: AppPreferences

    private var request by mutableStateOf<LaunchRequest?>(null)

    /** The `dark` launch extra. A change of the theme setting clears it, so Settings always wins over an old extra. */
    private var darkOverride by mutableStateOf<Boolean?>(null)
    private var requestCounter = 0

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Top bar is navy in both themes, so status bar icons are always light.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // A rotation or process restore re-delivers the same intent. Handle only a fresh start.
        if (savedInstanceState == null) accept(intent)
        setContent {
            val themeMode by appPreferences.themeMode.collectAsStateWithLifecycle()
            var seenMode by remember { mutableStateOf(themeMode) }
            LaunchedEffect(themeMode) {
                if (themeMode != seenMode) {
                    seenMode = themeMode
                    darkOverride = null
                }
            }
            val systemDark = isSystemInDarkTheme()
            val dark = darkOverride ?: when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DriveLinkTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    AppNav(request)
                }
            }
        }
        ColdStart.onFirstFrame(this, timing)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
    }

    private fun accept(intent: Intent?) {
        val next = LaunchRequest.from(++requestCounter, intent)
        // Blocking on purpose: the scenario and the vehicle must be stored before the screens load data.
        runBlocking { next.applyTo(demoConfig) }
        darkOverride = next.dark
        request = next
    }
}
