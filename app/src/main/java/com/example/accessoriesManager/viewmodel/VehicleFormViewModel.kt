package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Vehicle
import com.example.accessoriesManager.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleFormViewModel @Inject constructor(
    private val repository: VehicleRepository
) : ViewModel() {

    // Para rellenar el form en modo edición
    private val _form = MutableStateFlow<Vehicle?>(null)
    val form: StateFlow<Vehicle?> = _form

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    sealed class UiState {
        data object Idle : UiState()
        data object Checking : UiState()
        data object Saving : UiState()

        data class MakeError(val msg: String) : UiState()
        data class ModelError(val msg: String) : UiState()

        data class Success(val msg: String) : UiState()
        data class Error(val msg: String) : UiState()
    }

    fun loadById(id: String) {
        viewModelScope.launch {
            runCatching { repository.getById(id) }
                .onSuccess { _form.value = it }
                .onFailure { _state.value = UiState.Error(it.message ?: "Error cargando vehículo") }
        }
    }

    fun save(
        id: String?,
        makeRaw: String,
        modelRaw: String
    ) {
        viewModelScope.launch {
            val make = makeRaw.trim()
            val model = modelRaw.trim()

            // ✅ Validaciones básicas
            if (make.isBlank()) {
                _state.value = UiState.MakeError("La marca es obligatoria")
                return@launch
            }
            if (model.isBlank()) {
                _state.value = UiState.ModelError("El modelo es obligatorio")
                return@launch
            }

            _state.value = UiState.Checking

            // ✅ Validar duplicados (make + model)
            val exists = runCatching {
                if (id.isNullOrBlank()) {
                    repository.existsByMakeAndModel(make, model)
                } else {
                    repository.existsByMakeAndModelExcludingId(make, model, id)
                }
            }.getOrElse {
                _state.value = UiState.Error(it.message ?: "Error verificando duplicados")
                return@launch
            }

            if (exists) {
                _state.value = UiState.ModelError("Ya existe ese vehículo (marca + modelo)")
                return@launch
            }

            _state.value = UiState.Saving

            // ✅ Guardar o actualizar
            val vehicle = Vehicle(
                id = id,
                make = make,
                model = model
            )

            val result = runCatching {
                if (id.isNullOrBlank()) {
                    repository.add(vehicle)
                } else {
                    repository.update(id, vehicle)
                }
            }

            if (result.isSuccess) {
                _state.value = UiState.Success(
                    if (id.isNullOrBlank()) "Vehículo guardado ✅" else "Vehículo actualizado ✅"
                )
            } else {
                _state.value = UiState.Error(result.exceptionOrNull()?.message ?: "Error guardando vehículo")
            }
        }
    }

    fun resetState() {
        _state.value = UiState.Idle
    }
}
