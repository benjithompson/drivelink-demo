package com.drivelink.core.settings

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStorePinStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val stores = mutableListOf<TestStore>()

    @After fun tearDown() = stores.forEach { it.close() }

    private fun open(): Pair<DataStorePinStore, TestStore> {
        stores.forEach { it.close() }
        val store = TestStore(File(tmp.root, "pin.preferences_pb")).also { stores += it }
        return DataStorePinStore(store.dataStore) to store
    }

    @Test fun noPin_byDefault() {
        val (pins, _) = open()
        assertThat(pins.hasPin.value).isFalse()
        assertThat(pins.verify("1234")).isFalse()
    }

    @Test fun setPin_verifies_andPersists_withoutStoringThePin() = runBlocking<Unit> {
        val (pins, store) = open()
        pins.set("1234")

        assertThat(pins.hasPin.value).isTrue()
        assertThat(pins.verify("1234")).isTrue()
        assertThat(pins.verify("4321")).isFalse()

        val (reopened, _) = open()
        assertThat(reopened.hasPin.value).isTrue()
        assertThat(reopened.verify("1234")).isTrue()
        assertThat(File(tmp.root, "pin.preferences_pb").readBytes().toString(Charsets.ISO_8859_1)).doesNotContain("1234")
        store.close()
    }

    @Test fun clear_removesThePin() = runBlocking<Unit> {
        val (pins, _) = open()
        pins.set("1234")
        pins.clear()

        assertThat(pins.hasPin.value).isFalse()
        assertThat(open().first.hasPin.value).isFalse()
    }

    @Test fun invalidPin_isRejected() {
        val (pins, _) = open()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { pins.set("12a4") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { pins.set("123") } }
    }
}
