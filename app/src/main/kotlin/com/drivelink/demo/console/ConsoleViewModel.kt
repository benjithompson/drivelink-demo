package com.drivelink.demo.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.command.CommandProgress
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.DemoScenarios
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.domain.config.PinStore
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.repository.AccountRepository
import com.drivelink.core.domain.repository.AuthRepository
import com.drivelink.core.domain.repository.VehicleRepository
import com.drivelink.core.network.inspector.InspectorEntry
import com.drivelink.core.network.inspector.NetworkInspector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import kotlin.time.TimeSource

/** The result of the last console action. [correlationId] is the error's id, or the id of the last recorded call. */
data class ConsoleResult(
    val action: String,
    val success: Boolean,
    val message: String,
    val correlationId: String?,
)

data class ConsoleUiState(
    val profiles: List<EndpointProfile> = emptyList(),
    val activeProfile: EndpointProfile? = null,
    val scenario: String = DemoScenarios.DEFAULT,
    val scenarios: List<String> = DemoScenarios.ALL,
    val vin: String? = null,
    val signedInAs: String? = null,
    /** The action that runs now; the action buttons are disabled while it runs. */
    val busyAction: String? = null,
    val result: ConsoleResult? = null,
    /** Text of the last command progress state, for example "Waiting for vehicle… (poll 2)". */
    val commandProgress: String? = null,
    val entries: List<InspectorEntry> = emptyList(),
)

/**
 * Demo console: endpoint profile and scenario selection, profile add / edit / delete, profile
 * import and export, a VIN override, Reset session, a few live calls, and the Network Inspector.
 *
 * Secrets: the editor never shows a stored API key value, [exportProfiles] leaves API key values
 * out, and [DemoConfig] stores them encrypted.
 */
@HiltViewModel
class ConsoleViewModel @Inject constructor(
    private val demoConfig: DemoConfig,
    sessionStore: SessionStore,
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val vehicles: VehicleRepository,
    private val runCommand: RunCommandUseCase,
    private val inspector: NetworkInspector,
    private val garage: GarageRepository,
    private val pins: PinStore,
) : ViewModel() {

    private data class Local(val busy: String? = null, val result: ConsoleResult? = null, val progress: String? = null)

    private val local = MutableStateFlow(Local())

    private val config = combine(demoConfig.profiles, demoConfig.activeProfile, demoConfig.scenario, demoConfig.vin) { p, a, s, v ->
        ConsoleUiState(profiles = p, activeProfile = a, scenario = s, vin = v)
    }

    val state: StateFlow<ConsoleUiState> = combine(config, sessionStore.session, inspector.entries, local) { cfg, session, entries, l ->
        cfg.copy(
            signedInAs = session?.email,
            busyAction = l.busy,
            result = l.result,
            commandProgress = l.progress,
            entries = entries,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConsoleUiState())

    fun selectProfile(id: String) = viewModelScope.launch {
        try {
            demoConfig.setActiveProfile(id)
        } catch (e: IllegalArgumentException) {
            setResult(ConsoleResult("Profile", false, e.message ?: "Invalid profile.", null))
        }
    }

    fun selectScenario(name: String) = viewModelScope.launch { demoConfig.setScenario(name) }

    /**
     * Adds or replaces a profile from the editor [draft]. Returns an error text for the editor,
     * or null when the profile is stored. A new profile is not activated.
     */
    suspend fun saveProfile(draft: ProfileDraft): String? {
        val profiles = demoConfig.profiles.value
        return try {
            val existing = draft.id?.let { id -> profiles.firstOrNull { it.id == id } }
            demoConfig.upsertProfile(draft.toProfile(existing, profiles.map { it.id }.toSet()))
            null
        } catch (e: IllegalArgumentException) {
            e.message ?: "The profile is not valid."
        }
    }

    /** Deletes a user profile. Returns an error text, or null on success. A built-in profile cannot be deleted. */
    suspend fun deleteProfile(id: String): String? = try {
        demoConfig.deleteProfile(id)
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "The profile cannot be deleted."
    }

    /**
     * The profiles as JSON, without API key values. [destination] is for the result card, for
     * example "the clipboard".
     */
    fun exportProfiles(destination: String): String {
        val json = demoConfig.exportProfilesJson()
        val count = (runCatching { DriveLinkJson.parseToJsonElement(json) }.getOrNull() as? JsonArray)?.size ?: 0
        setResult(ok(ACTION_EXPORT, "Exported $count profiles to $destination. API keys are not included."))
        return json
    }

    /** Imports profiles from pasted JSON. One invalid profile rejects the whole text. */
    fun importProfiles(json: String) = run(ACTION_IMPORT) {
        try {
            demoConfig.importProfilesJson(json)
            val count = (runCatching { DriveLinkJson.parseToJsonElement(json) }.getOrNull() as? JsonArray)?.size ?: 0
            ok(ACTION_IMPORT, "Imported $count profiles. Entries for the built-in cloud and Local profiles are skipped.")
        } catch (e: IllegalArgumentException) {
            ConsoleResult(ACTION_IMPORT, false, e.message ?: "The text is not a profile list.", null)
        }
    }

    /**
     * Sets the VIN that the console calls use. A blank [vin] clears it. The app replaces the VIN
     * with a vehicle of the account when it loads the vehicle list.
     */
    fun setVin(vin: String) = viewModelScope.launch {
        demoConfig.setVin(vin)
        val stored = demoConfig.vin.value
        setResult(ok(ACTION_VIN, if (stored == null) "VIN cleared." else "VIN set to $stored."))
    }

    /**
     * Signs out, clears the scenario (back to `default`) and, when [clearPin] is true, the PIN.
     * The app then opens Login. Profiles and the VIN stay.
     */
    fun resetSession(clearPin: Boolean) = run(ACTION_RESET) {
        garage.signOut()
        demoConfig.setScenario(DemoScenarios.DEFAULT)
        if (clearPin) pins.clear()
        ok(ACTION_RESET, "Signed out. Scenario is default. " + if (clearPin) "PIN cleared." else "PIN kept.")
    }

    fun clearInspector() = inspector.clear()

    fun toCurl(entry: InspectorEntry): String = inspector.toCurl(entry)

    fun signIn() = run(ACTION_SIGN_IN) {
        auth.login(DEMO_EMAIL, DEMO_PASSWORD).describe(ACTION_SIGN_IN) { "Signed in as $DEMO_EMAIL" }
    }

    fun testConnection() = run(ACTION_HEALTH) {
        val start = TimeSource.Monotonic.markNow()
        val result = account.getHealth()
        val ms = start.elapsedNow().inWholeMilliseconds
        result.describe(ACTION_HEALTH) { "${it.status} · version ${it.version} · $ms ms" }
    }

    fun loadVehicles() = run(ACTION_VEHICLES) {
        val result = vehicles.listVehicles()
        val first = (result as? Outcome.Ok)?.value?.firstOrNull()
        if (first != null) demoConfig.setVin(first.vin)
        result.describe(ACTION_VEHICLES) { list ->
            if (first == null) "No vehicles." else "${list.size} vehicles · selected ${first.nickname} (${first.vin})"
        }
    }

    fun loadStatus() = run(ACTION_STATUS) {
        val vin = demoConfig.vin.value ?: return@run noVin(ACTION_STATUS)
        vehicles.getStatus(vin).describe(ACTION_STATUS) { s ->
            val energy = s.batteryPct?.let { "battery $it%" } ?: s.fuelPct?.let { "fuel $it%" } ?: "energy n/a"
            "${if (s.locked) "Locked" else "Unlocked"} · $energy · range ${s.rangeMi} mi · ${s.connectivity}"
        }
    }

    fun lock() = run(ACTION_LOCK) {
        val vin = demoConfig.vin.value ?: return@run noVin(ACTION_LOCK)
        val start = TimeSource.Monotonic.markNow()
        var terminal: CommandProgress = CommandProgress.Sending
        runCommand(vin, CommandRequest(CommandType.LOCK, DEMO_PIN)).collect { progress ->
            terminal = progress
            local.update { it.copy(progress = progress.label()) }
        }
        val seconds = String.format(Locale.US, "%.1f s", start.elapsedNow().inWholeMilliseconds / 1000.0)
        when (val t = terminal) {
            is CommandProgress.Succeeded -> ok(ACTION_LOCK, "Lock succeeded in $seconds")
            is CommandProgress.Failed -> ConsoleResult(ACTION_LOCK, false, "Lock failed: ${t.reason ?: "no reason"} ($seconds)", lastCorrelationId())
            is CommandProgress.TimedOut -> ConsoleResult(ACTION_LOCK, false, "The vehicle did not respond in ${RunCommandUseCase.HARD_STOP.inWholeSeconds} s.", lastCorrelationId())
            is CommandProgress.Error -> failure(ACTION_LOCK, t.error)
            else -> ConsoleResult(ACTION_LOCK, false, "Stopped.", lastCorrelationId())
        }
    }

    private fun run(action: String, block: suspend () -> ConsoleResult) {
        if (local.value.busy != null) return
        local.update { it.copy(busy = action, progress = if (action == ACTION_LOCK) null else it.progress) }
        viewModelScope.launch {
            val result = try {
                block()
            } finally {
                local.update { it.copy(busy = null) }
            }
            setResult(result)
        }
    }

    private fun setResult(result: ConsoleResult) = local.update { it.copy(result = result) }

    private fun <T> Outcome<T>.describe(action: String, text: (T) -> String): ConsoleResult = when (this) {
        is Outcome.Ok -> ok(action, text(value))
        is Outcome.Err -> failure(action, error)
    }

    private fun ok(action: String, message: String) = ConsoleResult(action, true, message, lastCorrelationId())

    private fun failure(action: String, error: AppError) = ConsoleResult(
        action,
        false,
        "${error.javaClass.simpleName}: ${error.message}",
        error.correlationId,
    )

    private fun noVin(action: String) = ConsoleResult(action, false, "No vehicle selected. Tap \"Load vehicles\" first.", null)

    private fun lastCorrelationId(): String? = inspector.entries.value.firstOrNull()?.correlationId

    companion object {
        const val ACTION_SIGN_IN = "Sign in"
        const val ACTION_HEALTH = "Test connection"
        const val ACTION_VEHICLES = "Load vehicles"
        const val ACTION_STATUS = "Load status"
        const val ACTION_LOCK = "Lock"
        const val ACTION_EXPORT = "Export profiles"
        const val ACTION_IMPORT = "Import profiles"
        const val ACTION_VIN = "Set VIN"
        const val ACTION_RESET = "Reset session"

        /** The createToken request example in api/openapi.yaml. */
        const val DEMO_EMAIL = "alex.rivera@drivelink.test"
        const val DEMO_PASSWORD = "demo-password"

        /** The sendCommand request example in api/openapi.yaml. */
        const val DEMO_PIN = "1234"

        /** Host and port of a base URL, or null when it is empty or invalid. */
        fun hostOf(baseUrl: String): String? = try {
            URI(baseUrl).let { uri -> uri.host?.let { if (uri.port > 0) "$it:${uri.port}" else it } }
        } catch (e: Exception) {
            null
        }
    }
}

/** Console text for a progress state. */
fun CommandProgress.label(): String = when (this) {
    is CommandProgress.Sending -> "Sending command…"
    is CommandProgress.Waiting -> {
        val base = if (waking) "Waking vehicle…" else "Waiting for vehicle…"
        if (attempt == 0) "$base (accepted)" else "$base (poll $attempt)"
    }
    is CommandProgress.Succeeded -> "Done · ${command.status}"
    is CommandProgress.Failed -> "Command failed · ${reason ?: "no reason"}"
    is CommandProgress.TimedOut -> "Vehicle did not respond"
    is CommandProgress.Error -> "Error · ${error.message}"
}
