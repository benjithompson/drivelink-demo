package com.drivelink.core.settings

import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.BuiltInProfiles
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingExternalConfigTest {
    @get:Rule val tmp = TemporaryFolder()

    private val buildInfo = AppBuildInfo(mockBaseUrl = "https://vs1.mock.example.com", clientVersion = "android/0.1.0", debug = true)
    private val stores = mutableListOf<TestStore>()
    private val file by lazy { File(tmp.root, "settings.preferences_pb") }

    @After fun tearDown() = stores.forEach { it.close() }

    private fun open(pending: PendingExternalConfig): DataStoreDemoConfig {
        stores.forEach { it.close() }
        val store = TestStore(file).also { stores += it }
        return DataStoreDemoConfig(store.dataStore, buildInfo, FakeSecretCipher(), pending)
    }

    @Test fun holder_blankValuesAreAbsent_andTakeClears() {
        val pending = PendingExternalConfig()
        pending.set(" ", null)
        assertThat(pending.take()).isNull()
        pending.set("http://10.0.2.2:8080", "")
        assertThat(pending.take()).isEqualTo(PendingExternalConfig.Values("http://10.0.2.2:8080", null))
        assertThat(pending.take()).isNull()
    }

    @Test fun nothingPending_keepsDefaults() {
        val config = open(PendingExternalConfig())
        assertThat(config.activeProfile.value.id).isEqualTo(BuiltInProfiles.CLOUD_ID)
        assertThat(config.scenario.value).isEqualTo("default")
    }

    @Test fun pendingValues_applyOnce_andPersist() {
        val pending = PendingExternalConfig().apply { set("http://10.0.2.2:8080/", "slow-vehicle") }
        val config = open(pending)
        assertThat(config.activeProfile.value.id).isEqualTo("external")
        assertThat(config.activeProfile.value.baseUrl).isEqualTo("http://10.0.2.2:8080")
        assertThat(config.scenario.value).isEqualTo("slow-vehicle")
        assertThat(pending.take()).isNull()

        val reopened = open(pending)
        assertThat(reopened.activeProfile.value.id).isEqualTo("external")
        assertThat(reopened.scenario.value).isEqualTo("slow-vehicle")
    }

    @Test fun invalidBaseUrl_isRecorded_andScenarioStillApplies() {
        val pending = PendingExternalConfig().apply { set("ftp://nope", "server-error") }
        val config = open(pending)
        assertThat(pending.lastError).contains("http")
        assertThat(config.activeProfile.value.id).isEqualTo(BuiltInProfiles.CLOUD_ID)
        assertThat(config.scenario.value).isEqualTo("server-error")
    }
}
