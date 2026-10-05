package com.drivelink.demo.console

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) local network protection: an app needs the runtime permission
 * [PERMISSION] to connect to a local network address. Without it the connect times out.
 * Loopback (localhost, 127.0.0.1) does not need it; the emulator host alias 10.0.2.2 does.
 *
 * Test automation grants it without the dialog:
 * `adb shell pm grant com.drivelink.demo android.permission.ACCESS_LOCAL_NETWORK`
 * (or install with `adb install -g`).
 */
object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** The first API level that enforces the permission. */
    private const val ENFORCED_SINCE_SDK = 37

    fun enforced(): Boolean = Build.VERSION.SDK_INT >= ENFORCED_SINCE_SDK

    fun granted(context: Context): Boolean =
        !enforced() || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * True for an IP literal in a private, link-local or shared range, or an mDNS name (`.local`).
     * Other host names return false: the app does not resolve them to check.
     */
    fun isLocalNetworkHost(host: String?): Boolean {
        val h = host?.trim()?.lowercase()?.removePrefix("[")?.removeSuffix("]") ?: return false
        if (h.endsWith(".local")) return true
        val v4 = h.split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 4 && h.count { c -> c == '.' } == 3 }
        if (v4 != null) {
            val (a, b) = v4
            return a == 10 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254) ||
                (a == 100 && b in 64..127)
        }
        return h.contains(':') && (h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb"))
    }
}

/**
 * Asks for the local network permission when [host] is a local network address and the
 * permission is missing. Returns true when a call to [host] is blocked by the missing permission.
 */
@Composable
fun rememberLocalNetworkBlocked(host: String?): Boolean {
    val context = LocalContext.current
    val needed = LocalNetworkAccess.enforced() && LocalNetworkAccess.isLocalNetworkHost(host)
    var granted by remember(host) { mutableStateOf(LocalNetworkAccess.granted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(host, needed) {
        if (needed && !granted) launcher.launch(LocalNetworkAccess.PERMISSION)
    }
    return needed && !granted
}
