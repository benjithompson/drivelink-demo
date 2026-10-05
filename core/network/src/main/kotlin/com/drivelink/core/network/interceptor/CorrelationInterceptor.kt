package com.drivelink.core.network.interceptor

import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.network.CorrelationTag
import com.drivelink.core.network.DriveLinkHeaders
import com.drivelink.core.network.newCorrelationId
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `X-Correlation-Id` and `X-Client-Version`.
 *
 * Id source, in order: a header that the caller set; the [CorrelationTag] on the request
 * (set by [com.drivelink.core.network.DriveLinkHttp.callFactory]); a new UUID v4. The tag is also
 * set when it was absent, so later retries of the same request reuse the id.
 */
class CorrelationInterceptor(private val buildInfo: AppBuildInfo) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val id = request.header(DriveLinkHeaders.CORRELATION_ID)
            ?: request.tag(CorrelationTag::class.java)?.id
            ?: newCorrelationId()
        val builder = request.newBuilder()
            .header(DriveLinkHeaders.CORRELATION_ID, id)
            .tag(CorrelationTag::class.java, CorrelationTag(id))
        if (buildInfo.clientVersion.isNotBlank()) {
            builder.header(DriveLinkHeaders.CLIENT_VERSION, buildInfo.clientVersion)
        }
        return chain.proceed(builder.build())
    }
}
