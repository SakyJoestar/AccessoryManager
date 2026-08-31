package com.example.accessoriesmanager.ui

/** Shared save/load state for the Accessory, Headquarter, Vehicle and Installation forms. */
sealed class FormUiState {
    data object Idle : FormUiState()
    data object Checking : FormUiState()
    data object Saving : FormUiState()

    /** [field] identifies which input the error belongs to (e.g. "name", "price"). */
    data class FieldError(val field: String, val msg: String) : FormUiState()

    data class Success(val msg: String) : FormUiState()
    data class Error(val msg: String) : FormUiState()
}
