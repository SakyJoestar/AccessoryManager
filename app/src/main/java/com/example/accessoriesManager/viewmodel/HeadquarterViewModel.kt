package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import com.example.accessoriesManager.repository.HeadquarterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HeadquarterViewModel @Inject constructor(
    private val headquarterRepository: HeadquarterRepository
) : ViewModel() {
}