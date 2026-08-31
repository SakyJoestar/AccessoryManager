package com.example.accessoriesmanager.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.accessoriesmanager.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOperationDao {
    /** PENDING and FAILED both get replayed on the next sync — FAILED is a retry candidate, not a dead end. */
    @Query("SELECT * FROM pending_operations WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingOperationEntity>

    @Query("SELECT * FROM pending_operations ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingOperationEntity>>

    @Query("SELECT COUNT(*) FROM pending_operations WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Insert
    suspend fun insert(op: PendingOperationEntity): Long

    @Update
    suspend fun update(op: PendingOperationEntity)

    @Query("DELETE FROM pending_operations WHERE localId = :id")
    suspend fun delete(id: Long)
}
