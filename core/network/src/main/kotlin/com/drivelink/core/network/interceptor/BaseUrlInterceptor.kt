package com.drivelink.core.network.interceptor

import com.drivelink.core.domain.config.ApiGroup
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.network.EndpointNotConfiguredException
import com.drivelink.core.network.EndpointTag
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites scheme, host, port and path prefix of each request to the active endpoint profile.
 * It reads [DemoConfig.activeProfile] on every request, so a profile switch applies to the next
 * call without a new client.
 *
 * Example: base URL `https://h:8443/drivelink` and request path `/v1/vehicles` give
 * `https://h:8443/drivelink/v1/vehicles`. The query string stays.
 *
 * @throws EndpointNotConfiguredException when the resolved base URL is empty or not http/https.
 */
class BaseUrlInterceptor(private val demoConfig: DemoConfig) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val profile = demoConfig.activeProfile.value
        val target = resolve(profile, request.url)
        val builder = request.newBuilder()
            .url(target)
            .tag(EndpointTag::class.java, EndpointTag(profile.name, profile.apiKeyHeader?.takeIf { it.isNotBlank() }, request.url.encodedPath))
        val keyHeader = profile.apiKeyHeader
        val keyValue = profile.apiKeyValue
        if (!keyHeader.isNullOrBlank() && !keyValue.isNullOrEmpty()) {
            builder.header(keyHeader, keyValue)
        }
        return chain.proceed(builder.build())
    }

    companion object {
        /** The API group of a request path such as `/v1/auth/token`. Null means [EndpointProfile.baseUrl]. */
        fun groupOf(url: HttpUrl): ApiGroup? {
            val segments = url.pathSegments
            val index = segments.indexOf("v1")
            val first = segments.getOrNull(if (index >= 0) index + 1 else 0)
            return when (first) {
                "auth" -> ApiGroup.AUTH
                "alerts" -> ApiGroup.ALERTS
                "vehicles", "commands" -> ApiGroup.VEHICLE
                else -> null
            }
        }

        /** The final URL for [original] under [profile]. */
        fun resolve(profile: EndpointProfile, original: HttpUrl): HttpUrl {
            val group = groupOf(original)
            val raw = group?.let { profile.overrides[it] }?.takeIf { it.isNotBlank() } ?: profile.baseUrl
            val base = raw.trim().toHttpUrlOrNull()
                ?: throw EndpointNotConfiguredException(profile.name, raw)
            val prefix = base.encodedPath.trimEnd('/')
            return original.newBuilder()
                .scheme(base.scheme)
                .host(base.host)
                .port(base.port)
                .encodedPath(prefix + original.encodedPath)
                .build()
        }
    }
}
