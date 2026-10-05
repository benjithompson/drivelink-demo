package com.drivelink.core.network

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.ApiGroup
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.network.interceptor.BaseUrlInterceptor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Test

class BaseUrlTest {
    private val server = MockWebServer()
    private val second = MockWebServer()

    @Before fun setUp() { server.start(); second.start() }
    @After fun tearDown() { server.close(); second.close() }

    @Test fun resolve_prependsPathPrefix_andKeepsQuery() {
        val profile = EndpointProfile("p", "P", "https://h:8443/drivelink")
        val url = BaseUrlInterceptor.resolve(profile, "http://drivelink.invalid/v1/vehicles?page=2&x=a%20b".toHttpUrl())
        assertThat(url.toString()).isEqualTo("https://h:8443/drivelink/v1/vehicles?page=2&x=a%20b")
    }

    @Test fun resolve_trailingSlashInBase_isIgnored() {
        val profile = EndpointProfile("p", "P", "http://10.0.2.2:8080/")
        val url = BaseUrlInterceptor.resolve(profile, "http://drivelink.invalid/v1/health".toHttpUrl())
        assertThat(url.toString()).isEqualTo("http://10.0.2.2:8080/v1/health")
    }

    @Test fun groupOf_mapsPathPrefixes() {
        fun g(path: String) = BaseUrlInterceptor.groupOf("http://x/v1$path".toHttpUrl())
        assertThat(g("/auth/token")).isEqualTo(ApiGroup.AUTH)
        assertThat(g("/alerts")).isEqualTo(ApiGroup.ALERTS)
        assertThat(g("/vehicles/V/status")).isEqualTo(ApiGroup.VEHICLE)
        assertThat(g("/commands/c1")).isEqualTo(ApiGroup.VEHICLE)
        assertThat(g("/health")).isNull()
        assertThat(g("/me")).isNull()
    }

    @Test fun request_goesToProfileHost_withPathPrefix_andQuery() = runTest {
        val h = Harness(profileFor(server, "/drivelink"))
        server.enqueue(json(200, "[]"))
        apiCallUnit { h.api.vehicles("2") }
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/drivelink/v1/vehicles")
        assertThat(request.url.queryParameter("page")).isEqualTo("2")
    }

    @Test fun groupOverride_sendsThatGroupToAnotherHost() = runTest {
        val profile = profileFor(server).copy(overrides = mapOf(ApiGroup.AUTH to second.url("/auth-svc").toString()))
        val h = Harness(profile)
        second.enqueue(json(200, "{}"))
        server.enqueue(json(200, "[]"))
        apiCallUnit { h.api.token(JsonObject(emptyMap())) }
        apiCallUnit { h.api.vehicles(null) }
        assertThat(second.takeRequest().url.encodedPath).isEqualTo("/auth-svc/v1/auth/token")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/v1/vehicles")
    }

    @Test fun blankOverride_usesBaseUrl() = runTest {
        val h = Harness(profileFor(server).copy(overrides = mapOf(ApiGroup.VEHICLE to " ")))
        server.enqueue(json(200, "[]"))
        assertThat(apiCallUnit { h.api.vehicles(null) }).isEqualTo(Outcome.Ok(Unit))
    }

    @Test fun profileSwitch_redirectsNextCall_withoutNewClient() = runTest {
        val h = Harness(profileFor(server))
        server.enqueue(json(200, "[]"))
        second.enqueue(json(200, "[]"))
        apiCallUnit { h.api.vehicles(null) }
        h.config.activeProfile.value = EndpointProfile("two", "Second", second.url("/").toString())
        apiCallUnit { h.api.vehicles(null) }
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(second.requestCount).isEqualTo(1)
        assertThat(h.inspector.entries.value.first().profileName).isEqualTo("Second")
    }

    @Test fun emptyBaseUrl_isNetworkError_namingTheProfile() = runTest {
        val h = Harness(EndpointProfile("private-location", "Private location", ""))
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        val error = result.error as AppError.Network
        assertThat(error.timeout).isFalse()
        assertThat(error.message).contains("Private location")
        assertThat(error.correlationId).matches(UUID_V4.pattern)
    }

    @Test fun invalidBaseUrl_isNetworkError() = runTest {
        val h = Harness(EndpointProfile("x", "Broken", "ftp://host"))
        val result = apiCallUnit { h.api.vehicles(null) } as Outcome.Err
        assertThat(result.error.message).contains("Broken")
    }
}
