package com.example.accessoriesmanager.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.accessoriesmanager.model.Installation
import com.example.accessoriesmanager.model.InstalledAccessory
import com.example.accessoriesmanager.model.headquarterLabelFromAny
import com.example.accessoriesmanager.model.vehicleLabelFromAny
import com.google.firebase.Timestamp
import java.util.Date

/**
 * Local mirror of [Installation]. `headquarter`/`vehicle` are `Any?` on the Firestore model for
 * backward compatibility (legacy Map or plain String) — Room only ever stores the resolved label.
 */
@Entity(tableName = "installations")
data class InstallationEntity(
    @PrimaryKey val id: String,
    val order: Int?,
    val serie: String?,
    val plate: String?,
    val warehouse: String?,
    val condition: String?,
    val date: Long?,
    val headquarterLabel: String?,
    val headquarterId: String?,
    val increment: Long?,
    val vehicleLabel: String?,
    val vehicleId: String?,
    val accessories: List<InstalledAccessory>?,
    val photos: List<String>?,
    val state: String?,
    val totalWorked: Long?,
    val totalPaid: Long?,
    val totalUnpaid: Long?,
    val comment: String?,
    val createdAt: Long?,
    val updatedAt: Long?
)

fun Installation.toEntity(): InstallationEntity = InstallationEntity(
    id = id.orEmpty(),
    order = order,
    serie = serie,
    plate = plate,
    warehouse = warehouse,
    condition = condition,
    date = date?.toDate()?.time,
    headquarterLabel = headquarterLabelFromAny(headquarter).takeIf { it.isNotBlank() },
    headquarterId = headquarterId,
    increment = increment,
    vehicleLabel = vehicleLabelFromAny(vehicle).takeIf { it.isNotBlank() },
    vehicleId = vehicleId,
    accessories = accessories,
    photos = photos,
    state = state,
    totalWorked = totalWorked,
    totalPaid = totalPaid,
    totalUnpaid = totalUnpaid,
    comment = comment,
    createdAt = createdAt?.toDate()?.time,
    updatedAt = updatedAt?.toDate()?.time
)

fun InstallationEntity.toModel(): Installation = Installation(
    id = id,
    order = order,
    serie = serie,
    plate = plate,
    warehouse = warehouse,
    condition = condition,
    date = date?.let { Timestamp(Date(it)) },
    headquarter = headquarterLabel,
    headquarterId = headquarterId,
    increment = increment,
    vehicle = vehicleLabel,
    vehicleId = vehicleId,
    accessories = accessories,
    photos = photos,
    state = state,
    totalWorked = totalWorked,
    totalPaid = totalPaid,
    totalUnpaid = totalUnpaid,
    comment = comment,
    createdAt = createdAt?.let { Timestamp(Date(it)) },
    updatedAt = updatedAt?.let { Timestamp(Date(it)) }
)
