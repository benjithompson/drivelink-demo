package com.drivelink.core.network

import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.domain.config.Session
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.network.inspector.NetworkInspector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FakeDemoConfig(profile: EndpointProfile, scenario: String = "default") : DemoConfig {
    override val profiles = MutableStateFlow(listOf(profile))
    override val activeProfile = MutableStateFlow(profile)
    override val scenario = MutableStateFlow(scenario)
    override val vin = MutableStateFlow<String?>(null)
    override suspend fun setActiveProfile(id: String) {
        activeProfile.value = profiles.value.first { it.id == id }
    }
    override suspend fun upsertProfile(profile: EndpointProfile) {
        profiles.value = profiles.value.filterNot { it.id == profile.id } + profile
    }
    override suspend fun deleteProfile(id: String) {
        profiles.value = profiles.value.filterNot { it.id == id }
    }
    override suspend fun setScenario(name: String) { scenario.value = name }
    override suspend fun setVin(vin: String?) { this.vin.value = vin }
    override suspend fun applyExternal(baseUrl: String?, scenario: String?) = Unit
    override fun exportProfilesJson(): String = "[]"
    override suspend fun importProfilesJson(json: String) = Unit
}

class FakeSessionStore(initial: Session? = null) : SessionStore {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<Session?> = state
    var saves = 0
    var clears = 0
    override suspend fun save(session: Session) { saves++; state.value = session }
    override suspend fun clear() { clears++; state.value = null }
}

@Serializable
data class Cmd(val type: String, val pin: String)

/** Test Retrofit API. Paths are relative to /v1/. */
interface TestApi {
    @GET("health") suspend fun health(): Response<ResponseBody>
    @GET("vehicles") suspend fun vehicles(@Query("page") page: String?): Response<ResponseBody>
    @GET("vehicles") suspend fun vehiclesWithId(@Header("X-Correlation-Id") id: String): Response<ResponseBody>
    @POST("vehicles/{vin}/commands") suspend fun command(@Path("vin") vin: String, @Body body: Cmd): Response<ResponseBody>
    @POST("auth/token") suspend fun token(@Body body: JsonObject): Response<ResponseBody>
    @POST("auth/refresh") suspend fun refresh(@Body body: JsonObject): Response<ResponseBody>
    @GET("alerts") suspend fun alerts(): Response<ResponseBody>
    @GET("me") suspend fun me(): Response<ResponseBody>
}

val BUILD_INFO = AppBuildInfo(mockBaseUrl = "", clientVersion = "0.1.0 (1)", debug = true)

val FAST_TIMEOUTS = NetworkTimeouts(connect = 2.seconds, read = 500.milliseconds, write = 2.seconds, call = 5.seconds)

fun profileFor(server: MockWebServer, prefix: String = "", name: String = "Test"): EndpointProfile =
    EndpointProfile(id = "test", name = name, baseUrl = server.url(prefix).toString().trimEnd('/'))

class Harness(
    val profile: EndpointProfile,
    session: Session? = Session("at-old-1234", "rt-old", "alex@drivelink.test"),
    scenario: String = "default",
    tokenRefresher: TokenRefresher? = null,
    timeouts: NetworkTimeouts = FAST_TIMEOUTS,
) {
    val config = FakeDemoConfig(profile, scenario)
    val sessions = FakeSessionStore(session)
    val inspector = NetworkInspector()
    val http = DriveLinkHttp(config, sessions, BUILD_INFO, inspector, tokenRefresher, timeouts)
    val api: TestApi = http.create()
}

fun json(code: Int, body: String, contentType: String = "application/json"): MockResponse =
    MockResponse.Builder().code(code).setHeader("Content-Type", contentType).body(body).build()

fun problem(status: Int, code: String, correlationId: String? = "srv-$status"): String =
    """{"type":"https://drivelink.test/problems/x","title":"t","status":$status,"code":"$code"""" +
        (correlationId?.let { ""","correlationId":"$it"""" } ?: "") + "}"

val UUID_V4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
