package com.example.accessoriesManager.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accessories", indices = [androidx.room.Index(value = ["name"], unique = true)])
data class Accessory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firebaseId: String? = null, // Para enlazar con Firebase
    val name: String,
    val price: Double,
    var isPendingSync: Boolean = false,
    var isDeleted: Boolean = false  // Nuevo campo
){
    fun markAsDeleted(): Accessory = copy(isDeleted = true)
    fun markAsSynced(): Accessory = copy(isPendingSync = false)
}
