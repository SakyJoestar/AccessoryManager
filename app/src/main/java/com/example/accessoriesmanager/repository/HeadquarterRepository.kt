package com.example.accessoriesmanager.repository

import com.example.accessoriesmanager.model.Headquarter
import com.google.firebase.firestore.ListenerRegistration

interface HeadquarterRepository {
    suspend fun existsByName(name: String): Boolean
    suspend fun add(headquarter: Headquarter): String
    suspend fun update(id: String, headquarter: Headquarter)
    fun listenHeadquarters(
        onChange: (List<Headquarter>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration
    suspend fun deleteHeadquarter(id: String)
    suspend fun getById(id: String): Headquarter?
    suspend fun existsByNameExcludingId(name: String, excludeId: String): Boolean
    suspend fun getAll(): List<Headquarter>
    suspend fun getIncrement(headquarterId: String): Int
}
