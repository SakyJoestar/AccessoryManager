package com.example.accessoriesManager.data
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.accessoriesManager.model.Accessory
import kotlinx.coroutines.flow.Flow

@Dao
interface AccessoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccessory(accessory: Accessory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccessories(accessories: List<Accessory>)

    @Query("SELECT * FROM accessories")
    fun getAllAccessories(): Flow<List<Accessory>>

    @Query("DELETE FROM accessories WHERE id = :id")
    suspend fun deleteAccessory(id: Int)

    @Query("SELECT * FROM accessories WHERE isPendingSync = 1")
    fun getPendingAccessories(): List<Accessory>

    @Query("UPDATE accessories SET isPendingSync = 0 WHERE isPendingSync = 1")
    suspend fun markAsSynced()
}
