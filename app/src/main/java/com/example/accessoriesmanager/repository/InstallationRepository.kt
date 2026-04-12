package com.example.accessoriesmanager.repository

import com.example.accessoriesmanager.model.Installation
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

    // -------------------- helpers --------------------

    private fun uid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Usuario no autenticado")

    private fun installationsCol() =
        firestore.collection("users")
            .document(uid())
            .collection("installations")

    private fun installedAccessoriesCol(installationId: String) =
        installationsCol()
            .document(installationId)
            .collection("installedAccessories")

    // -------------------- normalización --------------------

    // "" o "   " -> null, timestamps controlados acá
    private fun normalize(
        installation: Installation,
        now: Timestamp,
        keepCreatedAt: Boolean
    ): Installation {
        val normalizedComment = installation.comment
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return installation.copy(
            id = null, // 👈 el id SIEMPRE es el docId
            comment = normalizedComment,
            createdAt = if (keepCreatedAt) installation.createdAt ?: now else installation.createdAt,
            updatedAt = now
        )
    }

    // -------------------- CRUD --------------------

    suspend fun getById(id: String): Installation? {
        val snap = installationsCol().document(id).get().await()
        return snap.toObject(Installation::class.java)?.copy(id = snap.id)
    }

    suspend fun create(installation: Installation): String {
        val now = Timestamp.now()

        val docRef = if (installation.id.isNullOrBlank()) {
            installationsCol().document() // auto-id
        } else {
            installationsCol().document(installation.id!!)
        }

        val data = normalize(
            installation = installation,
            now = now,
            keepCreatedAt = true
        )

        docRef.set(data).await()
        return docRef.id
    }

    suspend fun update(id: String, installation: Installation) {
        val now = Timestamp.now()

        val inc = installation.increment ?: 0L
        val selected = installation.accessories.orEmpty()
            .filter { !it.accessoryId.isNullOrBlank() }

        val totalWorked = selected.sumOf { it.price + inc }
        val totalPaid = selected.filter { it.isPaid }.sumOf { it.price + inc }
        val totalUnpaid = totalWorked - totalPaid

        val newState = when {
            selected.isEmpty() || totalWorked == 0L -> "NO_PAGADO"
            totalPaid == 0L -> "NO_PAGADO"      // ✅ FIX
            totalUnpaid == 0L -> "PAGADO"
            else -> "PARCIAL"
        }


        val data = normalize(
            installation = installation.copy(
                totalWorked = totalWorked,
                totalPaid = totalPaid,
                totalUnpaid = totalUnpaid,
                state = newState
            ),
            now = now,
            keepCreatedAt = false
        )

        installationsCol().document(id).set(data).await()
    }

    suspend fun delete(id: String) {
        installationsCol().document(id).delete().await()
    }

    // -------------------- LISTEN --------------------

    fun listenAll(
        onChange: (List<Installation>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return installationsCol()
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                val list = snap?.documents
                    ?.mapNotNull { doc ->
                        doc.toObject(Installation::class.java)?.copy(id = doc.id)
                    }
                    .orEmpty()

                onChange(list)
            }
    }

    // -------------------- ACCESORIOS / ESTADO --------------------

    /**
     * Marca TODOS los accesorios de una instalación como pagados / no pagados
     */
    suspend fun markAllAccessoriesPaidAndUpdateInstallation(
        installationId: String,
        paid: Boolean
    ) {
        val now = Timestamp.now()
        val instRef = installationsCol().document(installationId)

        val snap = instRef.get().await()
        val inst = snap.toObject(Installation::class.java)
            ?: throw IllegalStateException("Instalación no existe: $installationId")

        val increment = inst.increment ?: 0L
        val current = inst.accessories.orEmpty()

        // ✅ Actualiza SOLO el campo real de Firestore: "paid"
        val updatedAccessories: List<Map<String, Any?>> = current.map { acc ->
            mapOf(
                "accessoryId" to acc.accessoryId,
                "name" to acc.name,
                "price" to acc.price,   // precio base (NO sumes increment aquí)
                "paid" to paid          // 🔥 clave
            )
        }

        fun finalPrice(base: Long) = base + increment

        // ✅ Totales usando precio final = base + increment por cada accesorio
        val totalWorked = current.sumOf { finalPrice(it.price) }

        // ✅ totalPaid basado en flags reales (aquí todos quedan paid=true/false)
        val totalPaid = if (paid) totalWorked else 0L
        val totalUnpaid = totalWorked - totalPaid

        // ✅ Estado consistente con el resto de tu app
        val newState = when {
            totalWorked <= 0L -> "NO_PAGADO"
            totalPaid <= 0L -> "NO_PAGADO"       // ✅ tu caso: 1 accesorio no pagado
            totalUnpaid <= 0L -> "PAGADO"
            else -> "PARCIAL"
        }

        android.util.Log.d(
            "INSTALL_MARK",
            "id=$installationId paid=$paid inc=$increment acc=${updatedAccessories.size} " +
                    "total=$totalWorked paidTotal=$totalPaid unpaid=$totalUnpaid state=$newState"
        )

        instRef.update(
            mapOf(
                "accessories" to updatedAccessories,
                "totalWorked" to totalWorked,
                "totalPaid" to totalPaid,
                "totalUnpaid" to totalUnpaid,
                "state" to newState,
                "updatedAt" to now
            )
        ).await()
    }

}
