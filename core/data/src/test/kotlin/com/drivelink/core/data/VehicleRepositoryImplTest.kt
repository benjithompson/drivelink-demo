package com.drivelink.core.data

import com.drivelink.core.data.repository.VehicleRepositoryImpl
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.Connectivity
import com.drivelink.core.domain.model.Powertrain
import com.drivelink.core.domain.model.VehicleStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class VehicleRepositoryImplTest {
    private val server = MockWebServer()
    private lateinit var h: DataHarness
    private lateinit var repo: VehicleRepositoryImpl

    @Before fun setUp() {
        server.start()
        h = DataHarness(server)
        repo = VehicleRepositoryImpl(h.api)
    }

    @After fun tearDown() = server.close()

    @Test fun status200_parsesContractExample() = runTest {
        server.enqueue(response(200, Examples.read("getVehicleStatus/200.default.json")))
        val result = repo.getStatus("DLEV26AURA0000101")
        val status = (result as Outcome.Ok<VehicleStatus>).value
        assertThat(status.vin).isEqualTo("DLEV26AURA0000101")
        assertThat(status.powertrain).isEqualTo(Powertrain.EV)
        assertThat(status.connectivity).isEqualTo(Connectivity.ONLINE)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.url.encodedPath).isEqualTo("/v1/vehicles/DLEV26AURA0000101/status")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer at-demo-1234")
    }

    @Test fun status404_mapsProblemToNotFound_withServerCorrelationId() = runTest {
        server.enqueue(response(404, Examples.read("getVehicleStatus/404.unknown-vin.json"), "application/problem+json"))
        val error = (repo.getStatus("UNKNOWN0000000000") as Outcome.Err).error
        assertThat(error).isInstanceOf(AppError.NotFound::class.java)
        assertThat(error.correlationId).isNotEmpty()
    }

    @Test fun statusBadJson_mapsToParse_withSentCorrelationId() = runTest {
        server.enqueue(response(200, Examples.readRaw("getVehicleStatus.bad-payload.json")))
        val error = (repo.getStatus("DLEV26AURA0000101") as Outcome.Err).error
        assertThat(error).isInstanceOf(AppError.Parse::class.java)
        assertThat(error.correlationId).isEqualTo(server.takeRequest().headers["X-Correlation-Id"])
    }

    @Test fun listVehicles_parsesBothDemoCars() = runTest {
        server.enqueue(response(200, Examples.read("listVehicles/200.default.json")))
        val vehicles = (repo.listVehicles() as Outcome.Ok).value
        assertThat(vehicles.map { it.vin }).containsExactly("DLEV26AURA0000101", "DLGS25SLACE000202").inOrder()
    }

    @Test fun listTrips_sendsLimit_clampedTo50() = runTest {
        server.enqueue(response(200, Examples.read("listTrips/200.default.json")))
        repo.listTrips("DLEV26AURA0000101", limit = 99)
        assertThat(server.takeRequest().url.queryParameter("limit")).isEqualTo("50")
    }

    @Test fun server500_mapsToServer() = runTest {
        server.enqueue(response(500, Examples.read("getVehicleStatus/500.server-error.json"), "application/problem+json"))
        val error = (repo.getStatus("DLEV26AURA0000101") as Outcome.Err).error
        assertThat(error).isInstanceOf(AppError.Server::class.java)
    }
}
