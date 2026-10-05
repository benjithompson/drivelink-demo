package com.drivelink.core.settings

import com.drivelink.core.domain.config.Session
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStoreSessionStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val stores = mutableListOf<TestStore>()

    @After fun tearDown() = stores.forEach { it.close() }

    private fun file() = File(tmp.root, "session.preferences_pb")

    private fun open(): DataStoreSessionStore {
        stores.forEach { it.close() }
        val store = TestStore(file()).also { stores += it }
        return DataStoreSessionStore(store.dataStore)
    }

    @Test fun empty_byDefault() {
        assertThat(open().session.value).isNull()
    }

    @Test fun save_updatesFlow_andPersists() = runBlocking<Unit> {
        val session = Session("at-1", "rt-1", "alex@drivelink.test")
        val store = open()
        store.save(session)
        assertThat(store.session.value).isEqualTo(session)
        assertThat(open().session.value).isEqualTo(session)
    }

    @Test fun clear_removesSession_andPersists() = runBlocking<Unit> {
        val store = open()
        store.save(Session("at-1", "rt-1", "alex@drivelink.test"))
        store.clear()
        assertThat(store.session.value).isNull()
        assertThat(open().session.value).isNull()
    }
}
