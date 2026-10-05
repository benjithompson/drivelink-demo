package com.drivelink.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drivelink.core.domain.config.Session
import com.drivelink.core.domain.config.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SessionStore] in a Preferences [DataStore].
 *
 * The tokens are stored as plain text. This is acceptable for the demo only: the mock issues
 * fake tokens (`at-demo-…`). A real app would encrypt them like the API key values.
 *
 * The constructor reads the stored session once, blocking, so the auth interceptor sends the
 * restored token on the first request after a cold start. Writes update [session] before they
 * return.
 */
@Singleton
class DataStoreSessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SessionStore {

    private val mutex = Mutex()
    private val _session = MutableStateFlow(read(readBlocking()))

    override val session: StateFlow<Session?> = _session.asStateFlow()

    override suspend fun save(session: Session) = mutex.withLock {
        val prefs = dataStore.edit {
            it[KEY_ACCESS] = session.accessToken
            it[KEY_REFRESH] = session.refreshToken
            it[KEY_EMAIL] = session.email
        }
        _session.value = read(prefs)
    }

    override suspend fun clear() = mutex.withLock {
        val prefs = dataStore.edit {
            it.remove(KEY_ACCESS)
            it.remove(KEY_REFRESH)
            it.remove(KEY_EMAIL)
        }
        _session.value = read(prefs)
    }

    private fun readBlocking(): Preferences = try {
        runBlocking { dataStore.data.first() }
    } catch (e: IOException) {
        emptyPreferences()
    }

    private fun read(prefs: Preferences): Session? {
        val access = prefs[KEY_ACCESS] ?: return null
        return Session(access, prefs[KEY_REFRESH].orEmpty(), prefs[KEY_EMAIL].orEmpty())
    }

    private companion object {
        val KEY_ACCESS = stringPreferencesKey("session_access_token")
        val KEY_REFRESH = stringPreferencesKey("session_refresh_token")
        val KEY_EMAIL = stringPreferencesKey("session_email")
    }
}
