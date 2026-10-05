package com.drivelink.demo.console

import com.drivelink.core.data.garage.GarageRepository
import com.drivelink.core.domain.DriveLinkJson
import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.command.RunCommandUseCase
import com.drivelink.core.domain.config.ApiGroup
import com.drivelink.core.domain.config.BuiltInProfiles
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.EndpointProfile
import com.drivelink.core.network.inspector.NetworkInspector
import com.drivelink.demo.FakeCommandRepository
import com.drivelink.demo.FakePinStore
import com.drivelink.demo.GarageHarness
import com.drivelink.demo.MainDispatcherRule
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Rule
import org.junit.Test

/**
 * In-memory [DemoConfig] with the rules of the real one that the console depends on: built-in
 * profiles cannot be deleted, API key values stay out of the export, an import keeps a stored
 * key, and an invalid URL is rejected. The real rules are tested in :core:settings.
 */
class FakeProfilesConfig : DemoConfig {
    private val builtIns = listOf(
        EndpointProfile(BuiltInProfiles.CLOUD_ID, "BlazeMeter cloud", "https://mock.example.test", builtIn = true),
        EndpointProfile(BuiltInProfiles.PRIVATE_ID, "Private location", "", builtIn = true),
        EndpointProfile(BuiltInProfiles.LOCAL_ID, "Local", BuiltInProfiles.LOCAL_BASE_URL, builtIn = true),
    )
    private val serializer = ListSerializer(EndpointProfile.serializer())

    override val profiles = MutableStateFlow(builtIns)
    override val activeProfile = MutableStateFlow(builtIns.first())
    override val scenario = MutableStateFlow("default")
    override val vin = MutableStateFlow<String?>(null)

    override suspend fun setActiveProfile(id: String) {
        activeProfile.value = requireNotNull(profiles.value.firstOrNull { it.id == id }) { "No profile with id \"$id\"." }
    }

    override suspend fun upsertProfile(profile: EndpointProfile) {
        require(profile.name.isNotBlank()) { "The profile name is empty." }
        require(profile.baseUrl.startsWith("http://") || profile.baseUrl.startsWith("https://") || profile.id == BuiltInProfiles.PRIVATE_ID) {
            "The URL must start with http:// or https://: ${profile.baseUrl}"
        }
        val list = profiles.value
        profiles.value = if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it } else list + profile
        activeProfile.value = profiles.value.first { it.id == activeProfile.value.id }
    }

    override suspend fun deleteProfile(id: String) {
        require(builtIns.none { it.id == id }) { "A built-in profile cannot be deleted." }
        profiles.value = profiles.value.filterNot { it.id == id }
        if (activeProfile.value.id == id) activeProfile.value = builtIns.first()
    }

    override suspend fun setScenario(name: String) { scenario.value = name.trim().ifEmpty { "default" } }
    override suspend fun setVin(vin: String?) { this.vin.value = vin?.trim()?.takeIf { it.isNotEmpty() } }
    override suspend fun applyExternal(baseUrl: String?, scenario: String?) = Unit

    override fun exportProfilesJson(): String = DriveLinkJson.encodeToString(
        serializer,
        profiles.value.filter { !it.builtIn || it.id == BuiltInProfiles.PRIVATE_ID }.map { it.copy(apiKeyValue = null) },
    )

    override suspend fun importProfilesJson(json: String) {
        val decoded = try {
            DriveLinkJson.decodeFromString(serializer, json)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw IllegalArgumentException("The file is not a profile list: ${e.message}", e)
        }
        val incoming = decoded.filter { it.id !in setOf(BuiltInProfiles.CLOUD_ID, BuiltInProfiles.LOCAL_ID) }
        incoming.forEach { p ->
            val stored = profiles.value.firstOrNull { it.id == p.id }
            upsertProfile(p.copy(builtIn = false, apiKeyValue = p.apiKeyValue ?: stored?.apiKeyValue))
        }
    }
}

class ConsoleViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val h = GarageHarness()
    private val config = FakeProfilesConfig()
    private val pins = FakePinStore("1234")
    private val inspector = NetworkInspector()
    private val garage = GarageRepository(h.vehicles, h.alerts, h.account, h.auth, config)

    /** The state flow only updates while someone collects it (WhileSubscribed), so the tests collect it. */
    private fun TestScope.viewModel() = ConsoleViewModel(
        config, h.sessions, h.auth, h.account, h.vehicles,
        RunCommandUseCase(FakeCommandRepository(), TimingLog { _, _, _ -> }),
        inspector, garage, pins,
    ).also { vm -> backgroundScope.launch(Dispatchers.Unconfined) { vm.state.collect { } } }

    private val mock = ProfileDraft(name = "My mock", baseUrl = "http://10.0.2.2:9000/")

    // Profile add / edit / delete

    @Test fun addProfile_storesItWithAnIdFromTheName_andDoesNotActivateIt() = runTest {
        val vm = viewModel()

        assertThat(vm.saveProfile(mock)).isNull()

        val stored = config.profiles.value.last()
        assertThat(stored.id).isEqualTo("my-mock")
        assertThat(stored.name).isEqualTo("My mock")
        assertThat(stored.builtIn).isFalse()
        assertThat(vm.state.value.profiles.map { it.id }).contains("my-mock")
        assertThat(config.activeProfile.value.id).isEqualTo(BuiltInProfiles.CLOUD_ID)
    }

    @Test fun addProfile_withTheSameNameTwice_getsAFreeId() = runTest {
        val vm = viewModel()
        vm.saveProfile(mock)
        vm.saveProfile(mock)

        assertThat(config.profiles.value.map { it.id }).containsAtLeast("my-mock", "my-mock-2")
    }

    @Test fun addProfile_invalid_returnsTheError_andStoresNothing() = runTest {
        val vm = viewModel()

        assertThat(vm.saveProfile(mock.copy(name = " "))).isEqualTo("Enter a name for the profile.")
        assertThat(vm.saveProfile(mock.copy(baseUrl = "ftp://host"))).contains("http")
        assertThat(vm.saveProfile(mock.copy(apiKeyValue = "secret"))).contains("header name")
        assertThat(config.profiles.value).hasSize(3)
    }

    @Test fun editProfile_changesTheFields_andKeepsTheStoredKey() = runTest {
        val vm = viewModel()
        vm.saveProfile(mock.copy(apiKeyHeader = "x-api-key", apiKeyValue = "secret-1"))
        val existing = config.profiles.value.first { it.id == "my-mock" }
        val draft = ProfileDraft.of(existing)
        assertThat(draft.apiKeyValue).isEmpty()

        assertThat(vm.saveProfile(draft.copy(name = "Renamed", authUrl = "http://auth.example.test"))).isNull()

        val stored = config.profiles.value.first { it.id == "my-mock" }
        assertThat(stored.name).isEqualTo("Renamed")
        assertThat(stored.apiKeyValue).isEqualTo("secret-1")
        assertThat(stored.overrides).containsExactly(ApiGroup.AUTH, "http://auth.example.test")
    }

    @Test fun editProfile_canReplaceOrClearTheKey() = runTest {
        val vm = viewModel()
        vm.saveProfile(mock.copy(apiKeyHeader = "x-api-key", apiKeyValue = "secret-1"))
        val draft = ProfileDraft.of(config.profiles.value.first { it.id == "my-mock" })

        vm.saveProfile(draft.copy(apiKeyValue = "secret-2"))
        assertThat(config.profiles.value.first { it.id == "my-mock" }.apiKeyValue).isEqualTo("secret-2")

        vm.saveProfile(draft.copy(clearApiKey = true))
        assertThat(config.profiles.value.first { it.id == "my-mock" }.apiKeyValue).isNull()
    }

    @Test fun deleteProfile_removesAUserProfile() = runTest {
        val vm = viewModel()
        vm.saveProfile(mock)

        assertThat(vm.deleteProfile("my-mock")).isNull()
        assertThat(config.profiles.value.map { it.id }).doesNotContain("my-mock")
    }

    @Test fun deleteProfile_refusesABuiltInProfile() = runTest {
        val vm = viewModel()

        assertThat(vm.deleteProfile(BuiltInProfiles.LOCAL_ID)).isEqualTo("A built-in profile cannot be deleted.")
        assertThat(config.profiles.value.map { it.id }).contains(BuiltInProfiles.LOCAL_ID)
    }

    @Test fun deletingTheActiveProfile_activatesTheCloudProfile() = runTest {
        val vm = viewModel()
        vm.saveProfile(mock)
        vm.selectProfile("my-mock")
        assertThat(config.activeProfile.value.id).isEqualTo("my-mock")

        vm.deleteProfile("my-mock")

        assertThat(vm.state.value.activeProfile?.id).isEqualTo(BuiltInProfiles.CLOUD_ID)
    }

    // Draft rules

    @Test fun draftId_avoidsReservedAndTakenIds() {
        assertThat(ProfileDraft.freeId("Local", emptySet())).isEqualTo("local-2")
        assertThat(ProfileDraft.freeId("External", emptySet())).isEqualTo("external-2")
        assertThat(ProfileDraft.freeId("  ", emptySet())).isEqualTo("profile")
        assertThat(ProfileDraft.freeId("Staging #1!", setOf("staging-1"))).isEqualTo("staging-1-2")
    }

    @Test fun draft_blankOverridesAreDropped() {
        val profile = mock.copy(authUrl = "http://a.example.test", vehicleUrl = "  ").toProfile(null, emptySet())

        assertThat(profile.overrides.keys).containsExactly(ApiGroup.AUTH)
    }

    // Export and import

    @Test fun export_leavesOutApiKeyValues_andReportsTheDestination() = runTest {
        val vm = viewModel()
        vm.saveProfile(mock.copy(apiKeyHeader = "x-api-key", apiKeyValue = "top-secret"))

        val json = vm.exportProfiles("the clipboard")

        assertThat(json).doesNotContain("top-secret")
        assertThat(json).contains("my-mock")
        val result = vm.state.value.result
        assertThat(result?.success).isTrue()
        assertThat(result?.action).isEqualTo(ConsoleViewModel.ACTION_EXPORT)
        assertThat(result?.message).contains("the clipboard")
        assertThat(result?.message).contains("API keys are not included")
    }

    @Test fun exportThenImport_roundTrips_andKeepsTheStoredKey() = runTest {
        val vm = viewModel()
        vm.saveProfile(mock.copy(apiKeyHeader = "x-api-key", apiKeyValue = "top-secret"))
        val json = vm.exportProfiles("the clipboard")

        vm.deleteProfile("my-mock")
        vm.importProfiles(json)

        val imported = config.profiles.value.first { it.id == "my-mock" }
        assertThat(imported.baseUrl).isEqualTo("http://10.0.2.2:9000/")
        assertThat(imported.apiKeyHeader).isEqualTo("x-api-key")
        // The export has no secret, so a profile that was deleted comes back without its key.
        assertThat(imported.apiKeyValue).isNull()
        assertThat(vm.state.value.result?.success).isTrue()
        assertThat(vm.state.value.result?.action).isEqualTo(ConsoleViewModel.ACTION_IMPORT)
    }

    @Test fun import_ofAnExistingProfile_keepsItsKey() = runTest {
        val vm = viewModel()
        vm.saveProfile(mock.copy(apiKeyHeader = "x-api-key", apiKeyValue = "top-secret"))

        vm.importProfiles(vm.exportProfiles("the clipboard"))

        assertThat(config.profiles.value.first { it.id == "my-mock" }.apiKeyValue).isEqualTo("top-secret")
    }

    @Test fun import_ofBadText_showsAnError_andChangesNothing() = runTest {
        val vm = viewModel()
        vm.importProfiles("not json")

        val result = vm.state.value.result
        assertThat(result?.success).isFalse()
        assertThat(result?.message).contains("not a profile list")
        assertThat(config.profiles.value).hasSize(3)
        assertThat(vm.state.value.busyAction).isNull()
    }

    @Test fun import_ofAnInvalidProfile_rejectsTheWholeText() = runTest {
        val vm = viewModel()
        vm.importProfiles("""[{"id":"ok","name":"Ok","baseUrl":"http://ok.example.test"},{"id":"bad","name":"Bad","baseUrl":"nope"}]""")

        assertThat(vm.state.value.result?.success).isFalse()
        assertThat(config.profiles.value.map { it.id }).doesNotContain("bad")
    }

    // VIN

    @Test fun setVin_storesTheValue_andBlankClearsIt() = runTest {
        val vm = viewModel()
        vm.setVin(" DLEV26AURA0000101 ")
        assertThat(vm.state.value.vin).isEqualTo("DLEV26AURA0000101")
        assertThat(vm.state.value.result?.message).isEqualTo("VIN set to DLEV26AURA0000101.")

        vm.setVin("")
        assertThat(vm.state.value.vin).isNull()
        assertThat(vm.state.value.result?.message).isEqualTo("VIN cleared.")
    }

    // Reset session

    @Test fun resetSession_signsOut_resetsTheScenario_andKeepsThePin() = runTest {
        config.scenario.value = "server-error"
        val vm = viewModel()

        vm.resetSession(clearPin = false)

        assertThat(h.auth.logouts).isEqualTo(1)
        assertThat(h.sessions.session.value).isNull()
        assertThat(config.scenario.value).isEqualTo("default")
        assertThat(pins.hasPin.value).isTrue()
        assertThat(vm.state.value.result?.message).contains("PIN kept")
        assertThat(vm.state.value.busyAction).isNull()
    }

    @Test fun resetSession_canClearThePin() = runTest {
        val vm = viewModel()

        vm.resetSession(clearPin = true)

        assertThat(pins.hasPin.value).isFalse()
        assertThat(vm.state.value.result?.message).contains("PIN cleared")
    }

    @Test fun resetSession_keepsProfilesAndTheVin() = runTest {
        val vm = viewModel()
        vm.saveProfile(mock)
        config.setVin("DLEV26AURA0000101")

        vm.resetSession(clearPin = false)

        assertThat(config.profiles.value.map { it.id }).contains("my-mock")
        assertThat(config.vin.value).isEqualTo("DLEV26AURA0000101")
    }
}
