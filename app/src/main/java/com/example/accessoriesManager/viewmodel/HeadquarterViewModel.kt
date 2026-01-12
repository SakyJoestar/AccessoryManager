package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Headquarter
import com.example.accessoriesManager.repository.HeadquarterRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HeadquarterViewModel @Inject constructor(
    private val repository: HeadquarterRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<Headquarter>>(emptyList())
    val items: StateFlow<List<Headquarter>> = _items

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var listener: ListenerRegistration? = null

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

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteHeadquarter(id) }
                .onFailure { _error.value = it.message }
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
