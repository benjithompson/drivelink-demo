package com.drivelink.core.data

import com.drivelink.core.data.repository.AlertsRepositoryImpl
import com.drivelink.core.data.repository.AuthRepositoryImpl
import com.drivelink.core.data.repository.CommandRepositoryImpl
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.Session
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.model.CommandStatus
import com.drivelink.core.domain.model.CommandType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AuthCommandAlertsTest {
    private val server = MockWebServer()

    @Before fun setUp() = server.start()
    @After fun tearDown() = server.close()

    @Test fun login_savesSession_andSendsCredentials() = runTest {
        val h = DataHarness(server, session = null)
        server.enqueue(response(200, Examples.read("createToken/200.default.json")))
        val result = AuthRepositoryImpl(h.api, h.sessions).login("alex.rivera@drivelink.test", "demo-password")
        val token = (result as Outcome.Ok).value
        assertThat(h.sessions.session.value).isEqualTo(Session(token.accessToken, token.refreshToken, "alex.rivera@drivelink.test"))
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/v1/auth/token")
        assertThat(request.headers["Authorization"]).isNull()
        assertThat(request.body?.utf8()).contains("\"password\":\"demo-password\"")
    }

    @Test fun loginFailure_keepsNoSession() = runTest {
        val h = DataHarness(server, session = null)
        server.enqueue(response(401, Examples.read("createToken/401.invalid-credentials.json"), "application/problem+json"))
        val result = AuthRepositoryImpl(h.api, h.sessions).login("alex.rivera@drivelink.test", "wrong")
        assertThat((result as Outcome.Err).error).isInstanceOf(AppError.InvalidCredentials::class.java)
        assertThat(h.sessions.session.value).isNull()
    }

    @Test fun logout_clearsSession_withoutCall() = runTest {
        val h = DataHarness(server)
        assertThat(AuthRepositoryImpl(h.api, h.sessions).logout()).isEqualTo(Outcome.Ok(Unit))
        assertThat(h.sessions.session.value).isNull()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun commandSendAndPoll_useContractPaths_andPollAttemptHeader() = runTest {
        val h = DataHarness(server)
        val repo = CommandRepositoryImpl(h.api)
        server.enqueue(response(202, Examples.read("sendCommand/202.default.json")))
        server.enqueue(response(200, Examples.read("getCommand/200.default.json")))
        val sent = (repo.send("DLEV26AURA0000101", CommandRequest(CommandType.LOCK, "1234")) as Outcome.Ok).value
        assertThat(sent.status).isEqualTo(CommandStatus.QUEUED)
        repo.get(sent.commandId, attempt = 3)
        val post = server.takeRequest()
        assertThat(post.url.encodedPath).isEqualTo("/v1/vehicles/DLEV26AURA0000101/commands")
        assertThat(post.body?.utf8()).contains("\"type\":\"LOCK\"")
        val poll = server.takeRequest()
        assertThat(poll.url.encodedPath).isEqualTo("/v1/commands/${sent.commandId}")
        assertThat(poll.headers["X-Poll-Attempt"]).isEqualTo("3")
    }

    @Test fun alerts_vinQueryOptional_andMarkRead204() = runTest {
        val h = DataHarness(server)
        val repo = AlertsRepositoryImpl(h.api)
        server.enqueue(response(200, Examples.read("listAlerts/200.default.json")))
        server.enqueue(response(200, Examples.read("listAlerts/200.default.json")))
        server.enqueue(noContent())
        assertThat(repo.list()).isInstanceOf(Outcome.Ok::class.java)
        repo.list("DLEV26AURA0000101")
        assertThat(repo.markRead("al-1")).isEqualTo(Outcome.Ok(Unit))
        assertThat(server.takeRequest().url.queryParameter("vin")).isNull()
        assertThat(server.takeRequest().url.queryParameter("vin")).isEqualTo("DLEV26AURA0000101")
        val mark = server.takeRequest()
        assertThat(mark.method).isEqualTo("POST")
        assertThat(mark.url.encodedPath).isEqualTo("/v1/alerts/al-1/read")
    }
}
