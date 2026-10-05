package com.drivelink.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drivelink.core.domain.config.PinStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PinStore] in the settings [DataStore]. The constructor reads the stored value once, blocking,
 * so the start destination of the app can depend on [hasPin].
 */
@Singleton
class DataStorePinStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PinStore {

    private data class Stored(val salt: String, val hash: String)

    private val mutex = Mutex()
    private val state = MutableStateFlow(read(readBlocking()))

    private val _hasPin = MutableStateFlow(state.value != null)

    override val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    override suspend fun set(pin: String) {
        require(pin.length == 4 && pin.all { it in '0'..'9' }) { "The PIN must be 4 digits." }
        mutex.withLock {
            val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.toHex()
            val stored = Stored(salt, hash(salt, pin))
            dataStore.edit {
                it[KEY_SALT] = stored.salt
                it[KEY_HASH] = stored.hash
            }
            state.value = stored
            _hasPin.value = true
        }
    }

    override fun verify(pin: String): Boolean {
        val stored = state.value ?: return false
        return MessageDigest.isEqual(hash(stored.salt, pin).toByteArray(), stored.hash.toByteArray())
    }

    override suspend fun clear() {
        mutex.withLock {
            dataStore.edit {
                it.remove(KEY_SALT)
                it.remove(KEY_HASH)
            }
            state.value = null
            _hasPin.value = false
        }
    }

    private fun readBlocking(): Preferences = try {
        runBlocking { dataStore.data.first() }
    } catch (e: IOException) {
        emptyPreferences()
    }

    private fun read(prefs: Preferences): Stored? {
        val salt = prefs[KEY_SALT] ?: return null
        val hash = prefs[KEY_HASH] ?: return null
        return Stored(salt, hash)
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        val KEY_SALT = stringPreferencesKey("pin_salt")
        val KEY_HASH = stringPreferencesKey("pin_hash")
    }
}
