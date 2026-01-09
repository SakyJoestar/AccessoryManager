package com.example.accessoriesManager.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    override suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun register(name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).await()

        val user = auth.currentUser ?: error("No se pudo obtener el usuario actual")
        val uid = user.uid

        val userData = hashMapOf(
            "name" to name,
            "email" to email,
            "createdAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("users")
            .document(uid)
            .set(userData)
            .await()
    }

    override suspend fun loginWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()

        val user = auth.currentUser ?: error("Usuario de Firebase nulo")
        val uid = user.uid

        val userData = hashMapOf(
            "name" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "photoUrl" to (user.photoUrl?.toString()),
            "lastLogin" to FieldValue.serverTimestamp()
        )

        firestore.collection("users")
            .document(uid)
            .set(userData, SetOptions.merge())
            .await()
    }
}
