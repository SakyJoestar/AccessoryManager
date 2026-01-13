package com.example.accessoriesManager.repository

import com.example.accessoriesManager.model.Accessory
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AccessoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    suspend fun existsByName(name: String): Boolean {
        val snap = firestore.collection("accessories")
            .whereEqualTo("name", name)
            .limit(1)
            .get()
            .await()

        return !snap.isEmpty
    }

    suspend fun add(accessory: Accessory) {
        val data = hashMapOf(
            "name" to accessory.name,
            "price" to accessory.price, // Long
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("accessories")
            .add(data)
            .await()
    }

    suspend fun update(id: String, accessory: Accessory) {
        val updates = hashMapOf(
            "name" to accessory.name,
            "price" to accessory.price, // Long
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("accessories")
            .document(id)
            .update(updates)
            .await()
    }

    fun listenAccessories(
        onChange: (List<Accessory>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection("accessories")
            .orderBy("name")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                val list = snapshot
                    ?.toObjects(Accessory::class.java)
                    .orEmpty()

                onChange(list)
            }
    }

    suspend fun deleteAccessory(id: String) {
        firestore.collection("accessories")
            .document(id)
            .delete()
            .await()
    }

    suspend fun getById(id: String): Accessory? {
        val doc = firestore.collection("accessories")
            .document(id)
            .get()
            .await()

        return doc.toObject(Accessory::class.java)?.apply {
            this.id = doc.id
        }
    }

    suspend fun existsByNameExcludingId(name: String, excludeId: String): Boolean {
        val snap = firestore.collection("accessories")
            .whereEqualTo("name", name)
            .limit(5)
            .get()
            .await()

        return snap.documents.any { it.id != excludeId }
    }
}
