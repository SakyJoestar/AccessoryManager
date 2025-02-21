package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import com.example.accessoriesManager.repository.AccessoryRepository
import com.example.accessoriesManager.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AccessoriesViewModel @Inject constructor(
    private val accesoryRepository: AccessoryRepository
): ViewModel() {
}