package com.example.accessoriesManager.repository

import com.example.accessoriesManager.data.AccessoryDao
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.util.NetworkHelper
import com.google.firebase.auth.FirebaseAuth
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

    suspend fun addAccessory(accessory: Accessory) {
        try {
            if (networkHelper.isOnline()) {
                firestore.collection("accessories").document(accessory.id.toString()).set(accessory)
            } else {
                accessory.isPendingSync = true
            }
        } catch (e: Exception) {
            accessory.isPendingSync = true
        }
        accessoryDao.insertAccessory(accessory) // Siempre guardarlo en Room
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