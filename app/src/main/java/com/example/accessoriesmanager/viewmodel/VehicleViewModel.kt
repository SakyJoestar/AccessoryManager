package com.example.accessoriesmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesmanager.model.Vehicle
import com.example.accessoriesmanager.repository.VehicleRepository
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
class VehicleViewModel @Inject constructor(
    private val repository: VehicleRepository
) : ViewModel() {

    // ---------- LISTA ----------
    private val _items = MutableStateFlow<List<Vehicle>>(emptyList())
    val items: StateFlow<List<Vehicle>> = _items

    // ---------- ERROR GENERAL ----------
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ---------- EVENTOS DE CREACIÓN ----------
    sealed class CreateEvent {
        data class Success(val vehicle: Vehicle) : CreateEvent()
        data class Error(val msg: String) : CreateEvent()
    }

    private val _createEvents = MutableSharedFlow<CreateEvent>(extraBufferCapacity = 1)
    val createEvents: SharedFlow<CreateEvent> = _createEvents.asSharedFlow()

    private var listener: ListenerRegistration? = null

    // ---------- LISTENER ----------
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

    // ---------- CREATE ----------
    fun createVehicle(make: String, model: String) {
        viewModelScope.launch {
            val mk = make.trim()
            val md = model.trim()

            if (mk.isBlank()) {
                _createEvents.tryEmit(CreateEvent.Error("La marca es obligatoria"))
                return@launch
            }
            if (md.isBlank()) {
                _createEvents.tryEmit(CreateEvent.Error("El modelo es obligatorio"))
                return@launch
            }

            try {
                if (repository.existsByMakeAndModel(mk, md)) {
                    _createEvents.emit(CreateEvent.Error("Ya existe ese vehículo (marca y modelo)"))
                    return@launch
                }

                val createdId = repository.add(
                    Vehicle(
                        id = "",
                        make = mk,
                        model = md
                    )
                )

                _createEvents.emit(
                    CreateEvent.Success(
                        Vehicle(
                            id = createdId,
                            make = mk,
                            model = md
                        )
                    )
                )
            } catch (e: Exception) {
                _createEvents.emit(
                    CreateEvent.Error(e.message ?: "Error creando vehículo")
                )
            }
        }
    }

    // ---------- DELETE (optimista) ----------
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
