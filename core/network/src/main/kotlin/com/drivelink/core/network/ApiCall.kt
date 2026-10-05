package com.drivelink.core.network

import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.error.Problem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/**
 * Runs one logical API call and maps the result to an [Outcome]. Never throws for HTTP, parse
 * or transport errors; [kotlinx.coroutines.CancellationException] propagates.
 *
 * Correlation id: this function generates the id and makes it visible to
 * [DriveLinkHttp.callFactory] through a coroutine ThreadLocal element, so the request sends it as
 * X-Correlation-Id. [AppError.correlationId] is the problem body value when present, else the
 * header on the request that produced the response, else the generated id (transport errors).
 *
 * @param block the Retrofit call, for example `{ api.getStatus(vin) }`.
 */
suspend fun <T> apiCall(
    deserializer: DeserializationStrategy<T>,
    block: suspend () -> Response<ResponseBody>,
): Outcome<T> = execute(block) { body, correlationId ->
    decodeBody(body, deserializer, correlationId)
}

/** [apiCall] for responses without a body (204) or whose body the caller ignores. */
suspend fun apiCallUnit(block: suspend () -> Response<ResponseBody>): Outcome<Unit> =
    execute(block) { _, _ -> Outcome.Ok(Unit) }

private suspend fun <T> execute(
    block: suspend () -> Response<ResponseBody>,
    onSuccess: (body: String, correlationId: String) -> Outcome<T>,
): Outcome<T> {
    val generatedId = newCorrelationId()
    return try {
        withContext(CorrelationContext.current.asContextElement(generatedId)) {
            val response = block()
            val sentId = response.raw().request.header(DriveLinkHeaders.CORRELATION_ID) ?: generatedId
            withContext(Dispatchers.IO) {
                if (response.isSuccessful) {
                    val text = response.body()?.use { it.string() }.orEmpty()
                    onSuccess(text, sentId)
                } else {
                    val text = response.errorBody()?.use { it.string() }.orEmpty()
                    Outcome.Err(ErrorMapper.httpError(response.code(), text, response.headers()[DriveLinkHeaders.RETRY_AFTER], sentId))
                }
            }
        }
    } catch (e: IOException) {
        Outcome.Err(ErrorMapper.transportError(e, generatedId))
    }
}

private fun <T> decodeBody(text: String, deserializer: DeserializationStrategy<T>, correlationId: String): Outcome<T> =
    try {
        Outcome.Ok(DriveLinkJson.decodeFromString(deserializer, text))
    } catch (e: SerializationException) {
        Outcome.Err(AppError.Parse(e.message ?: e.javaClass.simpleName, correlationId))
    } catch (e: IllegalArgumentException) {
        Outcome.Err(AppError.Parse(e.message ?: e.javaClass.simpleName, correlationId))
    }

/** Maps HTTP errors and transport exceptions to [AppError]. */
object ErrorMapper {

    /**
     * A non-2xx response. The problem `code` wins over [status]. An unknown or absent code, or a
     * body that is not a problem (BlazeMeter answers an unmatched request with text 404
     * "No match Found"), maps by status.
     */
    fun httpError(status: Int, body: String, retryAfter: String?, sentCorrelationId: String?): AppError {
        val problem = parseProblem(body)
        val id = problem?.correlationId?.takeIf { it.isNotBlank() } ?: sentCorrelationId
        val retryAfterSec = retryAfter?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
        return when (problem?.code) {
            "INVALID_CREDENTIALS" -> AppError.InvalidCredentials(id)
            "TOKEN_EXPIRED" -> AppError.Unauthorized(id)
            "INVALID_PIN" -> AppError.InvalidPin(id)
            "VEHICLE_OFFLINE" -> AppError.VehicleOffline(id)
            "VEHICLE_ASLEEP" -> AppError.VehicleAsleep(id)
            "COMMAND_CONFLICT" -> AppError.Conflict(id)
            "RATE_LIMITED" -> AppError.RateLimited(retryAfterSec, id)
            "NOT_FOUND" -> AppError.NotFound(id)
            "INVALID_REQUEST" -> AppError.InvalidRequest(status, id)
            "INTERNAL" -> AppError.Server(status, id)
            else -> when {
                status == 401 -> AppError.Unauthorized(id)
                status == 403 -> AppError.InvalidRequest(403, id)
                status == 404 -> AppError.NotFound(id)
                status == 409 -> AppError.Conflict(id)
                status == 429 -> AppError.RateLimited(retryAfterSec, id)
                status in 400..499 -> AppError.InvalidRequest(status, id)
                else -> AppError.Server(status, id)
            }
        }
    }

    /** A transport failure: no response. */
    fun transportError(e: IOException, correlationId: String?): AppError.Network = when (e) {
        is EndpointNotConfiguredException -> AppError.Network(
            timeout = false,
            correlationId = correlationId,
            message = "${e.message} Set it in the Demo console.",
        )
        else -> AppError.Network(timeout = isTimeout(e), correlationId = correlationId)
    }

    private fun isTimeout(e: IOException): Boolean =
        e is SocketTimeoutException || (e is InterruptedIOException && e.message == "timeout")

    private fun parseProblem(body: String): Problem? {
        if (body.isBlank()) return null
        return try {
            DriveLinkJson.decodeFromString(Problem.serializer(), body)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
