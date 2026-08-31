package com.example.accessoriesmanager.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.accessoriesmanager.local.entity.AccessoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessoryDao {
    @Query("SELECT * FROM accessories ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AccessoryEntity>>

    @Query("SELECT * FROM accessories ORDER BY name COLLATE NOCASE")
    suspend fun getAllOnce(): List<AccessoryEntity>

    @Query("SELECT * FROM accessories WHERE id = :id")
    suspend fun getById(id: String): AccessoryEntity?

    @Upsert
    suspend fun upsert(item: AccessoryEntity)

    @Upsert
    suspend fun upsertAll(items: List<AccessoryEntity>)

    @Query("DELETE FROM accessories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM accessories")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<AccessoryEntity>) {
        clear()
        upsertAll(items)
    }
}
