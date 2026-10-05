package com.drivelink.demo.di

import com.drivelink.core.domain.TimingLog
import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.demo.BuildConfig
import com.drivelink.demo.timing.LogcatTimingLog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** App-level values that the core modules need: build values and the timing log. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppBuildInfo(): AppBuildInfo = AppBuildInfo(
        mockBaseUrl = BuildConfig.MOCK_BASE_URL,
        clientVersion = "android/" + BuildConfig.VERSION_NAME,
        debug = BuildConfig.DEBUG,
    )

    @Provides
    @Singleton
    fun provideTimingLog(): TimingLog = LogcatTimingLog
}
