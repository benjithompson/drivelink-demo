package com.drivelink.demo

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.demo.timing.ColdStart
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Debug builds only. Sets the demo configuration from intent extras, without UI, then finishes.
 *
 * Extras (all optional, strings):
 * - `baseUrl`: activates the profile "External" with this base URL.
 * - `profile`: activates the profile with this id (`blazemeter-cloud`, `private-location`, `local`, `external` or a user id).
 * - `scenario`: X-Scenario name (`default` sends no header).
 * - `vin`: selected vehicle; an empty value clears it.
 *
 * `profile` applies after `baseUrl`, so `profile` wins when both are set.
 *
 * ```
 * adb shell am start -n com.drivelink.demo/.DemoConfigActivity --es baseUrl http://10.0.2.2:8080 --es scenario slow-vehicle
 * adb shell am start -n com.drivelink.demo/.DemoConfigActivity --es profile local
 * ```
 *
 * Logcat (tag DL_CONFIG): `DL_CONFIG applied profile=<id> baseUrl=<url> scenario=<name> vin=<vin>`,
 * or `DL_CONFIG rejected <field>: <reason>` for an invalid value. The other values still apply.
 */
@AndroidEntryPoint
class DemoConfigActivity : ComponentActivity() {

    @Inject lateinit var demoConfig: DemoConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This activity started the process, or MainActivity already logged its cold start.
        ColdStart.skip()
        val extras = intent.extras
        // Blocking on purpose: the values must be stored before `am start` of the app UI that follows.
        runBlocking { apply(extras) }
        finish()
    }

    private suspend fun apply(extras: Bundle?) {
        val baseUrl = extras?.getString(EXTRA_BASE_URL)
        val scenario = extras?.getString(EXTRA_SCENARIO)
        val profile = extras?.getString(EXTRA_PROFILE)
        val vin = extras?.getString(EXTRA_VIN)
        // Separate writes, so an invalid base URL does not block the scenario.
        if (baseUrl != null) attempt(EXTRA_BASE_URL) { demoConfig.applyExternal(baseUrl, null) }
        if (scenario != null) attempt(EXTRA_SCENARIO) { demoConfig.applyExternal(null, scenario) }
        if (profile != null) attempt(EXTRA_PROFILE) { demoConfig.setActiveProfile(profile.trim()) }
        if (vin != null) attempt(EXTRA_VIN) { demoConfig.setVin(vin) }
        val active = demoConfig.activeProfile.value
        Log.i(
            TAG,
            "DL_CONFIG applied profile=${active.id} baseUrl=${active.baseUrl.ifEmpty { "<none>" }} " +
                "scenario=${demoConfig.scenario.value} vin=${demoConfig.vin.value ?: "<none>"}",
        )
    }

    private suspend fun attempt(field: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "DL_CONFIG rejected $field: ${e.message}")
        }
    }

    companion object {
        const val TAG = "DL_CONFIG"
        const val EXTRA_BASE_URL = "baseUrl"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_PROFILE = "profile"
        const val EXTRA_VIN = "vin"
    }
}
