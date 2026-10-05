package com.drivelink.demo

import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.Outcome
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.domain.config.PinStore
import com.drivelink.core.domain.config.Session
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.domain.error.AppError
import com.drivelink.core.domain.model.Alert
import com.drivelink.core.domain.model.ChargeSettings
import com.drivelink.core.domain.model.ClimatePresets
import com.drivelink.core.domain.model.Command
import com.drivelink.core.domain.model.CommandReason
import com.drivelink.core.domain.model.CommandRequest
import com.drivelink.core.domain.model.CommandStatus
import com.drivelink.core.domain.model.CommandType
import com.drivelink.core.domain.model.Health
import com.drivelink.core.domain.model.Location
import com.drivelink.core.domain.model.Maintenance
import com.drivelink.core.domain.model.ServiceRequest
import com.drivelink.core.domain.model.ServiceRequestCreate
import com.drivelink.core.domain.model.TokenResponse
import com.drivelink.core.domain.model.Trip
import com.drivelink.core.domain.model.User
import com.drivelink.core.domain.model.Vehicle
import com.drivelink.core.domain.model.VehicleStatus
import com.drivelink.core.domain.repository.AccountRepository
import com.drivelink.core.domain.repository.AlertsRepository
import com.drivelink.core.domain.repository.AuthRepository
import com.drivelink.core.domain.repository.CommandRepository
import com.drivelink.core.domain.repository.VehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File

const val AURORA = "DLEV26AURA0000101"
const val SOLACE = "DLGS25SLACE000202"

/** Contract examples from api/examples. Gradle sets the system property drivelink.repoRoot. */
object Examples {
    private val root: File by lazy {
        File(requireNotNull(System.getProperty("drivelink.repoRoot")) { "Run the tests through Gradle." })
    }

    fun text(path: String): String = root.resolve("api/examples/$path").readText()

    fun <T> decode(path: String, serializer: KSerializer<T>): T = DriveLinkJson.decodeFromString(serializer, text(path))

    fun status(name: String): VehicleStatus = decode("getVehicleStatus/$name.json", VehicleStatus.serializer())

    val vehicles: List<Vehicle> get() = decode("listVehicles/200.default.json", ListSerializer(Vehicle.serializer()))
    val user: User get() = decode("getMe/200.default.json", User.serializer())
    val alerts: List<Alert> get() = decode("listAlerts/200.default.json", ListSerializer(Alert.serializer()))
    val presets: ClimatePresets get() = decode("getClimatePresets/200.default.json", ClimatePresets.serializer())
}

/** Runs the ViewModel scope (Dispatchers.Main) on a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(UnconfinedTestDispatcher())
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeDemoConfig : DemoConfig {
    override val profiles = MutableStateFlow(listOf(EndpointProfile("test", "Test", "http://localhost")))
    override val activeProfile = MutableStateFlow(profiles.value.first())
    override val scenario = MutableStateFlow("default")
    override val vin = MutableStateFlow<String?>(null)
    override suspend fun setActiveProfile(id: String) = Unit
    override suspend fun upsertProfile(profile: EndpointProfile) = Unit
    override suspend fun deleteProfile(id: String) = Unit
    override suspend fun setScenario(name: String) { scenario.value = name }
    override suspend fun setVin(vin: String?) { this.vin.value = vin }
    override suspend fun applyExternal(baseUrl: String?, scenario: String?) = Unit
    override fun exportProfilesJson(): String = "[]"
    override suspend fun importProfilesJson(json: String) = Unit
}

class FakeSessionStore(initial: Session? = Session("at", "rt", "alex.rivera@drivelink.test")) : SessionStore {
    private val state = MutableStateFlow(initial)
    override val session: StateFlow<Session?> = state
    override suspend fun save(session: Session) { state.value = session }
    override suspend fun clear() { state.value = null }
}

class FakePinStore(pin: String? = null) : PinStore {
    private var stored: String? = pin
    private val has = MutableStateFlow(pin != null)
    override val hasPin: StateFlow<Boolean> = has
    override suspend fun set(pin: String) { stored = pin; has.value = true }
    override fun verify(pin: String): Boolean = stored == pin
    override suspend fun clear() { stored = null; has.value = false }
}

/** Auth fake. [loginResult] is the answer to login; a success also saves a session. */
class FakeAuthRepository(private val sessions: FakeSessionStore) : AuthRepository {
    var loginResult: Outcome<TokenResponse> = Outcome.Ok(TokenResponse("at-1", 900, "rt-1"))
    var logins = 0
    var logouts = 0
    override suspend fun login(email: String, password: String): Outcome<TokenResponse> {
        logins++
        val result = loginResult
        if (result is Outcome.Ok) sessions.save(Session(result.value.accessToken, result.value.refreshToken, email))
        return result
    }
    override suspend fun logout(): Outcome<Unit> { logouts++; sessions.clear(); return Outcome.Ok(Unit) }
}

class FakeAccountRepository(var user: Outcome<User> = Outcome.Ok(Examples.user)) : AccountRepository {
    override suspend fun getHealth(): Outcome<Health> = error("not used")
    override suspend fun getMe(): Outcome<User> = user
}

class FakeAlertsRepository(var alerts: Outcome<List<Alert>> = Outcome.Ok(Examples.alerts)) : AlertsRepository {
    override suspend fun list(vin: String?): Outcome<List<Alert>> = alerts
    override suspend fun markRead(alertId: String): Outcome<Unit> = Outcome.Ok(Unit)
}

/** Vehicle fake. [status] maps a VIN to its answer; the other calls return fixed examples. */
class FakeVehicleRepository : VehicleRepository {
    var vehicles: Outcome<List<Vehicle>> = Outcome.Ok(Examples.vehicles)
    val status = mutableMapOf<String, Outcome<VehicleStatus>>(
        AURORA to Outcome.Ok(Examples.status("200.default")),
        SOLACE to Outcome.Ok(Examples.status("200.default-ice")),
    )
    var presets: Outcome<ClimatePresets> = Outcome.Ok(Examples.presets)
    var statusCalls = 0

    override suspend fun listVehicles() = vehicles
    override suspend fun getStatus(vin: String): Outcome<VehicleStatus> {
        statusCalls++
        return status.getValue(vin)
    }
    override suspend fun getLocation(vin: String): Outcome<Location> =
        Outcome.Ok(Location(1.0, 2.0, 5, "1 Harbor Way", "Harbor District", "2026-10-02T16:58:00Z"))
    override suspend fun getChargeSettings(vin: String): Outcome<ChargeSettings> = error("not used")
    override suspend fun updateChargeSettings(vin: String, settings: ChargeSettings): Outcome<ChargeSettings> = error("not used")
    override suspend fun getClimatePresets(vin: String) = presets
    override suspend fun updateClimatePresets(vin: String, presets: ClimatePresets): Outcome<ClimatePresets> = error("not used")
    override suspend fun listTrips(vin: String, limit: Int): Outcome<List<Trip>> = Outcome.Ok(
        listOf(Trip("t1", "2026-10-02T10:00:00Z", "2026-10-02T10:30:00Z", 12.4, 30)),
    )
    override suspend fun getMaintenance(vin: String): Outcome<Maintenance> = error("not used")
    override suspend fun createServiceRequest(vin: String, request: ServiceRequestCreate): Outcome<ServiceRequest> = error("not used")
}

/** The repositories and the shared state, wired like the app wires them. */
class GarageHarness {
    val config = FakeDemoConfig()
    val sessions = FakeSessionStore()
    val auth = FakeAuthRepository(sessions)
    val vehicles = FakeVehicleRepository()
    val alerts = FakeAlertsRepository()
    val account = FakeAccountRepository()
    val garage = GarageRepository(vehicles, alerts, account, auth, config)
}

/** Command fake: [sendResult] answers send; [polls] answers get by attempt (the last one repeats). */
class FakeCommandRepository : CommandRepository {
    var sendResult: Outcome<Command> = Outcome.Ok(command(CommandStatus.QUEUED))
    var polls: List<Outcome<Command>> = listOf(Outcome.Ok(command(CommandStatus.SUCCEEDED)))
    val requests = mutableListOf<CommandRequest>()

    override suspend fun send(vin: String, request: CommandRequest): Outcome<Command> {
        requests += request
        return sendResult
    }

    override suspend fun get(commandId: String, attempt: Int): Outcome<Command> =
        polls[(attempt - 1).coerceIn(0, polls.lastIndex)]

    companion object {
        fun command(status: CommandStatus, reason: CommandReason? = null, type: CommandType = CommandType.LOCK) =
            Command("cmd-1", type, status, reason, "2026-10-03T10:00:00Z", "2026-10-03T10:00:01Z")

        fun networkError() = Outcome.Err(AppError.Network(timeout = false, correlationId = "cid-1"))
    }
}
