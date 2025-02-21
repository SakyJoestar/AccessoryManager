    package com.example.accessoriesManager.data
    import androidx.room.Dao
    import androidx.room.Insert
    import androidx.room.OnConflictStrategy
    import androidx.room.Query
    import com.example.accessoriesManager.model.Accessory
    import kotlinx.coroutines.flow.Flow

    @Dao
    interface AccessoryDao {
        @Insert(onConflict = OnConflictStrategy.ABORT)
        suspend fun insertAccessory(accessory: Accessory)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertAccessories(accessories: List<Accessory>)

        @Query("SELECT * FROM accessories")
        fun getAllAccessories(): Flow<List<Accessory>>

        @Query("SELECT * FROM accessories")
        suspend fun getAllAccessoriesOnce(): List<Accessory> // ✅ Obtiene los accesorios una vez (sin Flow

        @Query("UPDATE accessories SET name = :name, price = :price, isPendingSync = :isPendingSync WHERE id = :id")
        suspend fun updateAccessory(id: Int, name: String, price: Double, isPendingSync: Boolean)

        @Query("SELECT * FROM accessories WHERE isPendingSync = 1")
        fun getPendingAccessories(): Flow<List<Accessory>>

        @Query("DELETE FROM accessories WHERE id = :id")
        suspend fun deleteAccessory(id: Int)

        @Query("UPDATE accessories SET isDeleted = 1 WHERE id = :id")
        suspend fun markAccessoryAsDeleted(id: Int)

        @Query("SELECT * FROM accessories WHERE isDeleted = 1")
        fun getDeletedAccessories(): Flow<List<Accessory>>

        @Query("DELETE FROM accessories WHERE isDeleted = 1 AND isPendingSync = 0")
        suspend fun clearDeletedAccessories()

        @Query("UPDATE accessories SET firebaseId = :firebaseId WHERE id = :id")
        suspend fun updateAccessoryFirebaseId(id: Int, firebaseId: String)
    }

