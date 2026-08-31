package com.example.accessoriesmanager.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.accessoriesmanager.model.Accessory

@Entity(tableName = "accessories")
data class AccessoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val price: Long,
    val createdAt: Long?,
    val updatedAt: Long?
)

fun Accessory.toEntity(): AccessoryEntity = AccessoryEntity(
    id = id,
    name = name,
    price = price,
    createdAt = createdAt?.toDate()?.time,
    updatedAt = updatedAt?.toDate()?.time
)

fun AccessoryEntity.toModel(): Accessory = Accessory(
    id = id,
    name = name,
    price = price,
    createdAt = createdAt?.let { com.google.firebase.Timestamp(java.util.Date(it)) },
    updatedAt = updatedAt?.let { com.google.firebase.Timestamp(java.util.Date(it)) }
)
