package com.example.accessoriesManager.repository

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser

    suspend fun signInWithGoogle(credential: AuthCredential): Resource<FirebaseUser?> {
        return try {
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            Resource.Success(authResult.user) // Aquí authResult.user puede ser nulo, así que debe ser FirebaseUser?
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Login failed")
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}