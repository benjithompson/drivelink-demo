package com.drivelink.core.network

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class HeadersTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private val body = JsonObject(mapOf("refreshToken" to JsonPrimitive("rt")))

    @Test fun defaultScenario_omitsHeader() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(200, "[]"))
        apiCallUnit { h.api.vehicles(null) }
        assertThat(server.takeRequest().headers["X-Scenario"]).isNull()
    }

    @Test fun namedScenario_sendsHeader_andChangeAppliesToNextCall() = runTest {
        val h = Harness(profileFor(server), scenario = "slow-vehicle")
        server.enqueue(json(200, "[]"))
        server.enqueue(json(200, "[]"))
        apiCallUnit { h.api.vehicles(null) }
        h.config.scenario.value = "default"
        apiCallUnit { h.api.vehicles(null) }
        assertThat(server.takeRequest().headers["X-Scenario"]).isEqualTo("slow-vehicle")
        assertThat(server.takeRequest().headers["X-Scenario"]).isNull()
    }

    @Test fun correlationId_isUuidV4_perCall_andClientVersionSent() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(200, "[]"))
        server.enqueue(json(200, "[]"))
        apiCallUnit { h.api.vehicles(null) }
        apiCallUnit { h.api.vehicles(null) }
        val first = server.takeRequest().headers
        val second = server.takeRequest().headers
        assertThat(first["X-Correlation-Id"]).matches(UUID_V4.pattern)
        assertThat(second["X-Correlation-Id"]).matches(UUID_V4.pattern)
        assertThat(first["X-Correlation-Id"]).isNotEqualTo(second["X-Correlation-Id"])
        assertThat(first["X-Client-Version"]).isEqualTo("0.1.0 (1)")
    }

    @Test fun callerCorrelationHeader_isKept_andUsedInError() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(500, "oops", "text/plain"))
        val result = apiCallUnit { h.api.vehiclesWithId("caller-id") }
        assertThat(server.takeRequest().headers["X-Correlation-Id"]).isEqualTo("caller-id")
        assertThat((result as Outcome.Err).error.correlationId).isEqualTo("caller-id")
    }

    @Test fun errorCorrelationId_isTheSentHeader_whenProblemHasNone() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(409, problem(409, "VEHICLE_OFFLINE", correlationId = null), "application/problem+json"))
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        val sent = server.takeRequest().headers["X-Correlation-Id"]
        assertThat(result.error).isInstanceOf(AppError.VehicleOffline::class.java)
        assertThat(result.error.correlationId).isEqualTo(sent)
    }

    @Test fun bearer_onProtectedPaths() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(200, "[]"))
        server.enqueue(json(200, "{}"))
        server.enqueue(json(200, "[]"))
        apiCallUnit { h.api.vehicles(null) }
        apiCallUnit { h.api.me() }
        apiCallUnit { h.api.alerts() }
        repeat(3) { assertThat(server.takeRequest().headers["Authorization"]).isEqualTo("Bearer at-old-1234") }
    }

    @Test fun noBearer_onHealthTokenRefresh() = runTest {
        val h = Harness(profileFor(server))
        repeat(3) { server.enqueue(json(200, "{}")) }
        apiCallUnit { h.api.health() }
        apiCallUnit { h.api.token(body) }
        apiCallUnit { h.api.refresh(body) }
        repeat(3) { assertThat(server.takeRequest().headers["Authorization"]).isNull() }
    }

    @Test fun noBearer_withoutSession() = runTest {
        val h = Harness(profileFor(server), session = null)
        server.enqueue(json(200, "[]"))
        apiCallUnit { h.api.vehicles(null) }
        assertThat(server.takeRequest().headers["Authorization"]).isNull()
    }

    @Test fun apiKeyHeader_sentWhenProfileHasOne() = runTest {
        val h = Harness(profileFor(server).copy(apiKeyHeader = "X-Api-Key", apiKeyValue = "secret-key"))
        server.enqueue(json(200, "{}"))
        apiCallUnit { h.api.health() }
        assertThat(server.takeRequest().headers["X-Api-Key"]).isEqualTo("secret-key")
    }

    @Test fun postBody_isEncodedWithDriveLinkJson() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(202, """{"commandId":"cmd-1","status":"QUEUED"}"""))
        val result = apiCall(serializer<JsonObject>()) { h.api.command("VIN1", Cmd("LOCK", "1234")) }
        val request = server.takeRequest()
        assertThat(request.body?.utf8()).isEqualTo("""{"type":"LOCK","pin":"1234"}""")
        assertThat(request.headers["Content-Type"]).startsWith("application/json")
        assertThat((result as Outcome.Ok).value["commandId"]).isEqualTo(JsonPrimitive("cmd-1"))
    }
}
