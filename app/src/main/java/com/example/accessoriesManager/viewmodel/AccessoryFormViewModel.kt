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
class AccessoryFormViewModel @Inject constructor(
    private val repository: AccessoryRepository
) : ViewModel() {

    // Para rellenar el form en modo edición
    private val _form = MutableStateFlow<Accessory?>(null)
    val form: StateFlow<Accessory?> = _form

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    sealed class UiState {
        data object Idle : UiState()
        data object Checking : UiState()
        data object Saving : UiState()

        data class NameError(val msg: String) : UiState()
        data class PriceError(val msg: String) : UiState()

        data class Success(val msg: String) : UiState()
        data class Error(val msg: String) : UiState()
    }

    fun loadById(id: String) {
        viewModelScope.launch {
            runCatching { repository.getById(id) }
                .onSuccess { _form.value = it }
                .onFailure { _state.value = UiState.Error(it.message ?: "Error cargando accesorio") }
        }
    }

    fun save(
        id: String?,
        nameRaw: String,
        priceRaw: String?
    ) {
        viewModelScope.launch {
            val name = nameRaw.trim()
            val priceText = priceRaw?.trim().orEmpty()

            // ✅ Validaciones básicas
            if (name.isBlank()) {
                _state.value = UiState.NameError("El nombre es obligatorio")
                return@launch
            }

            val price = priceText.toLongOrNull()
            if (price == null) {
                _state.value = UiState.PriceError("Precio inválido")
                return@launch
            }
            if (price <= 0L) {
                _state.value = UiState.PriceError("El precio debe ser mayor a 0")
                return@launch
            }

            _state.value = UiState.Checking

            // ✅ Validar duplicados por nombre
            val exists = runCatching {
                if (id.isNullOrBlank()) {
                    repository.existsByName(name)
                } else {
                    repository.existsByNameExcludingId(name, id)
                }
            }.getOrElse {
                _state.value = UiState.Error(it.message ?: "Error verificando duplicados")
                return@launch
            }

            if (exists) {
                _state.value = UiState.NameError("Ya existe un accesorio con ese nombre")
                return@launch
            }

            _state.value = UiState.Saving

            // ✅ Guardar o actualizar
            val accessory = Accessory(
                id = id ?: "",
                name = name,
                price = price
            )

            val result = runCatching {
                if (id.isNullOrBlank()) {
                    repository.add(accessory)
                } else {
                    repository.update(id, accessory)
                }
            }

            if (result.isSuccess) {
                _state.value = UiState.Success(
                    if (id.isNullOrBlank()) "Accesorio guardado ✅" else "Accesorio actualizado ✅"
                )
            } else {
                _state.value = UiState.Error(result.exceptionOrNull()?.message ?: "Error guardando accesorio")
            }
        }
    }

    fun resetState() {
        _state.value = UiState.Idle
    }
}
