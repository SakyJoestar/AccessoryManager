package com.example.accessoriesManager.repository

import com.example.accessoriesManager.model.Installation
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstallationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")

    private fun col() =
        firestore.collection("users").document(uid()).collection("installations")

    // -------------------- CRUD --------------------

    suspend fun getById(id: String): Installation? {
        val snap = col().document(id).get().await()
        return snap.toObject(Installation::class.java)?.copy(id = snap.id)
    }

    /**
     * Crea un nuevo documento. Si installation.id viene null, Firestore genera uno.
     * createdAt/updatedAt se setean aquí si vienen null.
     */
    suspend fun create(installation: Installation): String {
        val now = Timestamp.now()

        val docRef = if (installation.id.isNullOrBlank()) {
            col().document() // auto-id
        } else {
            col().document(installation.id!!)
        }

        val data = installation.copy(
            id = null, // 👈 no guardar el id como campo (id = docId)
            createdAt = installation.createdAt ?: now,
            updatedAt = now
        )

        docRef.set(data).await()
        return docRef.id
    }

    /**
     * Actualiza un documento existente por id.
     * updatedAt se refresca automáticamente.
     */
    suspend fun update(id: String, installation: Installation) {
        val now = Timestamp.now()

        // Mantén createdAt si ya viene en el objeto; si no, lo respetamos como está en Firestore
        // (si quieres, aquí podríamos leer current y preservarlo)
        val data = installation.copy(
            id = null,
            updatedAt = now
        )

        col().document(id).set(data).await()
    }

    suspend fun delete(id: String) {
        col().document(id).delete().await()
    }

    // -------------------- LISTEN (opcional pero recomendado) --------------------

    fun listenAll(
        onChange: (List<Installation>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return col()
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Installation::class.java)?.copy(id = doc.id)
                }.orEmpty()

                onChange(list)
            }
    }
}
