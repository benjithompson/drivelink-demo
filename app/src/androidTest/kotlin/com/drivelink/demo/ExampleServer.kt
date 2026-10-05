package com.drivelink.demo

import androidx.test.platform.app.InstrumentationRegistry
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A MockWebServer on the device that answers with the contract examples (`api/examples`,
 * packaged as test assets). It serves the default scenario. [overrides] replaces the body for a
 * request: the key is "METHOD /path" and the value is a pair of status code and body.
 */
class ExampleServer {
    val server = MockWebServer()

    /** Every request, in order: "METHOD /v1/path" and the body. */
    val requests = CopyOnWriteArrayList<Pair<String, String>>()

    val overrides = mutableMapOf<String, Pair<Int, String>>()

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    fun asset(path: String): String = assets.open(path).bufferedReader().use { it.readText() }

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                val key = request.method + " " + path
                requests += key to (request.body?.utf8() ?: "")
                val (code, body) = overrides[key] ?: route(request.method, path)
                return MockResponse.Builder().code(code).setHeader("Content-Type", "application/json").body(body).build()
            }
        }
        server.start()
    }

    fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    fun close() = server.close()

    fun count(method: String, pathPrefix: String): Int =
        requests.count { it.first.startsWith("$method $pathPrefix") }

    fun bodyOf(method: String, pathPrefix: String): String? =
        requests.lastOrNull { it.first.startsWith("$method $pathPrefix") }?.second

    private fun route(method: String, path: String): Pair<Int, String> = when {
        method == "POST" && path == "/v1/auth/token" -> 200 to asset("createToken/200.default.json")
        method == "GET" && path == "/v1/me" -> 200 to asset("getMe/200.default.json")
        method == "GET" && path == "/v1/vehicles" -> 200 to asset("listVehicles/200.default.json")
        method == "GET" && path.endsWith("/status") ->
            200 to asset(if (path.contains(ICE_VIN)) "getVehicleStatus/200.default-ice.json" else "getVehicleStatus/200.default.json")
        method == "GET" && path.endsWith("/location") -> 200 to asset("getVehicleLocation/200.default.json")
        method == "GET" && path.endsWith("/trips") -> 200 to asset("listTrips/200.default.json")
        method == "GET" && path.endsWith("/climate-presets") -> 200 to asset("getClimatePresets/200.default.json")
        method == "GET" && path == "/v1/alerts" -> 200 to asset("listAlerts/200.default.json")
        method == "POST" && path.endsWith("/commands") -> 202 to asset("sendCommand/202.default.json")
        method == "GET" && path.startsWith("/v1/commands/") -> 200 to asset("getCommand/200.default.json")
        else -> 404 to """{"type":"about:blank","title":"Not found","status":404,"code":"NOT_FOUND"}"""
    }

    companion object {
        const val AURORA_VIN = "DLEV26AURA0000101"
        const val ICE_VIN = "DLGS25SLACE000202"
    }
}
