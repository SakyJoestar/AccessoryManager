package com.example.accessoriesManager.repository

import com.example.accessoriesManager.data.AccessoryDao
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.util.NetworkHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AccessoryRepository @Inject constructor(
    private val accessoryDao: AccessoryDao,
    private val firestore: FirebaseFirestore,
    private val networkHelper: NetworkHelper
) {

    /** 📌 Agrega un accesorio a Firebase y Room */
    suspend fun addAccessory(accessory: Accessory): Boolean {
        return try {
            val accessoryToSave = if (networkHelper.isOnline()) {
                val docRef = firestore.collection("accessories").document()
                val newAccessory = accessory.copy(firebaseId = docRef.id) // ✅ Guarda el ID de Firebase
                docRef.set(newAccessory).await()
                newAccessory
            } else {
                accessory.copy(isPendingSync = true)
            }

            accessoryDao.insertAccessory(accessoryToSave) // ✅ Guarda en Room después
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 📌 Sincroniza accesorios desde Firebase a Room */
    suspend fun syncAccessoriesFromFirebase() {
        if (networkHelper.isOnline()) {
            try {
                val snapshot = firestore.collection("accessories").get().await()
                val firebaseList = snapshot.toObjects(Accessory::class.java)

                val localList = accessoryDao.getAllAccessoriesOnce()

                val newOrUpdatedAccessories = firebaseList.filter { firebaseItem ->
                    val localItem = localList.find { it.firebaseId == firebaseItem.firebaseId }
                    localItem == null || localItem != firebaseItem // ✅ Nuevo o diferente
                }

                if (newOrUpdatedAccessories.isNotEmpty()) {
                    accessoryDao.insertAccessories(newOrUpdatedAccessories)
                }
            } catch (e: Exception) {
                // Log error
            }
        }
    }

    /** 📌 Devuelve accesorios desde Room */
    fun getAccessories(): Flow<List<Accessory>> = accessoryDao.getAllAccessories()

    /** 📌 Actualiza un accesorio en Firebase y Room */
    suspend fun updateAccessory(accessory: Accessory): Boolean {
        return try {
            if (networkHelper.isOnline()) {
                accessory.firebaseId?.let { id ->
                    firestore.collection("accessories").document(id).set(accessory).await()
                    accessoryDao.updateAccessory(accessory.id, accessory.name, accessory.price, false)
                }
            } else {
                accessoryDao.updateAccessory(accessory.id, accessory.name, accessory.price, true)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 📌 Elimina un accesorio en Firebase y Room */
    suspend fun deleteAccessory(accessory: Accessory) {
        try {
            if (networkHelper.isOnline()) {
                accessory.firebaseId?.let { id ->
                    firestore.collection("accessories").document(id).delete().await()
                    accessoryDao.deleteAccessory(accessory.id)
                }
            } else {
                accessoryDao.markAccessoryAsDeleted(accessory.id)
            }
        } catch (e: Exception) {
            // Log.e("Delete", "Error al eliminar accesorio", e)
        }
    }

    /** 📌 Sincroniza accesorios pendientes (subidas y eliminaciones) */
    suspend fun syncPendingAccessories() {
        if (networkHelper.isOnline()) {
            try {
                // 🔹 Sincroniza eliminaciones
                accessoryDao.getDeletedAccessories()
                    .flatMapMerge { it.asFlow() }
                    .collect { accessory ->
                        try {
                            accessory.firebaseId?.let { id ->
                                firestore.collection("accessories").document(id).delete().await()
                                accessoryDao.deleteAccessory(accessory.id) // ✅ Eliminar solo si se sincroniza
                            }
                        } catch (e: Exception) { /* Log error */ }
                    }
                accessoryDao.clearDeletedAccessories()

                // 🔹 Sincroniza accesorios pendientes
                accessoryDao.getPendingAccessories()
                    .flatMapMerge { it.asFlow() }
                    .collect { accessory ->
                        try {
                            val docRef = firestore.collection("accessories").document()
                            val newAccessory = accessory.copy(firebaseId = docRef.id)
                            docRef.set(newAccessory).await()

                            // ✅ Guarda el `firebaseId` en Room después de subir
                            accessoryDao.updateAccessoryFirebaseId(accessory.id, docRef.id)
                        } catch (e: Exception) { /* Log error */ }
                    }
            } catch (e: Exception) { /* Log error */ }
        }
    }
}
