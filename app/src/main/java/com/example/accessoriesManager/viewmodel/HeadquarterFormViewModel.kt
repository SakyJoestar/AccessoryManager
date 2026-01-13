package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Headquarter
import com.example.accessoriesManager.repository.HeadquarterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        data class Success(val msg: String = "Sede guardada correctamente ✅") : UiState()
        data class Error(val msg: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state

    fun save(id: String?, nameRaw: String, incrementRaw: String?) {
        val name = nameRaw.trim()
        val increment = incrementRaw?.trim().orEmpty().toIntOrNull() ?: 0

        if (name.isEmpty()) {
            _state.value = UiState.NameError("Obligatorio")
            return
        }

        viewModelScope.launch {
            try {
                _state.value = UiState.Checking

                val conflict = if (id == null) {
                    repo.existsByName(name)
                } else {
                    repo.existsByNameExcludingId(name, id)
                }

                if (conflict) {
                    _state.value = UiState.NameError("Ya existe una sede con ese nombre")
                    return@launch
                }

                _state.value = UiState.Saving

                val hq = Headquarter(name = name, increment = increment)

                if (id == null) repo.add(hq) else repo.update(id, hq)

                _state.value = UiState.Success(
                    if (id == null) "Sede guardada correctamente ✅" else "Sede actualizada ✅"
                )
                _state.value = UiState.Idle
            } catch (e: Exception) {
                _state.value = UiState.Error("Error: ${e.message ?: "algo salió mal"} 😢")
                _state.value = UiState.Idle
            }
        }
    }

    fun loadById(id: String) = viewModelScope.launch {
        try {
            _state.value = UiState.Checking
            val hq = repo.getById(id)  // <-- este también lo creas en el repo
            if (hq != null) {
                _form.value = hq       // o como guardes los datos del formulario
                _state.value = UiState.Idle
            } else {
                _state.value = UiState.Error("No se encontró la sede")
            }

        } catch (e: Exception) {
            _state.value = UiState.Error("Error cargando sede")
        }
    }
}
