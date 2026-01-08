package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.AuthStatus
import com.example.accessoriesManager.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository
) : ViewModel() {

    private val _status = MutableStateFlow<AuthStatus>(AuthStatus.Idle)
    val status: StateFlow<AuthStatus> = _status.asStateFlow()

    fun login(email: String, password: String) = viewModelScope.launch {
        _status.value = AuthStatus.Loading
        try {
            repo.login(email, password)
            _status.value = AuthStatus.Success("Inicio de sesión exitoso")
        } catch (e: Exception) {
            _status.value = AuthStatus.Error(e.localizedMessage ?: "Error al iniciar sesión")
        }
    }

    fun register(name: String, email: String, password: String) = viewModelScope.launch {
        _status.value = AuthStatus.Loading
        try {
            repo.register(name, email, password)
            _status.value = AuthStatus.Success("Registro exitoso")
        } catch (e: Exception) {
            _status.value = AuthStatus.Error(e.localizedMessage ?: "Error al registrarse")
        }
    }

    fun loginWithGoogleToken(idToken: String) = viewModelScope.launch {
        _status.value = AuthStatus.Loading
        try {
            repo.loginWithGoogle(idToken)
            _status.value = AuthStatus.Success("Inicio con Google exitoso")
        } catch (e: Exception) {
            _status.value = AuthStatus.Error(e.localizedMessage ?: "Error al iniciar con Google")
        }
    }

    fun resetStatus() {
        _status.value = AuthStatus.Idle
    }
}
