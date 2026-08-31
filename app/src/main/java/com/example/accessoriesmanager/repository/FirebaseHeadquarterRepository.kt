package com.example.accessoriesmanager.repository

import com.example.accessoriesmanager.model.Headquarter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

class FirebaseHeadquarterRepository @Inject constructor(
    firestore: FirebaseFirestore,
    auth: FirebaseAuth
) : BaseRepository(firestore, auth), HeadquarterRepository {

    // ✅ Ruta por usuario: /users/{uid}/headquarters
    private fun headquartersRef() = userCollection("headquarters")

    override suspend fun existsByName(name: String): Boolean {
        val snap = headquartersRef()
            .whereEqualTo("name", name)
            .limit(1)
            .get()
            .await()

        return !snap.isEmpty
    }

    override suspend fun add(headquarter: Headquarter): String {
        val data = hashMapOf(
            "name" to headquarter.name,
            "increment" to headquarter.increment,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val ref = headquartersRef().add(data).await()
        return ref.id
    }

    override suspend fun update(id: String, headquarter: Headquarter) {
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

    override fun listenHeadquarters(
        onChange: (List<Headquarter>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {

        val collator = Collator.getInstance(Locale("es", "ES"))

        return headquartersRef()
            .orderBy("name")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                val list = snapshot
                    ?.documents
                    ?.mapNotNull { doc ->
                        doc.toObject(Headquarter::class.java)?.apply {
                            this.id = doc.id
                        }
                    }
                    .orEmpty()

                val sorted = list.sortedWith { a, b ->
                    collator.compare(a.name?.trim(), b.name?.trim())
                }

                onChange(sorted)
            }
    }

    override suspend fun deleteHeadquarter(id: String) {
        headquartersRef()
            .document(id)
            .delete()
            .await()
    }

    override suspend fun getById(id: String): Headquarter? {
        val doc = headquartersRef()
            .document(id)
            .get()
            .await()

        return doc.toObject(Headquarter::class.java)?.apply {
            this.id = doc.id
        }
    }

    override suspend fun existsByNameExcludingId(name: String, excludeId: String): Boolean {
        val snap = headquartersRef()
            .whereEqualTo("name", name)
            .limit(5)
            .get()
            .await()

        return snap.documents.any { it.id != excludeId }
    }

    override suspend fun getAll(): List<Headquarter> {
        val snap = headquartersRef()
            .orderBy("name")
            .get()
            .await()

        return snap.documents.mapNotNull { doc ->
            doc.toObject(Headquarter::class.java)?.apply {
                this.id = doc.id
            }
        }
    }

    override suspend fun getIncrement(headquarterId: String): Int {
        return try {
            val snap = userCollection("installations")
                .whereEqualTo("headquarterId", headquarterId) // ✅
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            val doc = snap.documents.firstOrNull() ?: return 0
            (doc.getLong("increment") ?: 0L).toInt()

        } catch (e: Exception) {
            android.util.Log.e("HeadquarterRepo", "getIncrement failed", e)
            0
        }
    }
}
