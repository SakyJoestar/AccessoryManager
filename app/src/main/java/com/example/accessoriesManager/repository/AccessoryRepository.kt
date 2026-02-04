package com.example.accessoriesManager.repository

import com.example.accessoriesManager.model.Accessory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.text.Collator
import java.util.Locale
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

    suspend fun add(accessory: Accessory): String {
        val data = hashMapOf(
            "name" to accessory.name,
            "price" to accessory.price,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val ref = accessoriesRef().add(data).await()
        return ref.id
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

        val collator = Collator.getInstance(Locale("es", "ES"))

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

                val sorted = list.sortedWith { a, b ->
                    collator.compare(a.name.trim(), b.name.trim())
                }

                onChange(sorted)
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

    suspend fun getAll(): List<Accessory> {
        val snap = accessoriesRef()
            .orderBy("name")
            .get()
            .await()

        return snap.documents.mapNotNull { doc ->
            doc.toObject(Accessory::class.java)?.apply {
                this.id = doc.id
            }
        }
    }
}
