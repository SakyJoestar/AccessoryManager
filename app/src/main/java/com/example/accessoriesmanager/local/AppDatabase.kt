package com.example.accessoriesmanager.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.accessoriesmanager.local.converter.Converters
import com.example.accessoriesmanager.local.dao.AccessoryDao
import com.example.accessoriesmanager.local.dao.HeadquarterDao
import com.example.accessoriesmanager.local.dao.InstallationDao
import com.example.accessoriesmanager.local.dao.PendingOperationDao
import com.example.accessoriesmanager.local.dao.VehicleDao
import com.example.accessoriesmanager.local.entity.AccessoryEntity
import com.example.accessoriesmanager.local.entity.HeadquarterEntity
import com.example.accessoriesmanager.local.entity.InstallationEntity
import com.example.accessoriesmanager.local.entity.PendingOperationEntity
import com.example.accessoriesmanager.local.entity.VehicleEntity

@Database(
    entities = [
        AccessoryEntity::class,
        VehicleEntity::class,
        HeadquarterEntity::class,
        InstallationEntity::class,
        PendingOperationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accessoryDao(): AccessoryDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun headquarterDao(): HeadquarterDao
    abstract fun installationDao(): InstallationDao
    abstract fun pendingOperationDao(): PendingOperationDao
}
