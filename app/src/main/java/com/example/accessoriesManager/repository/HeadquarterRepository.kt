package com.example.accessoriesManager.repository

import com.google.firebase.auth.FirebaseAuth
import javax.inject.Inject

class HeadquarterRepository @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
}