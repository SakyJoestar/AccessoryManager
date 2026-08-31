package com.example.accessoriesmanager.di

import android.content.Context
import androidx.room.Room
import com.example.accessoriesmanager.local.AppDatabase
import com.example.accessoriesmanager.local.dao.AccessoryDao
import com.example.accessoriesmanager.local.dao.HeadquarterDao
import com.example.accessoriesmanager.local.dao.InstallationDao
import com.example.accessoriesmanager.local.dao.PendingOperationDao
import com.example.accessoriesmanager.local.dao.VehicleDao
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "accessories_manager.db").build()

    @Provides
    fun provideAccessoryDao(db: AppDatabase): AccessoryDao = db.accessoryDao()

    @Provides
    fun provideVehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()

    @Provides
    fun provideHeadquarterDao(db: AppDatabase): HeadquarterDao = db.headquarterDao()

    @Provides
    fun provideInstallationDao(db: AppDatabase): InstallationDao = db.installationDao()

    @Provides
    fun providePendingOperationDao(db: AppDatabase): PendingOperationDao = db.pendingOperationDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()
}
