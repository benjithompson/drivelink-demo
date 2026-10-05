package com.drivelink.demo

import com.drivelink.demo.console.LocalNetworkAccess
import com.drivelink.demo.timing.LogcatTimingLog
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class AppUnitTest {
    @Test fun timingFormat_matchesPerfectoParser() {
        assertThat(LogcatTimingLog.format("command_ms", 4210, linkedMapOf("type" to "LOCK", "result" to "SUCCEEDED")))
            .isEqualTo("DL_TIMING command_ms=4210 type=LOCK result=SUCCEEDED")
        assertThat(LogcatTimingLog.format("cold_start_ms", 812, emptyMap())).isEqualTo("DL_TIMING cold_start_ms=812")
        assertThat(LogcatTimingLog.format("x", 1, mapOf("k" to "a b"))).isEqualTo("DL_TIMING x=1 k=a_b")
    }

    @Test fun localNetworkHosts() {
        listOf("10.0.2.2", "192.168.1.20", "172.16.0.5", "172.31.255.1", "169.254.1.1", "100.64.0.1", "printer.local", "[fd00::1]", "fe80::1")
            .forEach { assertWithMessage(it).that(LocalNetworkAccess.isLocalNetworkHost(it)).isTrue() }
        listOf("127.0.0.1", "localhost", "172.32.0.1", "8.8.8.8", "mock.example.com", "10.0.2", null, "2001:db8::1")
            .forEach { assertWithMessage(it.toString()).that(LocalNetworkAccess.isLocalNetworkHost(it)).isFalse() }
    }
}
