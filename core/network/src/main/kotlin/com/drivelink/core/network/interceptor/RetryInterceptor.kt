package com.drivelink.core.network.interceptor

import com.drivelink.core.network.AttemptTag
import com.drivelink.core.network.EndpointNotConfiguredException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * D-16: retries a GET once after a transport error (IOException, timeouts included).
 * POST, PUT and DELETE are never retried, so a remote command is never sent twice.
 * The retry is the same request, so it keeps the same X-Correlation-Id.
 */
class RetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val attempt = request.tag(AttemptTag::class.java)?.number ?: 1
        val first = request.newBuilder().tag(AttemptTag::class.java, AttemptTag(attempt)).build()
        if (request.method != "GET") return chain.proceed(first)
        return try {
            chain.proceed(first)
        } catch (e: IOException) {
            if (e is EndpointNotConfiguredException || chain.call().isCanceled()) throw e
            val retry = request.newBuilder().tag(AttemptTag::class.java, AttemptTag(attempt + 1)).build()
            try {
                chain.proceed(retry)
            } catch (second: IOException) {
                second.addSuppressed(e)
                throw second
            }
        }
    }
}
