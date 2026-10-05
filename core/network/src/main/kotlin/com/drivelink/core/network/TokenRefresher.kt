package com.drivelink.core.network

import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.config.Session
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Exchanges the refresh token for a new session. Called by
 * [com.drivelink.core.network.interceptor.AuthInterceptor] on an OkHttp thread while it holds
 * the refresh lock.
 *
 * Contract: blocking; returns null when the refresh fails (any reason). An implementation must
 * use a synchronous call (`Call.execute()`), not a suspend Retrofit call with `runBlocking`:
 * an async call needs a free dispatcher slot, and the slots can all be held by requests that
 * wait for this refresh.
 */
fun interface TokenRefresher {
    fun refresh(session: Session): Session?
}

/**
 * Default [TokenRefresher]: `POST /v1/auth/refresh` through the DriveLink client (so the base
 * URL, scenario, correlation id and inspector apply), with a synchronous call.
 * [callFactory] is a function because the client that contains the auth interceptor is
 * built after this object.
 */
class HttpTokenRefresher(private val callFactory: () -> Call.Factory) : TokenRefresher {

    @Serializable
    private data class RefreshRequest(val refreshToken: String)

    @Serializable
    private data class TokenResponse(val accessToken: String, val expiresIn: Long = 0, val refreshToken: String)

    override fun refresh(session: Session): Session? {
        val body = DriveLinkJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(session.refreshToken))
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(DriveLinkHttp.PLACEHOLDER_BASE_URL + "auth/refresh")
            .post(body)
            .build()
        return try {
            callFactory().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val token = DriveLinkJson.decodeFromString(TokenResponse.serializer(), response.body.string())
                session.copy(accessToken = token.accessToken, refreshToken = token.refreshToken)
            }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
