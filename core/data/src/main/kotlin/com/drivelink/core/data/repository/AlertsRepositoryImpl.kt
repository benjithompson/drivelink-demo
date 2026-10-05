package com.drivelink.core.data.repository

import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.model.Alert
import com.drivelink.core.domain.repository.AlertsRepository
import com.drivelink.core.network.api.DriveLinkApi
import com.drivelink.core.network.apiCall
import com.drivelink.core.network.apiCallUnit
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

class AlertsRepositoryImpl @Inject constructor(
    private val api: DriveLinkApi,
) : AlertsRepository {

    override suspend fun list(vin: String?): Outcome<List<Alert>> =
        apiCall(ListSerializer(Alert.serializer())) { api.listAlerts(vin) }

    override suspend fun markRead(alertId: String): Outcome<Unit> = apiCallUnit { api.markAlertRead(alertId) }
}
