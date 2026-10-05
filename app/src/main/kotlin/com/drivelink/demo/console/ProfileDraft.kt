package com.drivelink.demo.console

import androidx.compose.runtime.saveable.listSaver
import com.drivelink.core.domain.config.ApiGroup
import com.drivelink.core.domain.config.BuiltInProfiles
import com.drivelink.core.domain.config.EndpointProfile

/**
 * The values of the profile editor, as the user typed them. [id] is null for a new profile.
 *
 * The editor never shows a stored API key value. [apiKeyValue] is empty unless the user typed a
 * new one. An empty value keeps the stored key; [clearApiKey] removes it.
 */
data class ProfileDraft(
    val id: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val apiKeyHeader: String = "",
    val apiKeyValue: String = "",
    val clearApiKey: Boolean = false,
    val trustUserCerts: Boolean = false,
    val authUrl: String = "",
    val vehicleUrl: String = "",
    val alertsUrl: String = "",
) {
    /**
     * The profile to store. [existing] is the profile that the draft edits (null for a new one).
     * [takenIds] are the ids in use; a new profile gets a free id made from its name.
     *
     * @throws IllegalArgumentException for a missing name, or an API key value without a header name.
     * URLs are validated when the profile is stored.
     */
    fun toProfile(existing: EndpointProfile?, takenIds: Set<String>): EndpointProfile {
        require(name.isNotBlank()) { "Enter a name for the profile." }
        val key = when {
            clearApiKey -> null
            apiKeyValue.isNotEmpty() -> apiKeyValue
            else -> existing?.apiKeyValue
        }
        val header = apiKeyHeader.trim().ifEmpty { null }
        require(key == null || header != null) { "Enter the header name for the API key, for example x-api-key." }
        val overrides = buildMap {
            if (authUrl.isNotBlank()) put(ApiGroup.AUTH, authUrl)
            if (vehicleUrl.isNotBlank()) put(ApiGroup.VEHICLE, vehicleUrl)
            if (alertsUrl.isNotBlank()) put(ApiGroup.ALERTS, alertsUrl)
        }
        return EndpointProfile(
            id = existing?.id ?: freeId(name, takenIds),
            name = name,
            baseUrl = baseUrl,
            apiKeyHeader = header,
            apiKeyValue = key,
            trustUserCerts = trustUserCerts,
            overrides = overrides,
            builtIn = existing?.builtIn ?: false,
        )
    }

    companion object {
        /** Keeps the editor fields over a rotation. A typed API key value is not saved; the user types it again. */
        val Saver = listSaver<ProfileDraft, Any?>(
            save = {
                listOf(
                    it.id, it.name, it.baseUrl, it.apiKeyHeader, "", it.clearApiKey,
                    it.trustUserCerts, it.authUrl, it.vehicleUrl, it.alertsUrl,
                )
            },
            restore = {
                ProfileDraft(
                    id = it[0] as String?, name = it[1] as String, baseUrl = it[2] as String,
                    apiKeyHeader = it[3] as String, apiKeyValue = it[4] as String, clearApiKey = it[5] as Boolean,
                    trustUserCerts = it[6] as Boolean, authUrl = it[7] as String, vehicleUrl = it[8] as String,
                    alertsUrl = it[9] as String,
                )
            },
        )

        /** The draft for an existing profile. The API key value stays out of the draft. */
        fun of(profile: EndpointProfile) = ProfileDraft(
            id = profile.id,
            name = profile.name,
            baseUrl = profile.baseUrl,
            apiKeyHeader = profile.apiKeyHeader.orEmpty(),
            trustUserCerts = profile.trustUserCerts,
            authUrl = profile.overrides[ApiGroup.AUTH].orEmpty(),
            vehicleUrl = profile.overrides[ApiGroup.VEHICLE].orEmpty(),
            alertsUrl = profile.overrides[ApiGroup.ALERTS].orEmpty(),
        )

        /** Ids that a new profile must not use: the built-ins and the External profile of the setup activity. */
        private val RESERVED = setOf(
            BuiltInProfiles.CLOUD_ID, BuiltInProfiles.PRIVATE_ID, BuiltInProfiles.LOCAL_ID, "external",
        )

        /** A lowercase id from [name], with a number added when the id is taken: "my-mock", "my-mock-2". */
        internal fun freeId(name: String, takenIds: Set<String>): String {
            val base = name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "profile" }
            val taken = takenIds + RESERVED
            if (base !in taken) return base
            var n = 2
            while ("$base-$n" in taken) n++
            return "$base-$n"
        }
    }
}
