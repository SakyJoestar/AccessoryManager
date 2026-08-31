package com.example.accessoriesmanager.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PendingEntityType { ACCESSORY, VEHICLE, HEADQUARTER, INSTALLATION }

enum class PendingOperationType { CREATE, UPDATE, DELETE, MARK_PAID }

enum class PendingStatus { PENDING, FAILED }

/** One queued offline write, replayed against Firestore by `SyncManager` once online. */
@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val entityType: String,
    val operationType: String,
    val targetId: String?,
    val payloadJson: String,
    val createdAt: Long,
    val status: String,
    val lastError: String?,
    val attemptCount: Int = 0
)
