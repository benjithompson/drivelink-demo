package com.drivelink.core.settings

import androidx.datastore.preferences.core.edit
import com.drivelink.core.domain.config.ApiGroup
import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.BuiltInProfiles
import com.drivelink.core.domain.config.EndpointProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreDemoConfigTest {
    @get:Rule val tmp = TemporaryFolder()

    private val buildInfo = AppBuildInfo(mockBaseUrl = "https://vs1.mock.example.com/", clientVersion = "0.1.0 (1)", debug = true)
    private val cipher = FakeSecretCipher()
    private lateinit var file: File
    private lateinit var store: TestStore
    private lateinit var config: DataStoreDemoConfig

    @Before fun setUp() {
        file = File(tmp.root, "settings.preferences_pb")
        open()
    }

    @After fun tearDown() = store.close()

    private fun open() {
        store = TestStore(file)
        config = DataStoreDemoConfig(store.dataStore, buildInfo, cipher)
    }

    private fun reopen() {
        store.close()
        open()
    }

    private val user = EndpointProfile("u1", "Team mock", "https://team.example.com:8443/drivelink/", apiKeyHeader = "X-Api-Key", apiKeyValue = "s3cret")

    @Test fun defaults() {
        val profiles = config.profiles.value
        assertThat(profiles.map { it.id }).containsExactly(BuiltInProfiles.CLOUD_ID, BuiltInProfiles.PRIVATE_ID, BuiltInProfiles.LOCAL_ID).inOrder()
        assertThat(profiles.all { it.builtIn }).isTrue()
        assertThat(profiles[0].baseUrl).isEqualTo("https://vs1.mock.example.com")
        assertThat(profiles[1].baseUrl).isEmpty()
        assertThat(profiles[2].baseUrl).isEqualTo(BuiltInProfiles.LOCAL_BASE_URL)
        assertThat(config.activeProfile.value.id).isEqualTo(BuiltInProfiles.CLOUD_ID)
        assertThat(config.scenario.value).isEqualTo("default")
        assertThat(config.vin.value).isNull()
    }

    @Test fun upsert_normalizes_andPersistsAcrossInstances() = runBlocking<Unit> {
        config.upsertProfile(user)
        config.setActiveProfile("u1")
        config.setScenario("slow-vehicle")
        config.setVin("DLEV26AURA0000101")
        assertThat(config.activeProfile.value.baseUrl).isEqualTo("https://team.example.com:8443/drivelink")

        reopen()

        assertThat(config.activeProfile.value).isEqualTo(user.copy(baseUrl = "https://team.example.com:8443/drivelink"))
        assertThat(config.scenario.value).isEqualTo("slow-vehicle")
        assertThat(config.vin.value).isEqualTo("DLEV26AURA0000101")
        assertThat(config.profiles.value.map { it.id }.last()).isEqualTo("u1")
    }

    @Test fun apiKeyValue_isEncryptedAtRest_andDecryptedOnLoad() = runBlocking<Unit> {
        config.upsertProfile(user)
        val raw = store.dataStore.data.first()[DataStoreDemoConfig.KEY_PROFILES]!!
        assertThat(raw).doesNotContain("s3cret")
        assertThat(raw).contains(cipher.encrypt("s3cret"))
        reopen()
        assertThat(config.profiles.value.single { it.id == "u1" }.apiKeyValue).isEqualTo("s3cret")
    }

    @Test fun undecryptableApiKey_loadsAsNull() = runBlocking<Unit> {
        config.upsertProfile(user)
        store.dataStore.edit {
            it[DataStoreDemoConfig.KEY_PROFILES] = it[DataStoreDemoConfig.KEY_PROFILES]!!.replace(cipher.encrypt("s3cret"), "garbage")
        }
        reopen()
        assertThat(config.profiles.value.single { it.id == "u1" }.apiKeyValue).isNull()
    }

    @Test fun activeSwitch_unknownId_throws() = runBlocking<Unit> {
        config.setActiveProfile(BuiltInProfiles.LOCAL_ID)
        assertThat(config.activeProfile.value.baseUrl).isEqualTo(BuiltInProfiles.LOCAL_BASE_URL)
        val error = runCatching { config.setActiveProfile("nope") }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(config.activeProfile.value.id).isEqualTo(BuiltInProfiles.LOCAL_ID)
    }

    @Test fun builtIns_cannotBeDeleted() = runBlocking<Unit> {
        for (id in listOf(BuiltInProfiles.CLOUD_ID, BuiltInProfiles.PRIVATE_ID, BuiltInProfiles.LOCAL_ID)) {
            assertThat(runCatching { config.deleteProfile(id) }.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThat(config.profiles.value).hasSize(3)
    }

    @Test fun deletingActiveProfile_activatesCloud() = runBlocking<Unit> {
        config.upsertProfile(user)
        config.setActiveProfile("u1")
        config.deleteProfile("u1")
        assertThat(config.activeProfile.value.id).isEqualTo(BuiltInProfiles.CLOUD_ID)
        assertThat(config.profiles.value.map { it.id }).doesNotContain("u1")
        config.deleteProfile("unknown")
    }

    @Test fun builtInEdits_keepFixedFields() = runBlocking<Unit> {
        config.upsertProfile(EndpointProfile(BuiltInProfiles.CLOUD_ID, "Renamed", "https://vs2.mock.example.com/", apiKeyHeader = "X-Key", apiKeyValue = "k"))
        config.upsertProfile(EndpointProfile(BuiltInProfiles.LOCAL_ID, "Renamed", "http://other:9000"))
        config.upsertProfile(EndpointProfile(BuiltInProfiles.PRIVATE_ID, "Renamed", "https://pl.example.com:9443"))
        reopen()
        val byId = config.profiles.value.associateBy { it.id }
        assertThat(byId.getValue(BuiltInProfiles.CLOUD_ID).baseUrl).isEqualTo("https://vs2.mock.example.com")
        assertThat(byId.getValue(BuiltInProfiles.CLOUD_ID).name).isEqualTo("BlazeMeter cloud")
        assertThat(byId.getValue(BuiltInProfiles.CLOUD_ID).apiKeyValue).isEqualTo("k")
        assertThat(byId.getValue(BuiltInProfiles.LOCAL_ID).baseUrl).isEqualTo(BuiltInProfiles.LOCAL_BASE_URL)
        assertThat(byId.getValue(BuiltInProfiles.PRIVATE_ID).baseUrl).isEqualTo("https://pl.example.com:9443")
        assertThat(byId.getValue(BuiltInProfiles.PRIVATE_ID).name).isEqualTo("Private location")
        assertThat(byId.values.filter { it.builtIn }).hasSize(3)
    }

    @Test fun cloudOverride_blankOrBuildValue_followsTheBuild() = runBlocking<Unit> {
        config.upsertProfile(EndpointProfile(BuiltInProfiles.CLOUD_ID, "x", "https://vs2.mock.example.com"))
        assertThat(config.profiles.value.first().baseUrl).isEqualTo("https://vs2.mock.example.com")

        config.upsertProfile(EndpointProfile(BuiltInProfiles.CLOUD_ID, "x", " "))
        assertThat(config.profiles.value.first().baseUrl).isEqualTo("https://vs1.mock.example.com")

        // The build value is stored blank: a later build with another URL wins.
        config.upsertProfile(EndpointProfile(BuiltInProfiles.CLOUD_ID, "x", "https://vs1.mock.example.com/"))
        val stored = store.dataStore.data.first()[DataStoreDemoConfig.KEY_PROFILES].orEmpty()
        assertThat(stored).doesNotContain("vs1.mock.example.com")
    }

    @Test fun invalidUrls_areRejected() = runBlocking<Unit> {
        for (url in listOf("", "  ", "ftp://host", "not a url", "https://", "https://h/x?y=1", "mock.example.com")) {
            val error = runCatching { config.upsertProfile(user.copy(baseUrl = url)) }.exceptionOrNull()
            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        }
        val badOverride = runCatching { config.upsertProfile(user.copy(overrides = mapOf(ApiGroup.AUTH to "ftp://x"))) }.exceptionOrNull()
        assertThat(badOverride).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(config.profiles.value).hasSize(3)
    }

    @Test fun overrides_blankRemoved_validTrimmed() = runBlocking<Unit> {
        config.upsertProfile(user.copy(overrides = mapOf(ApiGroup.AUTH to " https://auth.example.com/ ", ApiGroup.ALERTS to " ")))
        assertThat(config.profiles.value.single { it.id == "u1" }.overrides).containsExactly(ApiGroup.AUTH, "https://auth.example.com")
    }

    @Test fun applyExternal_createsAndActivatesExternal_andSetsScenario() = runBlocking<Unit> {
        config.applyExternal(" http://192.168.1.20:8080/ ", "rate-limited")
        val active = config.activeProfile.value
        assertThat(active.id).isEqualTo("external")
        assertThat(active.name).isEqualTo("External")
        assertThat(active.baseUrl).isEqualTo("http://192.168.1.20:8080")
        assertThat(active.builtIn).isFalse()
        assertThat(config.scenario.value).isEqualTo("rate-limited")

        config.applyExternal("https://second.example.com", null)
        assertThat(config.profiles.value.count { it.id == "external" }).isEqualTo(1)
        assertThat(config.activeProfile.value.baseUrl).isEqualTo("https://second.example.com")
        assertThat(config.scenario.value).isEqualTo("rate-limited")

        config.applyExternal(null, "default")
        assertThat(config.scenario.value).isEqualTo("default")
        assertThat(config.activeProfile.value.id).isEqualTo("external")
    }

    @Test fun applyExternal_invalidUrl_throws_andChangesNothing() = runBlocking<Unit> {
        val error = runCatching { config.applyExternal("ftp://x", "fast") }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(config.scenario.value).isEqualTo("default")
        assertThat(config.activeProfile.value.id).isEqualTo(BuiltInProfiles.CLOUD_ID)
    }

    @Test fun export_containsUserProfilesAndPrivate_withoutApiKeyValues() = runBlocking<Unit> {
        config.upsertProfile(user)
        config.upsertProfile(EndpointProfile(BuiltInProfiles.PRIVATE_ID, "x", "https://pl.example.com", apiKeyHeader = "X-Key", apiKeyValue = "pl-secret"))
        val json = config.exportProfilesJson()
        assertThat(json).doesNotContain("s3cret")
        assertThat(json).doesNotContain("pl-secret")
        assertThat(json).contains("X-Api-Key")
        assertThat(json).contains("\"u1\"")
        assertThat(json).contains(BuiltInProfiles.PRIVATE_ID)
        assertThat(json).doesNotContain(BuiltInProfiles.CLOUD_ID)
        assertThat(json).doesNotContain("\"${BuiltInProfiles.LOCAL_ID}\"")
    }

    @Test fun import_roundTrip_keepsStoredApiKey_andNeverOverwritesCloudOrLocal() = runBlocking<Unit> {
        config.upsertProfile(user)
        val exported = config.exportProfilesJson()
        val extra = """[{"id":"${BuiltInProfiles.CLOUD_ID}","name":"x","baseUrl":"https://evil.example.com"},
            |{"id":"${BuiltInProfiles.LOCAL_ID}","name":"x","baseUrl":"https://evil.example.com"},
            |{"id":"u2","name":"Second","baseUrl":"http://10.0.0.5:8080","builtIn":true}]""".trimMargin()
        config.importProfilesJson(exported)
        config.importProfilesJson(extra)
        val byId = config.profiles.value.associateBy { it.id }
        assertThat(byId.getValue("u1").apiKeyValue).isEqualTo("s3cret")
        assertThat(byId.getValue(BuiltInProfiles.CLOUD_ID).baseUrl).isEqualTo("https://vs1.mock.example.com")
        assertThat(byId.getValue(BuiltInProfiles.LOCAL_ID).baseUrl).isEqualTo(BuiltInProfiles.LOCAL_BASE_URL)
        assertThat(byId.getValue("u2").builtIn).isFalse()
        assertThat(byId.getValue("u2").baseUrl).isEqualTo("http://10.0.0.5:8080")
    }

    @Test fun import_invalid_rejectsWholeFile() = runBlocking<Unit> {
        for (json in listOf("not json", "{}", """[{"id":"a"}]""", """[{"id":"ok","name":"Ok","baseUrl":"https://ok.example.com"},{"id":"bad","name":"Bad","baseUrl":"ftp://x"}]""")) {
            val error = runCatching { config.importProfilesJson(json) }.exceptionOrNull()
            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThat(config.profiles.value.map { it.id }).doesNotContain("ok")
    }

    @Test fun corruptProfilesJson_fallsBackToBuiltIns() = runBlocking<Unit> {
        store.dataStore.edit { it[DataStoreDemoConfig.KEY_PROFILES] = "{broken" }
        reopen()
        assertThat(config.profiles.value).hasSize(3)
    }

    @Test fun blankScenario_isDefault() = runBlocking<Unit> {
        config.setScenario("  ")
        assertThat(config.scenario.value).isEqualTo("default")
    }

    @Test fun emptyMockBaseUrl_givesEmptyCloud() {
        store.close()
        store = TestStore(file)
        val c = DataStoreDemoConfig(store.dataStore, buildInfo.copy(mockBaseUrl = ""), cipher)
        assertThat(c.activeProfile.value.baseUrl).isEmpty()
    }

}
