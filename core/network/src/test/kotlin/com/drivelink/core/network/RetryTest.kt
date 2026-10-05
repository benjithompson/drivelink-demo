package com.drivelink.core.network

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RetryTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private fun stalled() = MockResponse.Builder().code(200).body("[]").headersDelay(2, TimeUnit.SECONDS).build()

    @Test fun get_isRetriedOnce_afterReadTimeout_withSameCorrelationId() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(stalled())
        server.enqueue(json(200, "[]"))
        val result = apiCallUnit { h.api.vehicles(null) }
        assertThat(result).isEqualTo(Outcome.Ok(Unit))
        assertThat(server.requestCount).isEqualTo(2)
        val first = server.takeRequest().headers["X-Correlation-Id"]
        val second = server.takeRequest().headers["X-Correlation-Id"]
        assertThat(first).isEqualTo(second)
        val attempts = h.inspector.entries.value.map { it.attempt to it.status }
        assertThat(attempts).containsExactly(2 to 200, 1 to null).inOrder()
    }

    @Test fun get_failsAfterSecondTimeout_asTimeoutNetworkError() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(stalled())
        server.enqueue(stalled())
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        assertThat((result.error as AppError.Network).timeout).isTrue()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun post_isNeverRetried() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(stalled())
        server.enqueue(json(202, "{}"))
        val result = apiCallUnit { h.api.command("VIN1", Cmd("LOCK", "1234")) } as Outcome.Err
        assertThat((result.error as AppError.Network).timeout).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().method).isEqualTo("POST")
    }

    @Test fun httpErrorStatus_isNotRetried() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(500, problem(500, "INTERNAL")))
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        assertThat(result.error).isInstanceOf(AppError.Server::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }
}
