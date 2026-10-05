package com.drivelink.core.data

import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.domain.config.Session
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.network.DriveLinkHttp
import com.drivelink.core.network.NetworkTimeouts
import com.drivelink.core.network.api.DriveLinkApi
import com.drivelink.core.network.inspector.NetworkInspector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.io.File
import kotlin.time.Duration.Companion.seconds

class FakeDemoConfig(profile: EndpointProfile) : DemoConfig {
    override val profiles = MutableStateFlow(listOf(profile))
    override val activeProfile = MutableStateFlow(profile)
    override val scenario = MutableStateFlow("default")
    override val vin = MutableStateFlow<String?>(null)
    override suspend fun setActiveProfile(id: String) { activeProfile.value = profiles.value.first { it.id == id } }
    override suspend fun upsertProfile(profile: EndpointProfile) { profiles.value = profiles.value.filterNot { it.id == profile.id } + profile }
    override suspend fun deleteProfile(id: String) { profiles.value = profiles.value.filterNot { it.id == id } }
    override suspend fun setScenario(name: String) { scenario.value = name }
    override suspend fun setVin(vin: String?) { this.vin.value = vin }
    override suspend fun applyExternal(baseUrl: String?, scenario: String?) = Unit
    override fun exportProfilesJson(): String = "[]"
    override suspend fun importProfilesJson(json: String) = Unit
}

class FakeSessionStore(initial: Session? = null) : SessionStore {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<Session?> = state
    override suspend fun save(session: Session) { state.value = session }
    override suspend fun clear() { state.value = null }
}

/** The real network stack (DriveLinkHttp + DriveLinkApi) pointed at [server]. */
class DataHarness(server: MockWebServer, session: Session? = Session("at-demo-1234", "rt-demo", "alex.rivera@drivelink.test")) {
    val config = FakeDemoConfig(EndpointProfile("test", "Test", server.url("/").toString().trimEnd('/')))
    val sessions = FakeSessionStore(session)
    val inspector = NetworkInspector()
    val http = DriveLinkHttp(
        config, sessions, AppBuildInfo("", "android/0.1.0", debug = true), inspector,
        timeouts = NetworkTimeouts(connect = 2.seconds, read = 2.seconds, write = 2.seconds, call = 5.seconds),
    )
    val api: DriveLinkApi = http.create()
}

/** Contract files. build.gradle.kts sets the system property drivelink.repoRoot. */
object Examples {
    private val root: File by lazy {
        File(requireNotNull(System.getProperty("drivelink.repoRoot")) { "Run the tests through Gradle." })
    }

    /** A file in api/examples, for example `getVehicleStatus/200.default.json`. */
    fun read(path: String): String = root.resolve("api/examples/$path").readText()

    /** A file in api/examples-raw (invalid on purpose). */
    fun readRaw(name: String): String = root.resolve("api/examples-raw/$name").readText()
}

fun response(code: Int, body: String, contentType: String = "application/json"): MockResponse =
    MockResponse.Builder().code(code).setHeader("Content-Type", contentType).body(body).build()

fun noContent(): MockResponse = MockResponse.Builder().code(204).build()
