package com.example.accessoriesmanager.repository

interface AuthRepository {
    suspend fun login(email: String, password: String)
    suspend fun register(name: String, email: String, password: String)
    suspend fun loginWithGoogle(idToken: String)
}