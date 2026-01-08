package com.example.accessoriesManager.model

sealed class AuthStatus {
    data object Idle : AuthStatus()
    data object Loading : AuthStatus()
    data class Success(val message: String) : AuthStatus()
    data class Error(val message: String) : AuthStatus()
}
