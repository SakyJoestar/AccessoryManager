package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import com.example.accessoriesManager.repository.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    private val recordRepository: RecordRepository
) : ViewModel() {
}