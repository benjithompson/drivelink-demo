package com.drivelink.core.testing

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.drivelink.core.settings.PendingExternalConfig
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Instrumentation runner for Hilt tests.
 *
 * Instrumentation arguments (optional), for Perfecto and CI:
 * - `baseUrl`: activates the endpoint profile "External" with this base URL.
 * - `scenario`: sets the X-Scenario name.
 *
 * Without `baseUrl`, the runner reads [EMBEDDED_BASE_URL_ASSET] from the test APK (built with
 * `-PembedBaseUrl`, for Perfecto repository scripts, which cannot pass arguments). Without either,
 * it adds `notPackage` = [CORE_SUITE_PACKAGE]: the core suite needs a live endpoint.
 *
 * Example: `adb shell am instrument -w -e baseUrl http://10.0.2.2:8080 -e scenario slow-vehicle ...`
 * or Gradle `-Pandroid.testInstrumentationRunnerArguments.baseUrl=http://10.0.2.2:8080`.
 *
 * `onCreate` runs before the first Hilt component exists, so the values go to
 * [PendingExternalConfig.Default]; the settings store applies and persists them when it is created.
 */
class DriveLinkTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)

    override fun onCreate(arguments: Bundle?) {
        val args = arguments ?: Bundle()
        if (args.getString(ARG_BASE_URL) == null) embeddedBaseUrl()?.let { args.putString(ARG_BASE_URL, it) }
        PendingExternalConfig.Default.set(args.getString(ARG_BASE_URL), args.getString(ARG_SCENARIO))
        // The core suite needs a live endpoint. Without baseUrl it is left out, so the JUnit XML
        // does not list ten skipped tests (AGP writes an assumption failure as a failure).
        if (args.getString(ARG_BASE_URL) == null && args.getString(ARG_NOT_PACKAGE) == null) {
            args.putString(ARG_NOT_PACKAGE, CORE_SUITE_PACKAGE)
        }
        super.onCreate(args)
    }

    /** The endpoint built into the test APK, or null. [getContext] is the test APK's own context. */
    private fun embeddedBaseUrl(): String? =
        runCatching { context.assets.open(EMBEDDED_BASE_URL_ASSET).bufferedReader().use { it.readText().trim() } }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val EMBEDDED_BASE_URL_ASSET = "drivelink-base-url.txt"
        const val ARG_BASE_URL = "baseUrl"
        const val ARG_SCENARIO = "scenario"
        const val CORE_SUITE_PACKAGE = "com.drivelink.demo.core"
        private const val ARG_NOT_PACKAGE = "notPackage"
    }
}
