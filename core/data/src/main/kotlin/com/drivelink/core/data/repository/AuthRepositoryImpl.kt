package com.drivelink.core.data.repository

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.Session
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.domain.model.TokenRequest
import com.drivelink.core.domain.model.TokenResponse
import com.drivelink.core.domain.repository.AuthRepository
import com.drivelink.core.network.api.DriveLinkApi
import com.drivelink.core.network.apiCall
import javax.inject.Inject

/** createToken, then the session goes to [SessionStore]. The network layer refreshes the token on 401. */
class AuthRepositoryImpl @Inject constructor(
    private val api: DriveLinkApi,
    private val sessionStore: SessionStore,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Outcome<TokenResponse> {
        val result = apiCall(TokenResponse.serializer()) { api.createToken(TokenRequest(email, password)) }
        if (result is Outcome.Ok) {
            sessionStore.save(Session(result.value.accessToken, result.value.refreshToken, email))
        }
        return result
    }

    override suspend fun logout(): Outcome<Unit> {
        sessionStore.clear()
        return Outcome.Ok(Unit)
    }
}
