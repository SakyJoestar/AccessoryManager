package com.example.accessoriesmanager.repository

import com.example.accessoriesmanager.model.Accessory
import com.google.firebase.firestore.ListenerRegistration

interface AccessoryRepository {
    suspend fun existsByName(name: String): Boolean
    suspend fun add(accessory: Accessory): String
    suspend fun update(id: String, accessory: Accessory)
    fun listenAccessories(
        onChange: (List<Accessory>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration
    suspend fun deleteAccessory(id: String)
    suspend fun getById(id: String): Accessory?
    suspend fun existsByNameExcludingId(name: String, excludeId: String): Boolean
    suspend fun getAll(): List<Accessory>
}
