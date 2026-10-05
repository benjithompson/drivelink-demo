package com.drivelink.core.domain.model

import kotlinx.serialization.Serializable

/** 200 body of getMaintenance (GET /vehicles/{vin}/maintenance). */
@Serializable
data class Maintenance(
    val odometerMi: Int,
    val lastService: ServicePoint? = null,
    val nextService: ServicePoint? = null,
    val intervalMi: Int? = null,
    val items: List<ServiceItem>,
    val recalls: List<Recall>,
    val preferredServiceCenter: ServiceCenter? = null,
)

@Serializable
data class ServicePoint(
    val odometerMi: Int,
    /** yyyy-MM-dd. */
    val date: String,
)

@Serializable
data class ServiceItem(
    val id: String,
    val name: String,
    val dueMi: Int? = null,
    /** yyyy-MM-dd. */
    val dueDate: String? = null,
    val status: ServiceItemStatus,
)

@Serializable
enum class ServiceItemStatus { UPCOMING, DUE, OVERDUE }

@Serializable
data class Recall(
    val id: String,
    val campaign: String,
    val title: String,
    val description: String? = null,
    val status: RecallStatus,
    /** yyyy-MM-dd. */
    val issuedDate: String? = null,
)

@Serializable
enum class RecallStatus { OPEN, REMEDIED }

@Serializable
data class ServiceCenter(
    val id: String,
    val name: String,
    val address: String,
    val phone: String? = null,
    val distanceMi: Double? = null,
    val hours: String? = null,
    val openNow: Boolean? = null,
)

/** Body of createServiceRequest (POST /vehicles/{vin}/service-requests). */
@Serializable
data class ServiceRequestCreate(
    val serviceCenterId: String,
    /** yyyy-MM-dd. */
    val preferredDate: String,
    val itemIds: List<String>? = null,
    /** At most 500 characters. */
    val notes: String? = null,
)

/** 201 body of createServiceRequest. */
@Serializable
data class ServiceRequest(
    val requestId: String,
    val status: ServiceRequestStatus,
)

@Serializable
enum class ServiceRequestStatus { REQUESTED, CONFIRMED, DECLINED }
