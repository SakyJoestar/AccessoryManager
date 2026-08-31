package com.example.accessoriesmanager.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.accessoriesmanager.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY make COLLATE NOCASE")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY make COLLATE NOCASE")
    suspend fun getAllOnce(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getById(id: String): VehicleEntity?

    @Upsert
    suspend fun upsert(item: VehicleEntity)

    @Upsert
    suspend fun upsertAll(items: List<VehicleEntity>)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM vehicles")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<VehicleEntity>) {
        clear()
        upsertAll(items)
    }
}
