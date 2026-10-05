package com.drivelink.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.drivelink.core.domain.config.AppPreferences
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.PinStore
import com.drivelink.core.domain.config.SessionStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SettingsModule.FILE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Settings bindings. The app module must provide [com.drivelink.core.domain.config.AppBuildInfo].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {

    @Binds
    abstract fun bindDemoConfig(impl: DataStoreDemoConfig): DemoConfig

    @Binds
    abstract fun bindSessionStore(impl: DataStoreSessionStore): SessionStore

    @Binds
    abstract fun bindAppPreferences(impl: DataStoreAppPreferences): AppPreferences

    @Binds
    abstract fun bindPinStore(impl: DataStorePinStore): PinStore

    companion object {
        const val FILE_NAME = "drivelink_settings"

        /**
         * One DataStore instance for the file per process; DataStore allows only one active
         * instance per file. The instance lives in a process-wide property delegate, not in the
         * Hilt component, because Hilt tests create a new component for each test.
         */
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.applicationContext.settingsDataStore

        @Provides
        @Singleton
        fun provideSecretCipher(): SecretCipher = KeystoreSecretCipher()
    }
}
