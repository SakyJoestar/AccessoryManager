package com.example.accessoriesmanager.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.accessoriesmanager.local.entity.InstallationEntity
import kotlinx.coroutines.flow.Flow

/** Caches only the 10 most recent installations — [trimToTop10] enforces the cap after every write. */
@Dao
interface InstallationDao {
    @Query("SELECT * FROM installations ORDER BY date DESC LIMIT 10")
    fun observeRecent10(): Flow<List<InstallationEntity>>

    @Query("SELECT * FROM installations ORDER BY date DESC LIMIT 10")
    suspend fun getRecent10Once(): List<InstallationEntity>

    @Query("SELECT * FROM installations WHERE id = :id")
    suspend fun getById(id: String): InstallationEntity?

    @Query("DELETE FROM installations WHERE id NOT IN (SELECT id FROM installations ORDER BY date DESC LIMIT 10)")
    suspend fun trimToTop10()

    @Upsert
    suspend fun upsert(item: InstallationEntity)

    @Upsert
    suspend fun upsertAll(items: List<InstallationEntity>)

    @Query("DELETE FROM installations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM installations")
    suspend fun clear()

    @Transaction
    suspend fun upsertAndTrim(item: InstallationEntity) {
        upsert(item)
        trimToTop10()
    }

    @Transaction
    suspend fun replaceAllAndTrim(items: List<InstallationEntity>) {
        clear()
        upsertAll(items.sortedByDescending { it.date ?: 0L }.take(10))
    }
}
