package com.drivelink.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.util.Base64

/** Reversible fake: Base64 with a prefix, so tests can see that the stored value is not plain. */
class FakeSecretCipher : SecretCipher {
    override fun encrypt(plain: String): String = "enc:" + Base64.getEncoder().encodeToString(plain.reversed().toByteArray())
    override fun decrypt(encrypted: String): String {
        require(encrypted.startsWith("enc:")) { "not encrypted" }
        return String(Base64.getDecoder().decode(encrypted.removePrefix("enc:"))).reversed()
    }
}

/** A DataStore on [file]. Close the previous instance (cancel its scope) before opening the file again. */
class TestStore(val file: File) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    fun close() = scope.cancel()
}
