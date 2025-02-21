package com.example.accessoriesManager.repository

import com.example.accessoriesManager.data.AccessoryDao
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.util.NetworkHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AccessoryRepository @Inject constructor(
    private val accessoryDao: AccessoryDao,
    private val firestore: FirebaseFirestore,
    private val networkHelper: NetworkHelper
) {

    suspend fun addAccessory(accessory: Accessory): Boolean {
        return try {
            if (networkHelper.isOnline()) {
                firestore.collection("accessories").document(accessory.id.toString()).set(accessory)
            } else {
                accessory.isPendingSync = true
            }
            accessoryDao.insertAccessory(accessory)
            true // Indicar que la inserción fue exitosa
        } catch (e: Exception) {
            false // Indicar que hubo un error (por ejemplo, nombre duplicado)
        }
    }


    fun getAccessories(): Flow<List<Accessory>> {
        return if (networkHelper.isOnline()) {
            firestore.collection("accessories").snapshots().map { snapshot ->
                try {
                    val list = snapshot.toObjects(Accessory::class.java)
                    accessoryDao.insertAccessories(list) // Guardar en Room
                    list
                } catch (e: Exception) {
                    emptyList() // Evitar fallos si hay un error en los datos
                }
            }
        } else {
            accessoryDao.getAllAccessories()
        }
    }

    suspend fun updateAccessory(accessory: Accessory): Boolean {
        return try {
            if (networkHelper.isOnline()) {
                // Actualizar en Firebase
                firestore.collection("accessories").document(accessory.id.toString()).set(accessory)
                accessoryDao.updateAccessory(accessory.id, accessory.name, accessory.price, false)
            } else {
                // Marcar como pendiente si está offline
                accessoryDao.updateAccessory(accessory.id, accessory.name, accessory.price, true)
            }
            true
        } catch (e: Exception) {
            false // Indica que hubo un error
        }
    }

//  En la Ui se llama asi
//    val success = repository.updateAccessory(updatedAccessory)
//if (!success) {
//    Toast.makeText(context, "Error al actualizar el accesorio", Toast.LENGTH_SHORT).show()
//}

    suspend fun deleteAccessory(accessory: Accessory) {
        if (networkHelper.isOnline()) {
            firestore.collection("accessories").document(accessory.id.toString()).delete()
        }
        accessoryDao.deleteAccessory(accessory.id)
    }

    suspend fun syncPendingAccessories() {
        if (networkHelper.isOnline()) {
            val pendingAccessories = accessoryDao.getPendingAccessories()
            if (pendingAccessories.isNotEmpty()) {
                val batch = firestore.batch()
                pendingAccessories.forEach { accessory ->
                    val docRef = firestore.collection("accessories").document(accessory.id.toString())
                    batch.set(docRef, accessory)
                }
                batch.commit().await() // Esperar a que termine la subida
                accessoryDao.markAsSynced() // Actualizar en Room en una sola llamada
            }
        }
    }
}