package com.example.accessoriesManager.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accessories")
data class Accessory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val price: Double
)
