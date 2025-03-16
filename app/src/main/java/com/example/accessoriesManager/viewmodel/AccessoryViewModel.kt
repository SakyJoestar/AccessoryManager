package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.repository.AccessoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.firebase.firestore.FieldValue

@HiltViewModel
class AccessoryViewModel @Inject constructor(
    private val repository: AccessoryRepository
) : ViewModel() {

    private val _accessoriesList = MutableLiveData<MutableList<Accessory>>()
    val accessoriesList: LiveData<MutableList<Accessory>> get() = _accessoriesList

    private val _progressState = MutableLiveData(false)
    val progressState: LiveData<Boolean> get() = _progressState

    private fun getAccessoriesList() {
        viewModelScope.launch {
            _progressState.value = true
            try {
                val result = repository.getAccessoriesList()
               if(result.isSuccess) {
                   _accessoriesList.value = result.getOrElse { mutableListOf() }
               } else {
                   _accessoriesList.value = mutableListOf()
               }
                _progressState.value = false
            } catch (e: Exception) {
                _progressState.value = false
            }
        }
    }

    fun saveAccessory(accessory: Accessory) {
        viewModelScope.launch {
            _progressState.value = true
            try {
                accessory.createdAt = FieldValue.serverTimestamp()
                repository.saveAccessory(accessory)
                getAccessoriesList()
                _progressState.value = false
            } catch (e: Exception) {
                _progressState.value = false
            }
            _progressState.value = false
        }
    }

    fun deleteAccessory(accessory: Accessory) {
        viewModelScope.launch {
            _progressState.value = true
            try {
                repository.deleteAccessory(accessory)
                getAccessoriesList()
                _progressState.value = false
            } catch (e: Exception) {
                _progressState.value = false
            }
        }
    }

    fun updateAccessory(accessory: Accessory) {
        viewModelScope.launch {
            _progressState.value = true
            try {
                repository.updateAccessory(accessory)
                getAccessoriesList()
                _progressState.value = false
            } catch (e: Exception) {
                _progressState.value = false
            }
        }
    }
}
