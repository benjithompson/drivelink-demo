package com.drivelink.core.network

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.Session
import com.drivelink.core.domain.error.AppError
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AuthTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    private val tokenOk = """{"accessToken":"at-new-5678","expiresIn":3600,"refreshToken":"rt-new"}"""

    @Test fun unauthorized_refreshesOnce_andRetriesWithNewToken() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(401, problem(401, "TOKEN_EXPIRED"), "application/problem+json"))
        server.enqueue(json(200, tokenOk))
        server.enqueue(json(200, "[]"))

        val result = apiCallUnit { h.api.vehicles(null) }

        assertThat(result).isEqualTo(Outcome.Ok(Unit))
        val first = server.takeRequest()
        val refresh = server.takeRequest()
        val retry = server.takeRequest()
        assertThat(first.headers["Authorization"]).isEqualTo("Bearer at-old-1234")
        assertThat(refresh.url.encodedPath).isEqualTo("/v1/auth/refresh")
        assertThat(refresh.headers["Authorization"]).isNull()
        assertThat(refresh.body?.utf8()).isEqualTo("""{"refreshToken":"rt-old"}""")
        assertThat(retry.headers["Authorization"]).isEqualTo("Bearer at-new-5678")
        assertThat(retry.headers["X-Correlation-Id"]).isEqualTo(first.headers["X-Correlation-Id"])
        assertThat(h.sessions.session.value).isEqualTo(Session("at-new-5678", "rt-new", "alex@drivelink.test"))
    }

    @Test fun refreshFails_clearsSession_andReturnsUnauthorized() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(401, problem(401, "TOKEN_EXPIRED", "srv-first"), "application/problem+json"))
        server.enqueue(json(401, problem(401, "TOKEN_EXPIRED"), "application/problem+json"))

        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err

        assertThat(result.error).isEqualTo(AppError.Unauthorized("srv-first"))
        assertThat(h.sessions.session.value).isNull()
        assertThat(h.sessions.clears).isEqualTo(1)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun refresherThatThrows_isTreatedAsFailure() = runTest {
        val h = Harness(profileFor(server), tokenRefresher = { error("boom") })
        server.enqueue(json(401, problem(401, "TOKEN_EXPIRED")))
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        assertThat(result.error).isInstanceOf(AppError.Unauthorized::class.java)
        assertThat(h.sessions.session.value).isNull()
    }

    @Test fun retryAfterRefresh_stillUnauthorized_doesNotLoop() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(401, problem(401, "TOKEN_EXPIRED")))
        server.enqueue(json(200, tokenOk))
        server.enqueue(json(401, problem(401, "TOKEN_EXPIRED")))
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        assertThat(result.error).isInstanceOf(AppError.Unauthorized::class.java)
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test fun invalidCredentials_onAuthToken_doesNotRefresh() = runTest {
        val h = Harness(profileFor(server), session = null)
        server.enqueue(json(401, problem(401, "INVALID_CREDENTIALS"), "application/problem+json"))
        val result = apiCallUnit { h.api.token(JsonObject(emptyMap())) } as Outcome.Err
        assertThat(result.error).isInstanceOf(AppError.InvalidCredentials::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun noSession_401_doesNotRefresh() = runTest {
        val h = Harness(profileFor(server), session = null)
        server.enqueue(json(401, problem(401, "TOKEN_EXPIRED")))
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        assertThat(result.error).isInstanceOf(AppError.Unauthorized::class.java)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun concurrent401s_refreshOnlyOnce() = runTest {
        val refreshes = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath == "/v1/auth/refresh" -> {
                    refreshes.incrementAndGet()
                    MockResponse.Builder().code(200).body(tokenOk).headersDelay(200, TimeUnit.MILLISECONDS).build()
                }
                request.headers["Authorization"] == "Bearer at-new-5678" -> json(200, "[]")
                else -> MockResponse.Builder().code(401).body(problem(401, "TOKEN_EXPIRED"))
                    .headersDelay(50, TimeUnit.MILLISECONDS).build()
            }
        }
        val h = Harness(profileFor(server))

        val results = withContext(Dispatchers.IO) {
            (1..6).map { async { apiCallUnit { h.api.vehicles(null) } } }.awaitAll()
        }

        assertThat(results.toSet()).containsExactly(Outcome.Ok(Unit))
        assertThat(refreshes.get()).isEqualTo(1)
        assertThat(h.sessions.saves).isEqualTo(1)
    }
}
