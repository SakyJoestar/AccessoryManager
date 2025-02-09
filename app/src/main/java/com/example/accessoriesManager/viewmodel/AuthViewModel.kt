package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import com.example.accessoriesManager.repository.AuthRepository
import com.google.android.gms.common.internal.safeparcel.SafeParcelable.Constructor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
):ViewModel() {
}