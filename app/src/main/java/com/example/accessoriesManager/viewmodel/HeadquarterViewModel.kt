package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Headquarter
import com.example.accessoriesManager.repository.HeadquarterRepository
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
class HeadquarterViewModel @Inject constructor(
    private val repository: HeadquarterRepository
) : ViewModel() {

    // ---------- LISTA ----------
    private val _items = MutableStateFlow<List<Headquarter>>(emptyList())
    val items: StateFlow<List<Headquarter>> = _items

    // ---------- ERROR GENERAL ----------
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // ---------- EVENTOS DE CREACIÓN ----------
    sealed class CreateEvent {
        data class Success(val headquarter: Headquarter) : CreateEvent()
        data class Error(val msg: String) : CreateEvent()
    }

    private val _createEvents = MutableSharedFlow<CreateEvent>(extraBufferCapacity = 1)
    val createEvents: SharedFlow<CreateEvent> = _createEvents.asSharedFlow()

    private var listener: ListenerRegistration? = null

    // ---------- LISTENER ----------
    fun startListening() {
        if (listener != null) return

        listener = repository.listenHeadquarters(
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
                repository.deleteHeadquarter(id)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    // ---------- CREATE ----------
    fun createHeadquarter(name: String, increment: Long) {
        viewModelScope.launch {
            val cleanName = name.trim()

            if (cleanName.isBlank()) {
                _createEvents.tryEmit(CreateEvent.Error("El nombre es obligatorio"))
                return@launch
            }

            if (increment < 0) {
                _createEvents.tryEmit(CreateEvent.Error("El incremento no puede ser negativo"))
                return@launch
            }

            try {
                if (repository.existsByName(cleanName)) {
                    _createEvents.emit(CreateEvent.Error("Ya existe una sede con ese nombre"))
                    return@launch
                }

                val createdId = repository.add(
                    Headquarter(
                        id = "",
                        name = cleanName,
                        increment = increment.toInt()
                    )
                )

                _createEvents.emit(
                    CreateEvent.Success(
                        Headquarter(
                            id = createdId,
                            name = cleanName,
                            increment = increment.toInt()
                        )
                    )
                )

            } catch (e: Exception) {
                _createEvents.emit(
                    CreateEvent.Error(e.message ?: "Error creando sede")
                )
            }
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
