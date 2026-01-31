package com.example.accessoriesManager.ui

import com.example.accessoriesManager.model.Installation
import java.time.LocalDate

enum class StatusFilter { ALL, PAID, UNPAID }

data class InstallationsUiState(
    val all: List<Installation> = emptyList(),

    val query: String = "",
    val stateFilter: String? = null,     // null = todos
    val paidFilter: StatusFilter = StatusFilter.ALL,

    val dateExact: LocalDate? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,

    val expandedIds: Set<String> = emptySet(),

    val isLoading: Boolean = true,
    val error: String? = null
)
