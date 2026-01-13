package com.example.accessoriesManager.repository

import com.example.accessoriesManager.model.Headquarter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class HeadquarterRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // ✅ Ruta por usuario: /users/{uid}/headquarters
    private fun headquartersRef() =
        firestore.collection("users")
            .document(requireUid())
            .collection("headquarters")

    private fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("No hay usuario autenticado")

    suspend fun existsByName(name: String): Boolean {
        val snap = headquartersRef()
            .whereEqualTo("name", name)
            .limit(1)
            .get()
            .await()

        return !snap.isEmpty
    }

    suspend fun add(headquarter: Headquarter) {
        val data = hashMapOf(
            "name" to headquarter.name,
            "increment" to headquarter.increment,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        headquartersRef()
            .add(data)
            .await()
    }

    suspend fun update(id: String, headquarter: Headquarter) {
        val updates = hashMapOf(
            "name" to headquarter.name,
            "increment" to headquarter.increment,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        headquartersRef()
            .document(id)
            .update(updates)
            .await()
    }

    fun listenHeadquarters(
        onChange: (List<Headquarter>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return headquartersRef()
            .orderBy("name")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                // ✅ Para que cada item tenga su id
                val list = snapshot
                    ?.documents
                    ?.mapNotNull { doc ->
                        doc.toObject(Headquarter::class.java)?.apply {
                            this.id = doc.id
                        }
                    }
                    .orEmpty()

                onChange(list)
            }
    }

    suspend fun deleteHeadquarter(id: String) {
        headquartersRef()
            .document(id)
            .delete()
            .await()
    }

    suspend fun getById(id: String): Headquarter? {
        val doc = headquartersRef()
            .document(id)
            .get()
            .await()

        return doc.toObject(Headquarter::class.java)?.apply {
            this.id = doc.id
        }
    }

    suspend fun existsByNameExcludingId(name: String, excludeId: String): Boolean {
        val snap = headquartersRef()
            .whereEqualTo("name", name)
            .limit(5)
            .get()
            .await()

        return snap.documents.any { it.id != excludeId }
    }
}
