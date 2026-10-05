package com.drivelink.core.network.api

import com.drivelink.core.domain.model.ChargeSettings
import com.drivelink.core.domain.model.ClimatePresets
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.model.RefreshRequest
import com.drivelink.core.domain.model.ServiceRequestCreate
import com.drivelink.core.domain.model.TokenRequest
import com.drivelink.core.network.DriveLinkHeaders
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The 18 operations of api/openapi.yaml. Paths are relative to the Retrofit base URL
 * ([com.drivelink.core.network.DriveLinkHttp.PLACEHOLDER_BASE_URL], which ends in `/v1/`).
 *
 * Every method returns the raw [Response]; call it through
 * [com.drivelink.core.network.apiCall] or [com.drivelink.core.network.apiCallUnit], which add the
 * correlation id, decode the body and map errors. The interceptors add the auth, scenario,
 * correlation and client version headers.
 */
interface DriveLinkApi {

    /** getHealth. No auth. */
    @GET("health")
    suspend fun getHealth(): Response<ResponseBody>

    /** createToken. No auth. */
    @POST("auth/token")
    suspend fun createToken(@Body body: TokenRequest): Response<ResponseBody>

    /** refreshToken. No auth. The auth interceptor normally refreshes; this is for completeness. */
    @POST("auth/refresh")
    suspend fun refreshToken(@Body body: RefreshRequest): Response<ResponseBody>

    /** getMe. */
    @GET("me")
    suspend fun getMe(): Response<ResponseBody>

    /** listVehicles. */
    @GET("vehicles")
    suspend fun listVehicles(): Response<ResponseBody>

    /** getVehicleStatus. */
    @GET("vehicles/{vin}/status")
    suspend fun getVehicleStatus(@Path("vin") vin: String): Response<ResponseBody>

    /** sendCommand. 202 with the queued command. */
    @POST("vehicles/{vin}/commands")
    suspend fun sendCommand(@Path("vin") vin: String, @Body body: CommandRequest): Response<ResponseBody>

    /** getCommand. [attempt] is the poll number (1, 2, 3 ...). */
    @GET("commands/{commandId}")
    suspend fun getCommand(
        @Path("commandId") commandId: String,
        @Header(DriveLinkHeaders.POLL_ATTEMPT) attempt: Int,
    ): Response<ResponseBody>

    /** getVehicleLocation. */
    @GET("vehicles/{vin}/location")
    suspend fun getVehicleLocation(@Path("vin") vin: String): Response<ResponseBody>

    /** getChargeSettings. */
    @GET("vehicles/{vin}/charge-settings")
    suspend fun getChargeSettings(@Path("vin") vin: String): Response<ResponseBody>

    /** updateChargeSettings. */
    @PUT("vehicles/{vin}/charge-settings")
    suspend fun updateChargeSettings(@Path("vin") vin: String, @Body body: ChargeSettings): Response<ResponseBody>

    /** getClimatePresets. */
    @GET("vehicles/{vin}/climate-presets")
    suspend fun getClimatePresets(@Path("vin") vin: String): Response<ResponseBody>

    /** updateClimatePresets. */
    @PUT("vehicles/{vin}/climate-presets")
    suspend fun updateClimatePresets(@Path("vin") vin: String, @Body body: ClimatePresets): Response<ResponseBody>

    /** listTrips. [limit] is 1..50. */
    @GET("vehicles/{vin}/trips")
    suspend fun listTrips(@Path("vin") vin: String, @Query("limit") limit: Int): Response<ResponseBody>

    /** getMaintenance. */
    @GET("vehicles/{vin}/maintenance")
    suspend fun getMaintenance(@Path("vin") vin: String): Response<ResponseBody>

    /** createServiceRequest. 201. */
    @POST("vehicles/{vin}/service-requests")
    suspend fun createServiceRequest(@Path("vin") vin: String, @Body body: ServiceRequestCreate): Response<ResponseBody>

    /** listAlerts. A null [vin] omits the query parameter. */
    @GET("alerts")
    suspend fun listAlerts(@Query("vin") vin: String?): Response<ResponseBody>

    /** markAlertRead. 204, no body. */
    @POST("alerts/{alertId}/read")
    suspend fun markAlertRead(@Path("alertId") alertId: String): Response<ResponseBody>
}
