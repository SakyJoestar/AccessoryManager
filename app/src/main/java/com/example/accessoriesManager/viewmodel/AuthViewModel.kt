package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.repository.AuthRepository
import com.google.android.gms.common.internal.safeparcel.SafeParcelable.Constructor
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _user = MutableStateFlow<FirebaseUser?>(null)
    val user: StateFlow<FirebaseUser?> get() = _user

    init {
        _user.value = authRepository.getCurrentUser()
    }

    fun signInWithGoogle(credential: AuthCredential) {
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(credential)
            if (result is Resource.Success) {
                _user.value = result.data
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _user.value = null
    }
}