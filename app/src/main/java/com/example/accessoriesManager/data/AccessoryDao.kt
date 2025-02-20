package com.example.accessoriesManager.data
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.accessoriesManager.model.Accessory

@Dao
interface AccessoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccessory(accessory: Accessory)

    @Query("SELECT * FROM accessories")
    suspend fun getAllAccessories(): List<Accessory>

    @Query("DELETE FROM accessories WHERE id = :id")
    suspend fun deleteAccessory(id: Int)
}