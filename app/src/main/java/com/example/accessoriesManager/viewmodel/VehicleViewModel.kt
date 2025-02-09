package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import com.example.accessoriesManager.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {
}