package com.drivelink.core.data.di

import com.drivelink.core.data.repository.AccountRepositoryImpl
import com.drivelink.core.data.repository.AlertsRepositoryImpl
import com.drivelink.core.data.repository.AuthRepositoryImpl
import com.drivelink.core.data.repository.CommandRepositoryImpl
import com.drivelink.core.data.repository.VehicleRepositoryImpl
import com.drivelink.core.domain.repository.AccountRepository
import com.drivelink.core.domain.repository.AlertsRepository
import com.drivelink.core.domain.repository.AuthRepository
import com.drivelink.core.domain.repository.CommandRepository
import com.drivelink.core.domain.repository.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository bindings. The repositories are stateless; @Singleton only avoids new instances.
 * [com.drivelink.core.domain.command.RunCommandUseCase] has an @Inject constructor and needs
 * [com.drivelink.core.domain.TimingLog] from the app.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds @Singleton abstract fun bindAuth(impl: AuthRepositoryImpl): AuthRepository
    @Binds @Singleton abstract fun bindAccount(impl: AccountRepositoryImpl): AccountRepository
    @Binds @Singleton abstract fun bindVehicle(impl: VehicleRepositoryImpl): VehicleRepository
    @Binds @Singleton abstract fun bindCommand(impl: CommandRepositoryImpl): CommandRepository
    @Binds @Singleton abstract fun bindAlerts(impl: AlertsRepositoryImpl): AlertsRepository
}
