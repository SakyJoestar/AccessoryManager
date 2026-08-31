package com.example.accessoriesmanager.di

import com.example.accessoriesmanager.repository.AccessoryRepository
import com.example.accessoriesmanager.repository.AuthRepository
import com.example.accessoriesmanager.repository.FirebaseAuthRepository
import com.example.accessoriesmanager.repository.HeadquarterRepository
import com.example.accessoriesmanager.repository.InstallationRepository
import com.example.accessoriesmanager.repository.OfflineAccessoryRepository
import com.example.accessoriesmanager.repository.OfflineHeadquarterRepository
import com.example.accessoriesmanager.repository.OfflineInstallationRepository
import com.example.accessoriesmanager.repository.OfflineVehicleRepository
import com.example.accessoriesmanager.repository.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindAuthRepository(
        impl: FirebaseAuthRepository
    ): AuthRepository

    @Binds @Singleton
    abstract fun bindAccessoryRepository(
        impl: OfflineAccessoryRepository
    ): AccessoryRepository

    @Binds @Singleton
    abstract fun bindHeadquarterRepository(
        impl: OfflineHeadquarterRepository
    ): HeadquarterRepository

    @Binds @Singleton
    abstract fun bindVehicleRepository(
        impl: OfflineVehicleRepository
    ): VehicleRepository

    @Binds @Singleton
    abstract fun bindInstallationRepository(
        impl: OfflineInstallationRepository
    ): InstallationRepository
}
