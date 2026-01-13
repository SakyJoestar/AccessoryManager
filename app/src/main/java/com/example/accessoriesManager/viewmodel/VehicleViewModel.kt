package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Vehicle
import com.example.accessoriesManager.repository.VehicleRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: VehicleRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<Vehicle>>(emptyList())
    val items: StateFlow<List<Vehicle>> = _items

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var listener: ListenerRegistration? = null

    fun startListening() {
        if (listener != null) return

        listener = repository.listenVehicles(
            onChange = { _items.value = it },
            onError = { _error.value = it.message }
        )
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val previous = _items.value

            // ✅ 1) Quita de la lista ya (UI se actualiza sin recargar)
            _items.value = previous.filterNot { it.id == id }

            // ✅ 2) Borra en Firestore
            runCatching { repository.deleteVehicle(id) }
                .onFailure {
                    // Si falló, restauras la lista y muestras error
                    _items.value = previous
                    _error.value = it.message ?: "No se pudo eliminar"
                }
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
