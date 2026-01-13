package com.example.accessoriesManager.repository

import com.example.accessoriesManager.model.Headquarter
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class HeadquarterRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun existsByName(name: String): Boolean {
        val snap = firestore.collection("headquarters")
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

        firestore.collection("headquarters")
            .add(data)
            .await()
    }

    suspend fun update(id: String, headquarter: Headquarter) {
        val updates = hashMapOf(
            "name" to headquarter.name,
            "increment" to headquarter.increment,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("headquarters")
            .document(id)
            .update(updates)
            .await()
    }


    fun listenHeadquarters(
        onChange: (List<Headquarter>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection("headquarters")
            .orderBy("name")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                val list = snapshot
                    ?.toObjects(Headquarter::class.java)
                    .orEmpty()

                onChange(list)
            }
    }

    suspend fun deleteHeadquarter(id: String) {
        firestore.collection("headquarters").document(id).delete().await()
    }

    suspend fun getById(id: String): Headquarter? {
        val doc = firestore.collection("headquarters").document(id).get().await()
        return doc.toObject(Headquarter::class.java)?.apply { this.id = doc.id }
    }

    suspend fun existsByNameExcludingId(name: String, excludeId: String): Boolean {
        val snap = firestore.collection("headquarters")
            .whereEqualTo("name", name)
            .limit(5)
            .get()
            .await()

        // Si hay alguno con ese nombre y su id != excludeId => conflicto
        return snap.documents.any { it.id != excludeId }
    }
}
