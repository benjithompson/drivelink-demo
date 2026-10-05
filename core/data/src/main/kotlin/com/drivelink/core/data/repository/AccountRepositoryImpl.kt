package com.drivelink.core.data.repository

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.model.Health
import com.drivelink.core.domain.model.User
import com.drivelink.core.domain.repository.AccountRepository
import com.drivelink.core.network.api.DriveLinkApi
import com.drivelink.core.network.apiCall
import javax.inject.Inject

class AccountRepositoryImpl @Inject constructor(
    private val api: DriveLinkApi,
) : AccountRepository {

    override suspend fun getHealth(): Outcome<Health> = apiCall(Health.serializer()) { api.getHealth() }

    override suspend fun getMe(): Outcome<User> = apiCall(User.serializer()) { api.getMe() }
}
