package com.drivelink.core.network

import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.network.inspector.InspectorInterceptor
import com.drivelink.core.network.inspector.NetworkInspector
import com.drivelink.core.network.interceptor.AuthInterceptor
import com.drivelink.core.network.interceptor.BaseUrlInterceptor
import com.drivelink.core.network.interceptor.CorrelationInterceptor
import com.drivelink.core.network.interceptor.RetryInterceptor
import com.drivelink.core.network.interceptor.ScenarioInterceptor
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.time.toJavaDuration

/**
 * Builds the DriveLink OkHttp client and Retrofit instance.
 *
 * Interceptor order (application interceptors): BaseUrl, Auth, Scenario, Correlation, Retry,
 * Inspector, then [extraInterceptors] (the app adds HttpLoggingInterceptor there in debug builds).
 *
 * Retrofit uses [PLACEHOLDER_BASE_URL]; [BaseUrlInterceptor] sets the real host on each request.
 * API interface methods should return `retrofit2.Response<okhttp3.ResponseBody>` and be called
 * through [apiCall], which decodes the body with [DriveLinkJson]. `@Body` parameters are encoded
 * with the kotlinx-serialization converter and [DriveLinkJson].
 *
 * @param tokenRefresher null uses [HttpTokenRefresher] over this client.
 */
class DriveLinkHttp(
    demoConfig: DemoConfig,
    sessionStore: SessionStore,
    buildInfo: AppBuildInfo,
    inspector: NetworkInspector,
    tokenRefresher: TokenRefresher? = null,
    timeouts: NetworkTimeouts = NetworkTimeouts(),
    extraInterceptors: List<Interceptor> = emptyList(),
) {

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BaseUrlInterceptor(demoConfig))
        .addInterceptor(AuthInterceptor(sessionStore, tokenRefresher ?: HttpTokenRefresher { callFactory }))
        .addInterceptor(ScenarioInterceptor(demoConfig))
        .addInterceptor(CorrelationInterceptor(buildInfo))
        .addInterceptor(RetryInterceptor())
        .addInterceptor(InspectorInterceptor(inspector))
        .apply { extraInterceptors.forEach { addInterceptor(it) } }
        .connectTimeout(timeouts.connect.toJavaDuration())
        .readTimeout(timeouts.read.toJavaDuration())
        .writeTimeout(timeouts.write.toJavaDuration())
        .callTimeout(timeouts.call.toJavaDuration())
        .build()

    /**
     * [okHttpClient] plus the correlation id from [apiCall]: each new call gets a [CorrelationTag]
     * with the id of the enclosing [apiCall] (or a new UUID outside [apiCall]).
     * Use this, not [okHttpClient], for direct OkHttp calls.
     */
    val callFactory: Call.Factory = Call.Factory { request ->
        val tagged = if (request.tag(CorrelationTag::class.java) != null) {
            request
        } else {
            val id = CorrelationContext.current.get() ?: newCorrelationId()
            request.newBuilder().tag(CorrelationTag::class.java, CorrelationTag(id)).build()
        }
        okHttpClient.newCall(tagged)
    }

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .callFactory(callFactory)
        .addConverterFactory(DriveLinkJson.asConverterFactory("application/json".toMediaType()))
        .build()

    inline fun <reified T : Any> create(): T = retrofit.create(T::class.java)

    companion object {
        /** Retrofit base URL. The host is never contacted; BaseUrlInterceptor replaces it. */
        const val PLACEHOLDER_BASE_URL = "http://drivelink.invalid/v1/"
    }
}
