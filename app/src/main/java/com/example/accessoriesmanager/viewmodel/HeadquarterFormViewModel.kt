package com.example.accessoriesmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesmanager.model.Headquarter
import com.example.accessoriesmanager.repository.HeadquarterRepository
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
class HeadquarterFormViewModel @Inject constructor(
    private val repo: HeadquarterRepository
) : ViewModel() {

    private val _form = MutableStateFlow<Headquarter?>(null)
    val form: StateFlow<Headquarter?> = _form

    sealed class UiState {
        data object Idle : UiState()
        data object Checking : UiState()
        data object Saving : UiState()

        data class NameError(val msg: String) : UiState()
        data class IncrementError(val msg: String) : UiState()

        data class Success(val msg: String) : UiState()
        data class Error(val msg: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    // ✅ Evento para cerrar pantalla
    private val _closeScreen = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeScreen: SharedFlow<Unit> = _closeScreen.asSharedFlow()

    fun save(id: String?, nameRaw: String, incrementRaw: String?) {
        val name = nameRaw.trim()

        val increment = incrementRaw?.trim()?.toIntOrNull()
        if (increment == null) {
            _state.value = UiState.IncrementError("Incremento inválido")
            return
        }
        if (increment < 0) {
            _state.value = UiState.IncrementError("Debe ser >= 0")
            return
        }

        if (name.isBlank()) {
            _state.value = UiState.NameError("Obligatorio")
            return
        }

        viewModelScope.launch {
            try {
                _state.value = UiState.Checking

                val conflict = if (id.isNullOrBlank()) {
                    repo.existsByName(name)
                } else {
                    repo.existsByNameExcludingId(name, id)
                }

                if (conflict) {
                    _state.value = UiState.NameError("Ya existe una sede con ese nombre")
                    return@launch
                }

                _state.value = UiState.Saving

                val hq = Headquarter(
                    name = name,
                    increment = increment
                )

                if (id.isNullOrBlank()) {
                    repo.add(hq)
                } else {
                    repo.update(id, hq)
                }

                _state.value = UiState.Success(
                    if (id.isNullOrBlank())
                        "Sede guardada correctamente ✅"
                    else
                        "Sede actualizada ✅"
                )

                delay(300) // 👈 demora suave
                _closeScreen.tryEmit(Unit)

            } catch (e: Exception) {
                _state.value = UiState.Error(
                    e.message ?: "Error guardando sede"
                )
            }
        }
    }

    fun loadById(id: String) = viewModelScope.launch {
        try {
            _state.value = UiState.Checking
            val hq = repo.getById(id)
            if (hq != null) {
                _form.value = hq
                _state.value = UiState.Idle
            } else {
                _state.value = UiState.Error("No se encontró la sede")
            }
        } catch (e: Exception) {
            _state.value = UiState.Error("Error cargando sede")
        }
    }

    fun resetState() {
        _state.value = UiState.Idle
    }
}