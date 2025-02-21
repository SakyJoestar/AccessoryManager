package com.example.accessoriesManager.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.accessoriesManager.model.Accessory

@Database(entities = [Accessory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accessoryDao(): AccessoryDao
}