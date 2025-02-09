package com.example.accessoriesManager.repository

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
}