package com.example.accessoriesManager.repository

import com.example.accessoriesManager.model.Accessory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AccessoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // ✅ Ruta por usuario: /users/{uid}/accessories
    private fun accessoriesRef() =
        firestore.collection("users")
            .document(requireUid())
            .collection("accessories")

    private fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("No hay usuario autenticado")

    suspend fun existsByName(name: String): Boolean {
        val snap = accessoriesRef()
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

        accessoriesRef()
            .add(data)
            .await()
    }

    suspend fun update(id: String, accessory: Accessory) {
        val updates = hashMapOf(
            "name" to accessory.name,
            "price" to accessory.price, // Long
            "updatedAt" to FieldValue.serverTimestamp()
        )

        accessoriesRef()
            .document(id)
            .update(updates)
            .await()
    }

    fun listenAccessories(
        onChange: (List<Accessory>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return accessoriesRef()
            .orderBy("name")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                val list = snapshot
                    ?.documents
                    ?.mapNotNull { doc ->
                        doc.toObject(Accessory::class.java)?.apply {
                            this.id = doc.id
                        }
                    }
                    .orEmpty()

                onChange(list)
            }
    }

    suspend fun deleteAccessory(id: String) {
        accessoriesRef()
            .document(id)
            .delete()
            .await()
    }

    suspend fun getById(id: String): Accessory? {
        val doc = accessoriesRef()
            .document(id)
            .get()
            .await()

        return doc.toObject(Accessory::class.java)?.apply {
            this.id = doc.id
        }
    }

    suspend fun existsByNameExcludingId(name: String, excludeId: String): Boolean {
        val snap = accessoriesRef()
            .whereEqualTo("name", name)
            .limit(5)
            .get()
            .await()

        return snap.documents.any { it.id != excludeId }
    }
}
