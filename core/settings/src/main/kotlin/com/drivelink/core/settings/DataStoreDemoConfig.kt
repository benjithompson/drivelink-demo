package com.drivelink.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.BuiltInProfiles
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DemoConfig] stored in a Preferences [DataStore].
 *
 * Initial read: the constructor reads the stored values once, blocking. The OkHttp interceptors
 * read [activeProfile] and [scenario] synchronously on each request, so the flows must hold the
 * persisted values before the first request (for example a restored session at cold start, or a
 * Perfecto run that set the profile earlier). The file is small, so the read is short.
 *
 * Writes: each write runs one atomic `edit` under a [Mutex], then publishes the new state, so the
 * flows change before the write returns and the next request uses the new values.
 *
 * Storage: profiles are one JSON array ([DriveLinkJson]) in [KEY_PROFILES]. API key values are
 * encrypted with [cipher]; a value that cannot be decrypted (key lost after a reinstall) loads
 * as null.
 *
 * Built-in profiles ([BuiltInProfiles]) always exist and cannot be deleted. The cloud base URL
 * comes from [AppBuildInfo.mockBaseUrl] unless the user stores an override; a blank override
 * restores the build value. The Local base URL is fixed. Other fields of a built-in (API key,
 * overrides, the Private location base URL) can be edited.
 */
@Singleton
class DataStoreDemoConfig(
    private val dataStore: DataStore<Preferences>,
    private val buildInfo: AppBuildInfo,
    private val cipher: SecretCipher,
    pendingExternal: PendingExternalConfig,
) : DemoConfig {

    @Inject
    constructor(dataStore: DataStore<Preferences>, buildInfo: AppBuildInfo, cipher: SecretCipher) :
        this(dataStore, buildInfo, cipher, PendingExternalConfig.Default)

    private data class State(
        val profiles: List<EndpointProfile>,
        val active: EndpointProfile,
        val scenario: String,
        val vin: String?,
    )

    private val mutex = Mutex()
    private val listSerializer = ListSerializer(EndpointProfile.serializer())

    private val initial: State = load(readBlocking())

    private val _profiles = MutableStateFlow(initial.profiles)
    private val _active = MutableStateFlow(initial.active)
    private val _scenario = MutableStateFlow(initial.scenario)
    private val _vin = MutableStateFlow(initial.vin)

    override val profiles: StateFlow<List<EndpointProfile>> = _profiles.asStateFlow()
    override val activeProfile: StateFlow<EndpointProfile> = _active.asStateFlow()
    override val scenario: StateFlow<String> = _scenario.asStateFlow()
    override val vin: StateFlow<String?> = _vin.asStateFlow()

    init {
        pendingExternal.take()?.let { applyPending(it, pendingExternal) }
    }

    /**
     * Applies instrumentation arguments ([PendingExternalConfig]). An invalid base URL is
     * recorded in [PendingExternalConfig.lastError] and skipped; the scenario still applies.
     */
    private fun applyPending(values: PendingExternalConfig.Values, holder: PendingExternalConfig) = runBlocking {
        try {
            applyExternal(values.baseUrl, values.scenario)
        } catch (e: IllegalArgumentException) {
            holder.lastError = e.message
            if (values.scenario != null) applyExternal(null, values.scenario)
        }
    }

    override suspend fun setActiveProfile(id: String) = write { prefs ->
        val profiles = load(prefs).profiles
        require(profiles.any { it.id == id }) { "No profile with id \"$id\"." }
        prefs[KEY_ACTIVE] = id
    }

    /**
     * Adds or replaces the profile with the same id. A cloud base URL equal to the build value is
     * stored blank, so the profile follows the next build.
     *
     * @throws IllegalArgumentException for an invalid profile.
     */
    override suspend fun upsertProfile(profile: EndpointProfile) {
        val normalized = ProfileRules.normalize(profile).let {
            if (it.id == BuiltInProfiles.CLOUD_ID && it.baseUrl == cloudDefaultUrl()) it.copy(baseUrl = "") else it
        }
        write { prefs -> storeProfiles(prefs, upsert(load(prefs).profiles, normalized)) }
    }

    /** @throws IllegalArgumentException for a built-in profile. An unknown id does nothing. */
    override suspend fun deleteProfile(id: String) {
        require(id !in ProfileRules.BUILT_IN_IDS) { "A built-in profile cannot be deleted." }
        write { prefs ->
            val state = load(prefs)
            storeProfiles(prefs, state.profiles.filterNot { it.id == id })
            if (state.active.id == id) prefs[KEY_ACTIVE] = BuiltInProfiles.CLOUD_ID
        }
    }

    /** A blank name sets "default". */
    override suspend fun setScenario(name: String) = write { prefs ->
        prefs[KEY_SCENARIO] = name.trim().ifEmpty { DEFAULT_SCENARIO }
    }

    override suspend fun setVin(vin: String?) = write { prefs ->
        if (vin.isNullOrBlank()) prefs.remove(KEY_VIN) else prefs[KEY_VIN] = vin.trim()
    }

    /**
     * A non-null [baseUrl] updates the profile "External" (id [ProfileRules.EXTERNAL_ID]) and
     * activates it. A non-null [scenario] sets the scenario.
     *
     * @throws IllegalArgumentException when [baseUrl] is not a valid http or https URL.
     */
    override suspend fun applyExternal(baseUrl: String?, scenario: String?) {
        val url = baseUrl?.let { ProfileRules.normalizeUrl(it) }
        write { prefs ->
            if (url != null) {
                val profiles = load(prefs).profiles
                val existing = profiles.firstOrNull { it.id == ProfileRules.EXTERNAL_ID }
                val external = existing?.copy(baseUrl = url)
                    ?: EndpointProfile(ProfileRules.EXTERNAL_ID, ProfileRules.EXTERNAL_NAME, url)
                storeProfiles(prefs, upsert(profiles, external))
                prefs[KEY_ACTIVE] = ProfileRules.EXTERNAL_ID
            }
            if (scenario != null) prefs[KEY_SCENARIO] = scenario.trim().ifEmpty { DEFAULT_SCENARIO }
        }
    }

    /**
     * JSON array of the user profiles and the Private location built-in. API key values are
     * omitted, so an export can be shared; the API key header name stays. The cloud and Local
     * built-ins are not exported: the cloud URL is per device and the Local URL is fixed.
     */
    override fun exportProfilesJson(): String {
        val exported = _profiles.value
            .filter { !it.builtIn || it.id == BuiltInProfiles.PRIVATE_ID }
            .map { it.copy(apiKeyValue = null) }
        return DriveLinkJson.encodeToString(listSerializer, exported)
    }

    /**
     * Upserts the profiles of a JSON array by id. All profiles are validated first; one invalid
     * profile rejects the whole import. Entries for the cloud and Local built-ins are ignored.
     * An entry without an API key value keeps the value already stored for that id.
     *
     * @throws IllegalArgumentException for invalid JSON or an invalid profile.
     */
    override suspend fun importProfilesJson(json: String) {
        val decoded = try {
            DriveLinkJson.decodeFromString(listSerializer, json)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("The file is not a profile list: ${e.message}", e)
        }
        val incoming = decoded
            .filter { it.id.trim() !in ProfileRules.NOT_SHARED_IDS }
            .map { ProfileRules.normalize(it.copy(builtIn = false)) }
        write { prefs ->
            var profiles = load(prefs).profiles
            for (profile in incoming) {
                val existing = profiles.firstOrNull { it.id == profile.id }
                val merged = if (profile.apiKeyValue == null && existing?.apiKeyValue != null) {
                    profile.copy(apiKeyValue = existing.apiKeyValue)
                } else {
                    profile
                }
                profiles = upsert(profiles, merged)
            }
            storeProfiles(prefs, profiles)
        }
    }

    private suspend fun write(change: (MutablePreferences) -> Unit) {
        mutex.withLock {
            val prefs = dataStore.edit { change(it) }
            publish(load(prefs))
        }
    }

    private fun publish(state: State) {
        _profiles.value = state.profiles
        _active.value = state.active
        _scenario.value = state.scenario
        _vin.value = state.vin
    }

    private fun readBlocking(): Preferences = try {
        runBlocking { dataStore.data.first() }
    } catch (e: IOException) {
        emptyPreferences()
    }

    private fun cloudDefaultUrl(): String =
        ProfileRules.defaults(buildInfo.mockBaseUrl).first { it.id == BuiltInProfiles.CLOUD_ID }.baseUrl

    private fun upsert(profiles: List<EndpointProfile>, profile: EndpointProfile): List<EndpointProfile> {
        val index = profiles.indexOfFirst { it.id == profile.id }
        return if (index >= 0) profiles.toMutableList().also { it[index] = profile } else profiles + profile
    }

    private fun load(prefs: Preferences): State {
        val stored = decodeStored(prefs[KEY_PROFILES])
        val builtIns = ProfileRules.defaults(buildInfo.mockBaseUrl).map { default ->
            stored.firstOrNull { it.id == default.id }?.let { ProfileRules.enforceBuiltIn(it, default) } ?: default
        }
        val users = stored.filter { it.id !in ProfileRules.BUILT_IN_IDS }.map { it.copy(builtIn = false) }
        val profiles = builtIns + users
        val activeId = prefs[KEY_ACTIVE]
        return State(
            profiles = profiles,
            active = profiles.firstOrNull { it.id == activeId } ?: profiles.first(),
            scenario = prefs[KEY_SCENARIO] ?: DEFAULT_SCENARIO,
            vin = prefs[KEY_VIN],
        )
    }

    private fun storeProfiles(prefs: MutablePreferences, profiles: List<EndpointProfile>) {
        val encrypted = profiles.map { it.copy(apiKeyValue = it.apiKeyValue?.let(cipher::encrypt)) }
        prefs[KEY_PROFILES] = DriveLinkJson.encodeToString(listSerializer, encrypted)
    }

    private fun decodeStored(json: String?): List<EndpointProfile> {
        if (json.isNullOrBlank()) return emptyList()
        val list = try {
            DriveLinkJson.decodeFromString(listSerializer, json)
        } catch (e: SerializationException) {
            return emptyList()
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }
        return list.map { profile ->
            profile.copy(apiKeyValue = profile.apiKeyValue?.let { runCatching { cipher.decrypt(it) }.getOrNull() })
        }
    }

    companion object {
        const val DEFAULT_SCENARIO = "default"
        internal val KEY_PROFILES = stringPreferencesKey("profiles_json")
        internal val KEY_ACTIVE = stringPreferencesKey("active_profile_id")
        internal val KEY_SCENARIO = stringPreferencesKey("scenario")
        internal val KEY_VIN = stringPreferencesKey("vin")
    }
}
