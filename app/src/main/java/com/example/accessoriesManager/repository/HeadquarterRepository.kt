package com.example.accessoriesManager.repository

import com.example.accessoriesManager.model.Headquarter
import com.google.firebase.firestore.FirebaseFirestore
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
        firestore.collection("headquarters")
            .add(headquarter)
            .await()
    }
}
