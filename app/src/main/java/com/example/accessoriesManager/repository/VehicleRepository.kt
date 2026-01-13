package com.example.accessoriesManager.repository

import com.example.accessoriesManager.model.Vehicle
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class VehicleRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    // ✅ Validar si ya existe un vehículo por (make + model)
    suspend fun existsByMakeAndModel(make: String, model: String): Boolean {
        val snap = firestore.collection("vehicles")
            .whereEqualTo("make", make)
            .whereEqualTo("model", model)
            .limit(1)
            .get()
            .await()

        return !snap.isEmpty
    }

    // ✅ Crear vehículo (createdAt y updatedAt automáticos)
    suspend fun add(vehicle: Vehicle) {
        val data = hashMapOf(
            "make" to vehicle.make,
            "model" to vehicle.model,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("vehicles")
            .add(data)
            .await()
    }

    // ✅ Actualizar vehículo (updatedAt automático)
    suspend fun update(id: String, vehicle: Vehicle) {
        val updates = hashMapOf(
            "make" to vehicle.make,
            "model" to vehicle.model,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("vehicles")
            .document(id)
            .update(updates)
            .await()
    }

    // ✅ Listener en tiempo real para lista de vehículos
    fun listenVehicles(
        onChange: (List<Vehicle>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection("vehicles")
            .orderBy("make")
            .orderBy("model")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                val list = snapshot
                    ?.toObjects(Vehicle::class.java)
                    .orEmpty()

                onChange(list)
            }
    }

    // ✅ Eliminar
    suspend fun deleteVehicle(id: String) {
        firestore.collection("vehicles")
            .document(id)
            .delete()
            .await()
    }

    // ✅ Obtener por ID (y asegurar que se setee el id del documento)
    suspend fun getById(id: String): Vehicle? {
        val doc = firestore.collection("vehicles")
            .document(id)
            .get()
            .await()

        return doc.toObject(Vehicle::class.java)?.apply { this.id = doc.id }
    }

    // ✅ Validar duplicado por (make + model) excluyendo un id (para editar sin bloquearte)
    suspend fun existsByMakeAndModelExcludingId(make: String, model: String, excludeId: String): Boolean {
        val snap = firestore.collection("vehicles")
            .whereEqualTo("make", make)
            .whereEqualTo("model", model)
            .limit(5)
            .get()
            .await()

        // Si hay alguno con esa combinación y su id != excludeId => conflicto
        return snap.documents.any { it.id != excludeId }
    }
}
