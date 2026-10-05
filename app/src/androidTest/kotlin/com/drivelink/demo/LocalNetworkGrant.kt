package com.drivelink.demo

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.drivelink.demo.console.LocalNetworkAccess

/**
 * Android 17 (API 37) blocks connections to a local network address (for example the emulator host
 * alias 10.0.2.2) without a runtime permission, and the Demo console asks for it with a system
 * dialog that pauses the activity. A plain connected-test install does not grant it. A public
 * endpoint does not need it, so the grant only runs where the platform enforces it.
 */
fun grantLocalNetworkAccess() {
    if (Build.VERSION.SDK_INT < LOCAL_NETWORK_SDK) return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    runCatching {
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, LocalNetworkAccess.PERMISSION)
    }
}

private const val LOCAL_NETWORK_SDK = 37
