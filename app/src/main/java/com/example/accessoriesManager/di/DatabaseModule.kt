package com.example.accessoriesManager.di

import android.content.Context
import androidx.room.Room
import com.example.accessoriesManager.data.AccessoryDao
import com.example.accessoriesManager.data.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "app_database"
        ).build()
    }

    @Provides
    fun provideAccessoryDao(database: AppDatabase): AccessoryDao {
        return database.accessoryDao()
    }
}
