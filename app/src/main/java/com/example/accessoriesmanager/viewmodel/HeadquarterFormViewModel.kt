package com.example.accessoriesmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesmanager.model.Headquarter
import com.example.accessoriesmanager.repository.HeadquarterRepository
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
class HeadquarterFormViewModel @Inject constructor(
    private val repo: HeadquarterRepository
) : ViewModel() {

    private val _form = MutableStateFlow<Headquarter?>(null)
    val form: StateFlow<Headquarter?> = _form

    private val _state = MutableStateFlow<FormUiState>(FormUiState.Idle)
    val state: StateFlow<FormUiState> = _state

    // ✅ Evento para cerrar pantalla
    private val _closeScreen = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeScreen: SharedFlow<Unit> = _closeScreen.asSharedFlow()

    fun save(id: String?, nameRaw: String, incrementRaw: String?) {
        val name = nameRaw.trim()

        val increment = incrementRaw?.trim()?.toIntOrNull()
        if (increment == null) {
            _state.value = FormUiState.FieldError("increment", "Incremento inválido")
            return
        }
        if (increment < 0) {
            _state.value = FormUiState.FieldError("increment", "Debe ser >= 0")
            return
        }

        if (name.isBlank()) {
            _state.value = FormUiState.FieldError("name", "Obligatorio")
            return
        }

        viewModelScope.launch {
            try {
                _state.value = FormUiState.Checking

                val conflict = if (id.isNullOrBlank()) {
                    repo.existsByName(name)
                } else {
                    repo.existsByNameExcludingId(name, id)
                }

                if (conflict) {
                    _state.value = FormUiState.FieldError("name", "Ya existe una sede con ese nombre")
                    return@launch
                }

                _state.value = FormUiState.Saving

                val hq = Headquarter(
                    name = name,
                    increment = increment
                )

                if (id.isNullOrBlank()) {
                    repo.add(hq)
                } else {
                    repo.update(id, hq)
                }

                _state.value = FormUiState.Success(
                    if (id.isNullOrBlank())
                        "Sede guardada correctamente ✅"
                    else
                        "Sede actualizada ✅"
                )

                delay(300) // 👈 demora suave
                _closeScreen.tryEmit(Unit)

            } catch (e: Exception) {
                _state.value = FormUiState.Error(
                    e.message ?: "Error guardando sede"
                )
            }
        }
    }

    fun loadById(id: String) = viewModelScope.launch {
        try {
            _state.value = FormUiState.Checking
            val hq = repo.getById(id)
            if (hq != null) {
                _form.value = hq
                _state.value = FormUiState.Idle
            } else {
                _state.value = FormUiState.Error("No se encontró la sede")
            }
        } catch (e: Exception) {
            _state.value = FormUiState.Error("Error cargando sede")
        }
    }

    fun resetState() {
        _state.value = FormUiState.Idle
    }
}