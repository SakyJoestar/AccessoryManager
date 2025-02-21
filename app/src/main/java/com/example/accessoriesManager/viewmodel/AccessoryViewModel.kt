package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.repository.AccessoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccessoryViewModel @Inject constructor(
    private val accessoryRepository: AccessoryRepository
) : ViewModel() {

    private val _accessoriesList = MutableStateFlow<List<Accessory>>(emptyList())
    val accessoriesList: StateFlow<List<Accessory>> get() = _accessoriesList

    private val _progressState = MutableStateFlow(false)
    val progressState: StateFlow<Boolean> get() = _progressState

    // ✅ Obtener la lista de accesorios
    fun getAccessoriesList() {
        viewModelScope.launch {
            _progressState.value = true
            try {
                accessoryRepository.getAccessories()
                    .collect { accessories ->
                        _accessoriesList.value = accessories
                    }
            } finally {
                _progressState.value = false
            }
        }
    }

    // ✅ Agregar accesorio
    fun saveAccessory(accessory: Accessory) {
        viewModelScope.launch {
            _progressState.value = true
            try {
                accessoryRepository.addAccessory(accessory)
                getAccessoriesList() // Recargar lista
            } finally {
                _progressState.value = false
            }
        }
    }

    // ✅ Eliminar accesorio
    fun deleteAccessory(accessory: Accessory) {
        viewModelScope.launch {
            _progressState.value = true
            try {
                accessoryRepository.deleteAccessory(accessory)
                getAccessoriesList() // Recargar lista
            } finally {
                _progressState.value = false
            }
        }
    }
}
