package com.drivelink.core.data.di

import com.drivelink.core.domain.config.AppBuildInfo
import com.drivelink.core.domain.config.DemoConfig
import com.drivelink.core.domain.config.SessionStore
import com.drivelink.core.network.DriveLinkHttp
import com.drivelink.core.network.api.DriveLinkApi
import com.drivelink.core.network.inspector.NetworkInspector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.Interceptor
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Extra OkHttp application interceptors that run after the DriveLink interceptors (after the
 * Network Inspector). The app contributes `@IntoSet` bindings, for example an
 * HttpLoggingInterceptor in debug builds only. The set can be empty. Order within the set is
 * not defined, so contribute at most one order-sensitive interceptor.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExtraInterceptors

/**
 * Network bindings. Needs from other modules: [DemoConfig] and [SessionStore] (core:settings),
 * [AppBuildInfo] (app). [NetworkInspector] has an @Inject constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    /** Declares the set so it can be empty (release builds contribute nothing). */
    @Multibinds
    @ExtraInterceptors
    abstract fun extraInterceptors(): Set<Interceptor>

    companion object {
        @Provides
        @Singleton
        fun provideDriveLinkHttp(
            demoConfig: DemoConfig,
            sessionStore: SessionStore,
            buildInfo: AppBuildInfo,
            inspector: NetworkInspector,
            @ExtraInterceptors extraInterceptors: Set<@JvmSuppressWildcards Interceptor>,
        ): DriveLinkHttp = DriveLinkHttp(
            demoConfig = demoConfig,
            sessionStore = sessionStore,
            buildInfo = buildInfo,
            inspector = inspector,
            extraInterceptors = extraInterceptors.toList(),
        )

        @Provides
        @Singleton
        fun provideDriveLinkApi(http: DriveLinkHttp): DriveLinkApi = http.create()
    }
}
