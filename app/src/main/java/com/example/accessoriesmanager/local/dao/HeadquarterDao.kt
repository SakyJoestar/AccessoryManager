package com.example.accessoriesmanager.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.accessoriesmanager.local.entity.HeadquarterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeadquarterDao {
    @Query("SELECT * FROM headquarters ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<HeadquarterEntity>>

    @Query("SELECT * FROM headquarters ORDER BY name COLLATE NOCASE")
    suspend fun getAllOnce(): List<HeadquarterEntity>

    @Query("SELECT * FROM headquarters WHERE id = :id")
    suspend fun getById(id: String): HeadquarterEntity?

    @Upsert
    suspend fun upsert(item: HeadquarterEntity)

    @Upsert
    suspend fun upsertAll(items: List<HeadquarterEntity>)

    @Query("DELETE FROM headquarters WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM headquarters")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<HeadquarterEntity>) {
        clear()
        upsertAll(items)
    }
}
