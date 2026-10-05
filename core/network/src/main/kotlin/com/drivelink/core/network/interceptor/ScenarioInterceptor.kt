package com.drivelink.core.network.interceptor

import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.network.DriveLinkHeaders
import okhttp3.Interceptor
import okhttp3.Response

/** Adds `X-Scenario: <name>` from [DemoConfig.scenario]. No header for "default" or blank. */
class ScenarioInterceptor(private val demoConfig: DemoConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val scenario = demoConfig.scenario.value.trim()
        val request = chain.request()
        if (scenario.isEmpty() || scenario == DriveLinkHeaders.DEFAULT_SCENARIO) {
            return chain.proceed(request.newBuilder().removeHeader(DriveLinkHeaders.SCENARIO).build())
        }
        return chain.proceed(request.newBuilder().header(DriveLinkHeaders.SCENARIO, scenario).build())
    }
}
