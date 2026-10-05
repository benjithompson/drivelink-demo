package com.drivelink.core.network

import com.drivelink.core.domain.Outcome
import com.drivelink.core.network.inspector.InspectorEntry
import com.drivelink.core.network.inspector.NetworkInspector
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class InspectorTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private fun entry(id: Long) = InspectorEntry(
        id = id, startedAtEpochMs = 0, method = "GET", url = "http://h/v1/x", requestHeaders = emptyList(),
        requestBody = null, status = 200, responseHeaders = emptyList(), responseBody = null, durationMs = 1,
        error = null, correlationId = null, scenario = null, profileName = null, attempt = 1,
    )

    @Test fun recordsCall_withMaskedSecrets() = runTest {
        val profile = profileFor(server, name = "Local").copy(apiKeyHeader = "X-Api-Key", apiKeyValue = "secret-key")
        val h = Harness(profile, scenario = "slow-vehicle")
        server.enqueue(json(202, """{"commandId":"cmd-1"}"""))

        apiCallUnit { h.api.command("VIN1", Cmd("LOCK", "1234")) }

        val sent = server.takeRequest()
        val e = h.inspector.entries.value.single()
        assertThat(e.method).isEqualTo("POST")
        assertThat(e.url).isEqualTo(server.url("/v1/vehicles/VIN1/commands").toString())
        assertThat(e.status).isEqualTo(202)
        assertThat(e.requestBody).isEqualTo("""{"type":"LOCK","pin":"1234"}""")
        assertThat(e.responseBody).isEqualTo("""{"commandId":"cmd-1"}""")
        assertThat(e.correlationId).isEqualTo(sent.headers["X-Correlation-Id"])
        assertThat(e.scenario).isEqualTo("slow-vehicle")
        assertThat(e.profileName).isEqualTo("Local")
        assertThat(e.attempt).isEqualTo(1)
        assertThat(e.error).isNull()
        val headers = e.requestHeaders.toMap()
        assertThat(headers["Authorization"]).isEqualTo("Bearer …1234")
        assertThat(headers["X-Api-Key"]).isEqualTo("••••")
        assertThat(headers["Content-Type"]).isEqualTo("application/json; charset=utf-8")
        assertThat(e.requestHeaders.joinToString()).doesNotContain("secret-key")
        assertThat(e.requestHeaders.joinToString()).doesNotContain("at-old-1234")
        assertThat(e.responseHeaders.toMap()["Content-Type"]).isEqualTo("application/json")
    }

    @Test fun capsAt200_newestFirst() {
        val inspector = NetworkInspector()
        (1L..250L).forEach { inspector.record(entry(it)) }
        val ids = inspector.entries.value.map { it.id }
        assertThat(ids).hasSize(200)
        assertThat(ids.first()).isEqualTo(250L)
        assertThat(ids.last()).isEqualTo(51L)
    }

    @Test fun clear_emptiesBuffer() {
        val inspector = NetworkInspector()
        inspector.record(entry(1))
        inspector.clear()
        assertThat(inspector.entries.value).isEmpty()
    }

    @Test fun largeResponse_isTruncatedInInspector_butAppGetsFullBody() = runTest {
        val h = Harness(profileFor(server))
        val big = "a".repeat(100 * 1024)
        server.enqueue(json(200, "\"$big\""))

        val result = apiCall(String.serializer()) { h.api.vehicles(null) }

        assertThat((result as Outcome.Ok).value).hasLength(big.length)
        val body = h.inspector.entries.value.single().responseBody!!
        assertThat(body).endsWith(NetworkInspector.TRUNCATED_MARKER)
        assertThat(body.length).isEqualTo(NetworkInspector.MAX_BODY_BYTES + NetworkInspector.TRUNCATED_MARKER.length)
    }

    @Test fun largeRequestBody_isTruncated() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(202, "{}"))
        apiCallUnit { h.api.command("VIN1", Cmd("LOCK", "1".repeat(70_000))) }
        val body = h.inspector.entries.value.single().requestBody!!
        assertThat(body).endsWith(NetworkInspector.TRUNCATED_MARKER)
        assertThat(server.takeRequest().bodySize).isGreaterThan(70_000L)
    }

    @Test fun recordsTransportError() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(MockResponse.Builder().code(202).headersDelay(2, TimeUnit.SECONDS).build())
        apiCallUnit { h.api.command("VIN1", Cmd("LOCK", "1234")) }
        val e = h.inspector.entries.value.single()
        assertThat(e.status).isNull()
        assertThat(e.error).startsWith("SocketTimeoutException")
        assertThat(e.durationMs).isAtLeast(400L)
    }

    @Test fun toCurl_quotesAndKeepsMasks() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(202, "{}"))
        apiCallUnit { h.api.command("VIN1", Cmd("LO'CK", "1234")) }
        val e = h.inspector.entries.value.single()
        val curl = h.inspector.toCurl(e)
        assertThat(curl).startsWith("curl -X POST '${e.url}'")
        assertThat(curl).contains("-H 'Authorization: Bearer …1234'")
        assertThat(curl).contains("-H 'X-Correlation-Id: ${e.correlationId}'")
        assertThat(curl).contains("""--data-raw '{"type":"LO'\''CK","pin":"1234"}'""")
        assertThat(curl).doesNotContain("at-old-1234")
    }

    @Test fun maskBearer_shortToken() {
        assertThat(NetworkInspector.maskBearer("Bearer abc")).isEqualTo("Bearer …")
    }

}
