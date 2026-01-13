package com.example.accessoriesManager.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

abstract class BaseRepository(
    protected val db: FirebaseFirestore,
    protected val auth: FirebaseAuth
) {
    protected fun requireUid(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("No hay usuario autenticado")

    protected fun userDoc(): DocumentReference =
        db.collection("users").document(requireUid())

    protected fun userCollection(name: String): CollectionReference =
        userDoc().collection(name)
}
