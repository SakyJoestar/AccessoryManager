package com.example.accessoriesmanager.model

import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun Installation.matchesQuery(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true

    val qLower = q.lowercase()

    fun contains(value: String?): Boolean =
        value?.trim()?.lowercase()?.contains(qLower) == true

    // directos
    if (order?.toString()?.contains(q, ignoreCase = true) == true) return true
    if (contains(plate)) return true
    if (contains(serie)) return true

    // ✅ vehicle/headquarter retrocompatibles (String o Map)
    if (contains(vehicleLabelFromAny(vehicle))) return true
    if (contains(headquarterLabelFromAny(headquarter))) return true

    // accesorios
    val acc = accessories.orEmpty()
    if (acc.any { contains(it.name) }) return true
    if (acc.any { contains(it.accessoryId) }) return true

    return false
}

fun Installation.matchesStatus(statusUi: String): Boolean {
    val s = state?.trim().orEmpty()

    fun eq(vararg values: String): Boolean =
        values.any { it.equals(s, ignoreCase = true) }

    return when (statusUi.trim()) {
        "Pagado" -> eq("PAGADO", "Pagado", "PAID", "paid")
        "No Pagado" -> eq("NO_PAGADO", "No pagado", "NO PAGADO", "UNPAID", "unpaid")
        "Parcial" -> eq("PARCIAL", "Parcial", "INCOMPLETO", "Incompleto", "INCOMPLETE", "incomplete")
        else -> true
    }
}

fun Installation.matchesDates(
    exact: LocalDate?,
    from: LocalDate?,
    to: LocalDate?
): Boolean {
    if (exact == null && from == null && to == null) return true

    val d = date.toLocalDate() ?: return false

    if (exact != null) return d.isEqual(exact)
    if (from != null && d.isBefore(from)) return false
    if (to != null && d.isAfter(to)) return false

    return true
}

fun Installation.isPaidState(): Boolean {
    val s = state?.trim().orEmpty()
    return s.equals("PAGADO", true) || s.equals("paid", true) || s.equals("Pagado", true)
}

fun Installation.isUnpaidState(): Boolean {
    val s = state?.trim().orEmpty()
    return s.equals("NO_PAGADO", true) || s.equals("unpaid", true) || s.equals("No pagado", true)
}

fun Installation.isPartialState(): Boolean {
    val s = state?.trim().orEmpty()
    return s.equals("PARCIAL", true) || s.equals("incompleto", true) || s.equals("incomplete", true)
}

fun Timestamp?.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
    if (this == null) return null
    return Instant.ofEpochSecond(seconds, nanoseconds.toLong())
        .atZone(zone)
        .toLocalDate()
}
