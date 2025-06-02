package com.example.accessoriesManager.repository

import com.example.accessoriesManager.data.AccessoryDao
import com.example.accessoriesManager.model.Accessory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AccessoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun getCurrentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    suspend fun saveAccessory(accessory: Accessory): Result<Unit> {
        val userId = getCurrentUserId()
        return userId?.let {
            try {
                firestore
                    .collection("users")
                    .document(it)
                    .collection("accessories")
                    .add(accessory)
                    .await()

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(Exception("Error saving accessory: ${e.message}"))
            }
        } ?: Result.failure(Exception("User not authenticated"))
    }

    suspend fun updateAccessory(accessory: Accessory): Result<Unit> {
        val userId = getCurrentUserId()
        return userId?.let {
            accessory.id?.let { id ->
                try {
                    firestore.collection("users")
                        .document(it)
                        .collection("accessories")
                        .document(id)
                        .set(accessory)
                        .await()

                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception("Error updating accessory: ${e.message}"))
                }
            } ?: Result.failure(Exception("Accessory ID is null"))
        } ?: Result.failure(Exception("User not authenticated"))
    }

    suspend fun deleteAccessory(accessory: Accessory): Result<Unit> {
        val userId = getCurrentUserId()
        return userId?.let {
            accessory.id?.let { id ->
                try {
                    firestore
                        .collection("users")
                        .document(it)
                        .collection("accessories")
                        .document(id)
                        .delete()
                        .await()

                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(Exception("Error deleting accessory: ${e.message}"))
                }
            } ?: Result.failure(Exception("Accessory ID is null"))
        } ?: Result.failure(Exception("User not authenticated"))
    }

    suspend fun getAccessoriesList(): Result<MutableList<Accessory>> {
        val userId = getCurrentUserId()
        return userId?.let {
            try {
                val snapshot = firestore
                    .collection("users")
                    .document(it)
                    .collection("accessories")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                val accessories = snapshot.toObjects(Accessory::class.java)
                Result.success(accessories)
            } catch (e: Exception) {
                Result.failure(Exception("Error getting accessories: ${e.message}"))
            }
        } ?: Result.failure(Exception("User not authenticated"))
    }
}
