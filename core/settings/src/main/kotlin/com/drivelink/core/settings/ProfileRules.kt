package com.drivelink.core.settings

import com.drivelink.core.domain.config.BuiltInProfiles
import com.drivelink.core.domain.config.EndpointProfile
import java.net.URI
import java.net.URISyntaxException

/** Profile rules shared by [DataStoreDemoConfig]: built-ins, validation and normalization. */
internal object ProfileRules {

    const val EXTERNAL_ID = "external"
    const val EXTERNAL_NAME = "External"
    const val CLOUD_NAME = "BlazeMeter cloud"
    const val PRIVATE_NAME = "Private location"
    const val LOCAL_NAME = "Local"

    val BUILT_IN_IDS = listOf(BuiltInProfiles.CLOUD_ID, BuiltInProfiles.PRIVATE_ID, BuiltInProfiles.LOCAL_ID)

    /** Built-ins whose base URL is fixed: local. */
    val FIXED_IDS = setOf(BuiltInProfiles.LOCAL_ID)

    /** Built-ins that import and export skip: the cloud URL is per device, the Local URL is fixed. */
    val NOT_SHARED_IDS = setOf(BuiltInProfiles.CLOUD_ID, BuiltInProfiles.LOCAL_ID)

    /** Built-ins that accept a blank base URL: Private location ("not configured") and cloud ("build default"). */
    private val BLANK_URL_IDS = setOf(BuiltInProfiles.PRIVATE_ID, BuiltInProfiles.CLOUD_ID)

    fun defaults(mockBaseUrl: String): List<EndpointProfile> = listOf(
        EndpointProfile(BuiltInProfiles.CLOUD_ID, CLOUD_NAME, mockBaseUrl.trim().trimEnd('/'), builtIn = true),
        EndpointProfile(BuiltInProfiles.PRIVATE_ID, PRIVATE_NAME, "", builtIn = true),
        EndpointProfile(BuiltInProfiles.LOCAL_ID, LOCAL_NAME, BuiltInProfiles.LOCAL_BASE_URL, builtIn = true),
    )

    /**
     * Applies the fixed fields of a built-in to a stored or edited copy: the id, name and builtIn
     * flag always; the base URL for local. A blank cloud base URL is the build default.
     */
    fun enforceBuiltIn(edited: EndpointProfile, default: EndpointProfile): EndpointProfile = edited.copy(
        id = default.id,
        name = default.name,
        builtIn = true,
        baseUrl = when {
            default.id in FIXED_IDS -> default.baseUrl
            default.id == BuiltInProfiles.CLOUD_ID && edited.baseUrl.isBlank() -> default.baseUrl
            else -> edited.baseUrl
        },
    )

    /**
     * Trims and validates a user profile. A blank base URL is allowed only for the Private
     * location built-in ("not configured") and the cloud built-in ("build default").
     *
     * @throws IllegalArgumentException for a blank id or name, or an invalid URL.
     */
    fun normalize(profile: EndpointProfile): EndpointProfile {
        val id = profile.id.trim()
        val name = profile.name.trim()
        require(id.isNotEmpty()) { "The profile id is empty." }
        require(name.isNotEmpty()) { "The profile name is empty." }
        val baseUrl = if (id in BLANK_URL_IDS && profile.baseUrl.isBlank()) "" else normalizeUrl(profile.baseUrl)
        val overrides = profile.overrides
            .filterValues { it.isNotBlank() }
            .mapValues { (_, url) -> normalizeUrl(url) }
        return profile.copy(
            id = id,
            name = name,
            baseUrl = baseUrl,
            apiKeyHeader = profile.apiKeyHeader?.trim()?.takeIf { it.isNotEmpty() },
            apiKeyValue = profile.apiKeyValue?.takeIf { it.isNotEmpty() },
            overrides = overrides,
        )
    }

    /**
     * Trims the URL and removes a trailing slash.
     *
     * @throws IllegalArgumentException when the URL is not http or https with a host, or has a query or fragment.
     */
    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        val uri = try {
            URI(trimmed)
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("Not a valid URL: $raw", e)
        }
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") { "The URL must start with http:// or https://: $raw" }
        require(!uri.host.isNullOrBlank()) { "The URL has no host: $raw" }
        require(uri.rawQuery == null && uri.rawFragment == null) { "The URL must not have a query or fragment: $raw" }
        return trimmed
    }
}
