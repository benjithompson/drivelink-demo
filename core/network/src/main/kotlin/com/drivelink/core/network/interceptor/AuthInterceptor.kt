package com.drivelink.core.network.interceptor

import com.drivelink.core.domain.config.Session
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.network.AttemptTag
import com.drivelink.core.network.DriveLinkHeaders
import com.drivelink.core.network.EndpointTag
import com.drivelink.core.network.TokenRefresher
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <access token>` from [SessionStore.session], except on
 * /v1/health, /v1/auth/token and /v1/auth/refresh.
 *
 * On a 401 (not for /auth/ paths) it makes one refresh attempt through [tokenRefresher].
 * The refresh is synchronized: when several calls get 401 at the same time, only the first one
 * refreshes; the others see the new token and retry with it. A new session is saved and the
 * request is sent once more. When the refresh fails, the session is cleared and the original
 * 401 goes back to the caller. There is never more than one retry.
 */
class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val tokenRefresher: TokenRefresher,
) : Interceptor {

    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (isPublic(original)) {
            return chain.proceed(original.newBuilder().removeHeader(DriveLinkHeaders.AUTHORIZATION).build())
        }
        val sentToken = sessionStore.session.value?.accessToken
        val response = chain.proceed(original.withToken(sentToken))
        if (response.code != 401 || isAuthPath(original) || sentToken == null) return response

        val newToken = synchronized(lock) { refreshedToken(sentToken) } ?: return response
        response.close()
        val attempt = (original.tag(AttemptTag::class.java)?.number ?: 1) + 1
        val retry = original.withToken(newToken).newBuilder()
            .tag(AttemptTag::class.java, AttemptTag(attempt))
            .build()
        return chain.proceed(retry)
    }

    /** Returns the token to retry with, or null when the caller must get the 401. Runs under [lock]. */
    private fun refreshedToken(sentToken: String): String? {
        val current = sessionStore.session.value ?: return null
        // Another call refreshed while this one waited for the lock.
        if (current.accessToken != sentToken) return current.accessToken
        val refreshed: Session? = try {
            tokenRefresher.refresh(current)
        } catch (e: Exception) {
            null
        }
        return runBlocking {
            if (refreshed != null) {
                sessionStore.save(refreshed)
                refreshed.accessToken
            } else {
                sessionStore.clear()
                null
            }
        }
    }

    private fun Request.withToken(token: String?): Request {
        val builder = newBuilder()
        if (token.isNullOrEmpty()) builder.removeHeader(DriveLinkHeaders.AUTHORIZATION)
        else builder.header(DriveLinkHeaders.AUTHORIZATION, "Bearer $token")
        return builder.build()
    }

    private fun apiPath(request: Request): String {
        val path = request.tag(EndpointTag::class.java)?.apiPath ?: request.url.encodedPath
        val index = path.indexOf("/v1/")
        return if (index >= 0) path.substring(index + 3) else path
    }

    private fun isPublic(request: Request): Boolean = apiPath(request).trimEnd('/') in PUBLIC_PATHS

    private fun isAuthPath(request: Request): Boolean = apiPath(request).startsWith("/auth/")

    private companion object {
        val PUBLIC_PATHS = setOf("/health", "/auth/token", "/auth/refresh")
    }
}
