package com.example.accessoriesmanager.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.accessoriesmanager.model.Vehicle
import com.google.firebase.Timestamp
import java.util.Date

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val make: String?,
    val model: String?,
    val createdAt: Long?,
    val updatedAt: Long?
)

fun Vehicle.toEntity(): VehicleEntity = VehicleEntity(
    id = id.orEmpty(),
    make = make,
    model = model,
    createdAt = createdAt?.toDate()?.time,
    updatedAt = updatedAt?.toDate()?.time
)

fun VehicleEntity.toModel(): Vehicle = Vehicle(
    id = id,
    make = make,
    model = model,
    createdAt = createdAt?.let { Timestamp(Date(it)) },
    updatedAt = updatedAt?.let { Timestamp(Date(it)) }
)
