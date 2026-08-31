package com.example.accessoriesmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesmanager.model.Vehicle
import com.example.accessoriesmanager.repository.VehicleRepository
import com.example.accessoriesmanager.ui.FormUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VehicleFormViewModel @Inject constructor(
    private val repository: VehicleRepository
) : ViewModel() {

    // Para rellenar el form en modo edición
    private val _form = MutableStateFlow<Vehicle?>(null)
    val form: StateFlow<Vehicle?> = _form

    private val _state = MutableStateFlow<FormUiState>(FormUiState.Idle)
    val state: StateFlow<FormUiState> = _state

    // ✅ Evento para cerrar pantalla (one-shot)
    private val _closeScreen = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeScreen: SharedFlow<Unit> = _closeScreen.asSharedFlow()

    fun loadById(id: String) {
        viewModelScope.launch {
            runCatching { repository.getById(id) }
                .onSuccess { _form.value = it }
                .onFailure { _state.value = FormUiState.Error(it.message ?: "Error cargando vehículo") }
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
                _state.value = FormUiState.FieldError("make", "La marca es obligatoria")
                return@launch
            }
            if (model.isBlank()) {
                _state.value = FormUiState.FieldError("model", "El modelo es obligatorio")
                return@launch
            }

            _state.value = FormUiState.Checking

            // ✅ Validar duplicados (make + model)
            val exists = runCatching {
                if (id.isNullOrBlank()) {
                    repository.existsByMakeAndModel(make, model)
                } else {
                    repository.existsByMakeAndModelExcludingId(make, model, id)
                }
            }.getOrElse {
                _state.value = FormUiState.Error(it.message ?: "Error verificando duplicados")
                return@launch
            }

            if (exists) {
                _state.value = FormUiState.FieldError("model", "Ya existe ese vehículo (marca + modelo)")
                return@launch
            }

            _state.value = FormUiState.Saving

            // ✅ Guardar o actualizar
            val vehicle = Vehicle(
                id = id,     // si tu data class usa String? está ok
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
                _state.value = FormUiState.Success(
                    if (id.isNullOrBlank()) "Vehículo guardado ✅" else "Vehículo actualizado ✅"
                )

                delay(300) // 👈 demora suave para que se vea el mensaje
                _closeScreen.tryEmit(Unit)

            } else {
                _state.value = FormUiState.Error(
                    result.exceptionOrNull()?.message ?: "Error guardando vehículo"
                )
            }
        }
    }

    fun resetState() {
        _state.value = FormUiState.Idle
    }
}
