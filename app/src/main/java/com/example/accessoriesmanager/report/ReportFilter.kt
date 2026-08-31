package com.example.accessoriesmanager.report

import java.time.LocalDate

sealed class ReportRange {
    data object All : ReportRange()
    data class Range(val from: LocalDate, val to: LocalDate) : ReportRange()
    data class Month(val year: Int, val month: Int) : ReportRange() // month 1..12
}

/** [status] uses the same UI values as [com.example.accessoriesmanager.model.matchesStatus]. */
data class ReportFilter(
    val range: ReportRange,
    val status: String
)
