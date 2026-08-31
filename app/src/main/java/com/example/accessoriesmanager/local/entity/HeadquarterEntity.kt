package com.example.accessoriesmanager.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.accessoriesmanager.model.Headquarter
import com.google.firebase.Timestamp
import java.util.Date

@Entity(tableName = "headquarters")
data class HeadquarterEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val increment: Int,
    val createdAt: Long?,
    val updatedAt: Long?
)

fun Headquarter.toEntity(): HeadquarterEntity = HeadquarterEntity(
    id = id.orEmpty(),
    name = name,
    increment = increment,
    createdAt = createdAt?.toDate()?.time,
    updatedAt = updatedAt?.toDate()?.time
)

fun HeadquarterEntity.toModel(): Headquarter = Headquarter(
    id = id,
    name = name,
    increment = increment,
    createdAt = createdAt?.let { Timestamp(Date(it)) },
    updatedAt = updatedAt?.let { Timestamp(Date(it)) }
)
