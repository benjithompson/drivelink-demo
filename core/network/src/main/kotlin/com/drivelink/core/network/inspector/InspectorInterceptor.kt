package com.drivelink.core.network.inspector

import com.drivelink.core.network.AttemptTag
import com.drivelink.core.network.DriveLinkHeaders
import com.drivelink.core.network.EndpointTag
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException

/**
 * Records each attempt in [inspector]. It is the last DriveLink interceptor (after the retry
 * interceptor), so each retry is a separate entry. The response body is read with `peekBody`,
 * so the caller still gets the full body.
 */
class InspectorInterceptor(
    private val inspector: NetworkInspector,
    private val clock: () -> Long = System::currentTimeMillis,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val id = inspector.nextId()
        val startedAt = clock()
        val startNanos = System.nanoTime()
        val endpoint = request.tag(EndpointTag::class.java)
        val requestHeaders = mask(request.headers, endpoint?.apiKeyHeader) + bodyContentType(request)
        val requestBody = bodyText(request)

        fun entry(status: Int?, responseHeaders: List<Pair<String, String>>, responseBody: String?, error: String?) =
            InspectorEntry(
                id = id,
                startedAtEpochMs = startedAt,
                method = request.method,
                url = request.url.toString(),
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                status = status,
                responseHeaders = responseHeaders,
                responseBody = responseBody,
                durationMs = (System.nanoTime() - startNanos) / 1_000_000,
                error = error,
                correlationId = request.header(DriveLinkHeaders.CORRELATION_ID),
                scenario = request.header(DriveLinkHeaders.SCENARIO) ?: DriveLinkHeaders.DEFAULT_SCENARIO,
                profileName = endpoint?.profileName,
                attempt = request.tag(AttemptTag::class.java)?.number ?: 1,
            )

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            inspector.record(entry(null, emptyList(), null, "${e.javaClass.simpleName}: ${e.message}"))
            throw e
        }
        val responseBody = try {
            responseText(response)
        } catch (e: IOException) {
            "[body not readable: ${e.javaClass.simpleName}: ${e.message}]"
        }
        inspector.record(entry(response.code, mask(response.headers, null), responseBody, null))
        return response
    }

    /**
     * OkHttp adds Content-Type from the body after the application interceptors. Record it, so
     * the detail view and "Copy as cURL" show it.
     */
    private fun bodyContentType(request: Request): List<Pair<String, String>> {
        val type = request.body?.contentType() ?: return emptyList()
        return if (request.header("Content-Type") == null) listOf("Content-Type" to type.toString()) else emptyList()
    }

    private fun bodyText(request: Request): String? {
        val body = request.body ?: return null
        if (body.isOneShot() || body.isDuplex()) return "[streaming body not recorded]"
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            truncate(buffer)
        } catch (e: IOException) {
            "[body not readable: ${e.message}]"
        }
    }

    private fun responseText(response: Response): String? {
        val peeked = response.peekBody(NetworkInspector.MAX_BODY_BYTES + 1L)
        val bytes = peeked.bytes()
        if (bytes.isEmpty()) return null
        return truncate(Buffer().write(bytes))
    }

    private fun truncate(buffer: Buffer): String {
        val max = NetworkInspector.MAX_BODY_BYTES.toLong()
        return if (buffer.size > max) {
            buffer.readUtf8(max) + NetworkInspector.TRUNCATED_MARKER
        } else {
            buffer.readUtf8()
        }
    }

    private fun mask(headers: Headers, apiKeyHeader: String?): List<Pair<String, String>> =
        headers.map { (name, value) ->
            val masked = when {
                name.equals(DriveLinkHeaders.AUTHORIZATION, ignoreCase = true) -> NetworkInspector.maskBearer(value)
                apiKeyHeader != null && name.equals(apiKeyHeader, ignoreCase = true) -> NetworkInspector.MASK
                else -> value
            }
            name to masked
        }
}
