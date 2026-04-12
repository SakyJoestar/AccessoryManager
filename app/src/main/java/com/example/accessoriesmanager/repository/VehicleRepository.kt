package com.example.accessoriesmanager.repository

import com.example.accessoriesmanager.model.Vehicle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

class VehicleRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {

    // 🔐 UID obligatorio
    private fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("No hay usuario autenticado")

    // 📍 Ruta: /users/{uid}/vehicles
    private fun vehiclesRef() =
        firestore.collection("users")
            .document(requireUid())
            .collection("vehicles")

    // ✅ Validar si ya existe un vehículo por (make + model)
    suspend fun existsByMakeAndModel(make: String, model: String): Boolean {
        val snap = vehiclesRef()
            .whereEqualTo("make", make)
            .whereEqualTo("model", model)
            .limit(1)
            .get()
            .await()

        return !snap.isEmpty
    }

    // ✅ Crear vehículo
    suspend fun add(vehicle: Vehicle): String {
        val data = hashMapOf(
            "make" to vehicle.make,
            "model" to vehicle.model,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val ref = vehiclesRef().add(data).await()
        return ref.id
    }

    // ✅ Actualizar vehículo
    suspend fun update(id: String, vehicle: Vehicle) {
        val updates = hashMapOf(
            "make" to vehicle.make,
            "model" to vehicle.model,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        vehiclesRef()
            .document(id)
            .update(updates)
            .await()
    }

    fun listenVehicles(
        onChange: (List<Vehicle>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val collator = Collator.getInstance(Locale("es", "ES"))

        return vehiclesRef()
            .orderBy("model")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                val list = snapshot
                    ?.documents
                    ?.mapNotNull { doc ->
                        doc.toObject(Vehicle::class.java)?.apply { this.id = doc.id }
                    }
                    .orEmpty()

                val sorted = list.sortedWith { a, b ->
                    collator.compare(a.model?.trim(), b.model?.trim())
                }

                onChange(sorted)
            }
    }

    // ✅ Eliminar
    suspend fun deleteVehicle(id: String) {
        vehiclesRef()
            .document(id)
            .delete()
            .await()
    }

    // ✅ Obtener por ID
    suspend fun getById(id: String): Vehicle? {
        val doc = vehiclesRef()
            .document(id)
            .get()
            .await()

        return doc.toObject(Vehicle::class.java)?.apply {
            this.id = doc.id
        }
    }

    // ✅ Validar duplicado excluyendo ID (para edición)
    suspend fun existsByMakeAndModelExcludingId(
        make: String,
        model: String,
        excludeId: String
    ): Boolean {
        val snap = vehiclesRef()
            .whereEqualTo("make", make)
            .whereEqualTo("model", model)
            .limit(5)
            .get()
            .await()

        return snap.documents.any { it.id != excludeId }
    }

    suspend fun getAll(): List<Vehicle> {
        val snap = vehiclesRef()
            .orderBy("make")
            .orderBy("model")
            .get()
            .await()

        return snap.documents.mapNotNull { doc ->
            doc.toObject(Vehicle::class.java)?.apply {
                this.id = doc.id
            }
        }
    }
}
