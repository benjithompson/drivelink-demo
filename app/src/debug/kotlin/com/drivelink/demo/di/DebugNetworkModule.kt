package com.drivelink.demo.di

import com.drivelink.core.data.di.ExtraInterceptors
import com.drivelink.core.network.DriveLinkHeaders
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor

/** Debug builds only: HTTP logging to Logcat (tag okhttp.OkHttpClient). Release builds contribute nothing. */
@Module
@InstallIn(SingletonComponent::class)
object DebugNetworkModule {

    @Provides
    @IntoSet
    @ExtraInterceptors
    fun provideLoggingInterceptor(): Interceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS
        redactHeader(DriveLinkHeaders.AUTHORIZATION)
    }
}
