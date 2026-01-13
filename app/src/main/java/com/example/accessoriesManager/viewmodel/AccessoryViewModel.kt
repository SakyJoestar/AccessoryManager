package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.repository.AccessoryRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccessoryViewModel @Inject constructor(
    private val repository: AccessoryRepository
) : ViewModel() {

    private val _items = MutableStateFlow<List<Accessory>>(emptyList())
    val items: StateFlow<List<Accessory>> = _items

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var listener: ListenerRegistration? = null

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

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repository.deleteAccessory(id) }
                .onFailure { _error.value = it.message }
        }
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
