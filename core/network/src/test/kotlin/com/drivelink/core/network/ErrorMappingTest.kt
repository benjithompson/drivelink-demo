package com.drivelink.core.network

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

class ErrorMappingTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    @Serializable data class Health(val status: String, val version: String)

    private fun map(status: Int, body: String, retryAfter: String? = null) =
        ErrorMapper.httpError(status, body, retryAfter, "sent-id")

    @Test fun problemCodes_winOverStatus() {
        val expected = mapOf(
            "INVALID_CREDENTIALS" to AppError.InvalidCredentials("srv"),
            "TOKEN_EXPIRED" to AppError.Unauthorized("srv"),
            "INVALID_PIN" to AppError.InvalidPin("srv"),
            "VEHICLE_OFFLINE" to AppError.VehicleOffline("srv"),
            "VEHICLE_ASLEEP" to AppError.VehicleAsleep("srv"),
            "COMMAND_CONFLICT" to AppError.Conflict("srv"),
            "RATE_LIMITED" to AppError.RateLimited(null, "srv"),
            "NOT_FOUND" to AppError.NotFound("srv"),
            "INVALID_REQUEST" to AppError.InvalidRequest(418, "srv"),
            "INTERNAL" to AppError.Server(418, "srv"),
        )
        for ((code, error) in expected) {
            assertThat(map(418, problem(418, code, "srv"))).isEqualTo(error)
        }
    }

    @Test fun unknownCode_mapsByStatus() {
        assertThat(map(409, problem(409, "SOMETHING_NEW", null))).isEqualTo(AppError.Conflict("sent-id"))
    }

    @Test fun nonProblemBodies_mapByStatus() {
        assertThat(map(404, "No match Found")).isEqualTo(AppError.NotFound("sent-id"))
        assertThat(map(401, "")).isEqualTo(AppError.Unauthorized("sent-id"))
        assertThat(map(403, "<html/>")).isEqualTo(AppError.InvalidRequest(403, "sent-id"))
        assertThat(map(409, "")).isEqualTo(AppError.Conflict("sent-id"))
        assertThat(map(429, "")).isEqualTo(AppError.RateLimited(null, "sent-id"))
        assertThat(map(400, "{}")).isEqualTo(AppError.InvalidRequest(400, "sent-id"))
        assertThat(map(422, "")).isEqualTo(AppError.InvalidRequest(422, "sent-id"))
        assertThat(map(500, "oops")).isEqualTo(AppError.Server(500, "sent-id"))
        assertThat(map(503, "")).isEqualTo(AppError.Server(503, "sent-id"))
    }

    @Test fun retryAfter_parsedAsSeconds() {
        assertThat(map(429, problem(429, "RATE_LIMITED"), "30")).isEqualTo(AppError.RateLimited(30, "srv-429"))
        assertThat(map(429, problem(429, "RATE_LIMITED"), "Wed, 21 Oct 2026 07:28:00 GMT"))
            .isEqualTo(AppError.RateLimited(null, "srv-429"))
        assertThat(map(429, "", " 5 ")).isEqualTo(AppError.RateLimited(5, "sent-id"))
    }

    @Test fun transportErrors_timeoutFlag() {
        assertThat(ErrorMapper.transportError(SocketTimeoutException("read timed out"), "id").timeout).isTrue()
        assertThat(ErrorMapper.transportError(InterruptedIOException("timeout"), "id").timeout).isTrue()
        assertThat(ErrorMapper.transportError(IOException("connection reset"), "id").timeout).isFalse()
        val notConfigured = ErrorMapper.transportError(EndpointNotConfiguredException("Private location", ""), "id")
        assertThat(notConfigured.timeout).isFalse()
        assertThat(notConfigured.message).contains("\"Private location\"")
    }

    @Test fun ok_decodesBody() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(200, """{"status":"UP","version":"1.0.0","service":"drivelink-mock"}"""))
        assertThat(apiCall(Health.serializer()) { h.api.health() }).isEqualTo(Outcome.Ok(Health("UP", "1.0.0")))
    }

    @Test fun ok_badJson_isParseError() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(200, """{"status":42"""))
        val result = apiCall(Health.serializer()) { h.api.health() } as Outcome.Err
        val sent = server.takeRequest().headers["X-Correlation-Id"]
        assertThat(result.error).isInstanceOf(AppError.Parse::class.java)
        assertThat(result.error.correlationId).isEqualTo(sent)
    }

    @Test fun ok_missingField_isParseError() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(200, """{"status":"UP"}"""))
        val result = apiCall(Health.serializer()) { h.api.health() } as Outcome.Err
        assertThat(result.error).isInstanceOf(AppError.Parse::class.java)
    }

    @Test fun noContent_unitCall_isOk() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(MockResponse.Builder().code(204).build())
        assertThat(apiCallUnit { h.api.vehicles(null) }).isEqualTo(Outcome.Ok(Unit))
    }

    @Test fun textNotFound_fromServer() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(404, "No match Found", "text/plain"))
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        assertThat(result.error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test fun rateLimited_fromServer_readsRetryAfter() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(
            MockResponse.Builder().code(429).setHeader("Retry-After", "30")
                .body(problem(429, "RATE_LIMITED")).build(),
        )
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        assertThat(result.error).isEqualTo(AppError.RateLimited(30, "srv-429"))
    }

    @Test fun connectionRefused_isNetworkError() = runTest {
        val h = Harness(profileFor(server))
        server.close()
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        assertThat((result.error as AppError.Network).timeout).isFalse()
    }

    @Test fun cancellation_propagates() = runTest {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { apiCallUnit { throw CancellationException("stop") } }
        }
    }
}
