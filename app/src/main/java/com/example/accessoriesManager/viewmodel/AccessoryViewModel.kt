package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.repository.AccessoryRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccessoryViewModel @Inject constructor(
    private val repository: AccessoryRepository
) : ViewModel() {

    // ---------- LISTA ----------
    private val _items = MutableStateFlow<List<Accessory>>(emptyList())
    val items: StateFlow<List<Accessory>> = _items

    // ---------- ERROR GENERAL ----------
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ---------- EVENTOS DE CREACIÓN ----------
    sealed class CreateEvent {
        data class Success(val accessory: Accessory) : CreateEvent()
        data class Error(val msg: String) : CreateEvent()
    }

    private val _createEvents = MutableSharedFlow<CreateEvent>(extraBufferCapacity = 1)
    val createEvents: SharedFlow<CreateEvent> = _createEvents.asSharedFlow()

    private var listener: ListenerRegistration? = null

    // ---------- LISTENER ----------
    fun startListening() {
        if (listener != null) return

        listener = repository.listenAccessories(
            onChange = { _items.value = it },
            onError = { _error.value = it.message }
        )
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    // ---------- DELETE ----------
    fun delete(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteAccessory(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ---------- CREATE ----------
    fun createAccessory(name: String, price: Long) {
        viewModelScope.launch {
            val cleanName = name.trim()

            // Validaciones
            if (cleanName.isBlank()) {
                _createEvents.tryEmit(CreateEvent.Error("El nombre es obligatorio"))
                return@launch
            }
            if (price <= 0) {
                _createEvents.tryEmit(CreateEvent.Error("El precio debe ser mayor a 0"))
                return@launch
            }

            try {
                // Duplicado
                if (repository.existsByName(cleanName)) {
                    _createEvents.emit(
                        CreateEvent.Error("Ya existe un accesorio con ese nombre")
                    )
                    return@launch
                }

                // Crear en Firestore
                val createdId = repository.add(
                    Accessory(
                        id = "",
                        name = cleanName,
                        price = price
                    )
                )

                // Emitir éxito con objeto real
                _createEvents.emit(
                    CreateEvent.Success(
                        Accessory(
                            id = createdId,
                            name = cleanName,
                            price = price
                        )
                    )
                )

            } catch (e: Exception) {
                _createEvents.emit(
                    CreateEvent.Error(e.message ?: "Error creando accesorio")
                )
            }
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
