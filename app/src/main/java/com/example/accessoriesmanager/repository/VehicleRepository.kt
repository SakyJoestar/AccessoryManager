package com.example.accessoriesmanager.repository

import com.example.accessoriesmanager.model.Vehicle
import com.google.firebase.firestore.ListenerRegistration

interface VehicleRepository {
    suspend fun existsByMakeAndModel(make: String, model: String): Boolean
    suspend fun add(vehicle: Vehicle): String
    suspend fun update(id: String, vehicle: Vehicle)
    fun listenVehicles(
        onChange: (List<Vehicle>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration
    suspend fun deleteVehicle(id: String)
    suspend fun getById(id: String): Vehicle?
    suspend fun existsByMakeAndModelExcludingId(make: String, model: String, excludeId: String): Boolean
    suspend fun getAll(): List<Vehicle>
}
