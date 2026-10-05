package com.drivelink.core.domain.error

/**
 * Typed error for every failed call. Screens show [message] and, when present, [correlationId].
 *
 * [correlationId]: the problem body value when the server sent one, else the X-Correlation-Id
 * that the app sent with the request. Null only when no request was made.
 */
sealed interface AppError {
    val correlationId: String?
    val message: String

    /** 401 TOKEN_EXPIRED after the single refresh attempt failed. The app returns to login. */
    data class Unauthorized(override val correlationId: String?, override val message: String = "Your session has expired. Sign in again.") : AppError

    /** 401 INVALID_CREDENTIALS from createToken. */
    data class InvalidCredentials(override val correlationId: String?, override val message: String = "The email or password is incorrect.") : AppError

    /** 403 INVALID_PIN from sendCommand. */
    data class InvalidPin(override val correlationId: String?, override val message: String = "The PIN is incorrect.") : AppError

    /** 409 VEHICLE_OFFLINE. */
    data class VehicleOffline(override val correlationId: String?, override val message: String = "The vehicle is offline. Try again when it has a connection.") : AppError

    /** VEHICLE_ASLEEP. */
    data class VehicleAsleep(override val correlationId: String?, override val message: String = "The vehicle is asleep. Try again in a moment.") : AppError

    /** 409 COMMAND_CONFLICT, or any other 409. */
    data class Conflict(override val correlationId: String?, override val message: String = "Another command is in progress.") : AppError

    /** 429 RATE_LIMITED. [retryAfterSec] from the Retry-After header, null when absent. */
    data class RateLimited(val retryAfterSec: Int?, override val correlationId: String?, override val message: String = "Too many requests. Wait and try again.") : AppError

    /** 404 NOT_FOUND. */
    data class NotFound(override val correlationId: String?, override val message: String = "Not found.") : AppError

    /** 400 INVALID_REQUEST, or any other 4xx without a specific mapping. */
    data class InvalidRequest(val status: Int, override val correlationId: String?, override val message: String = "The request was not accepted.") : AppError

    /** 5xx, INTERNAL. */
    data class Server(val status: Int, override val correlationId: String?, override val message: String = "Something went wrong on our side.") : AppError

    /** Transport failure: no connection, DNS, TLS, timeout (call timeout 15 s). */
    data class Network(val timeout: Boolean, override val correlationId: String?, override val message: String = "Cannot reach the server. Check the connection.") : AppError

    /** A 2xx body that does not match the schema (bad-payload). Never crashes the app. */
    data class Parse(val detail: String, override val correlationId: String?, override val message: String = "The server sent data that the app cannot read.") : AppError
}
