package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Installation
import com.example.accessoriesManager.repository.InstallationRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class InstallationViewModel @Inject constructor(
    private val repo: InstallationRepository
) : ViewModel() {

    // ----- Fuente raw desde Firestore -----
    private val _all = MutableStateFlow<List<Installation>>(emptyList())

    // ----- Error -----
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ----- Filtros -----
    private val _query = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow("Todos") // Todos / Pagado / No Pagado / Parcial
    private val _dateExact = MutableStateFlow<LocalDate?>(null)
    private val _dateFrom = MutableStateFlow<LocalDate?>(null)
    private val _dateTo = MutableStateFlow<LocalDate?>(null)

    // ----- Expand / Collapse -----
    private val _expandedIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedIds: StateFlow<Set<String>> = _expandedIds.asStateFlow()

    // ----- Items filtrados -----
    private val _items = MutableStateFlow<List<Installation>>(emptyList())
    val items: StateFlow<List<Installation>> = _items.asStateFlow()

    private var reg: ListenerRegistration? = null
    private var filterJob: Job? = null

    init {
        filterJob = viewModelScope.launch {
            // pipeline por etapas como tú lo tienes
            val f1 = _all.combine(_query) { all, q -> all to q }
            val f2 = f1.combine(_statusFilter) { (all, q), status -> Triple(all, q, status) }
            val f3 = f2.combine(_dateExact) { t, exact -> Quad(t.first, t.second, t.third, exact) }
            val f4 = f3.combine(_dateFrom) { qd, from -> Quint(qd.a, qd.b, qd.c, qd.d, from) }
            val f5 = f4.combine(_dateTo) { qi, to -> Sext(qi.a, qi.b, qi.c, qi.d, qi.e, to) }

            f5.collect { data ->
                val all = data.a
                val q = data.b
                val status = data.c
                val exact = data.d
                val from = data.e
                val to = data.f

                _items.value = all.asSequence()
                    .filter { it.matchesQuery(q) }
                    .filter { it.matchesStatus(status) }          // ✅ nuevo
                    .filter { it.matchesDates(exact, from, to) }
                    .toList()
            }
        }
    }

    // ------------- Listening -------------

    fun startListening() {
        _error.value = null
        reg?.remove()

        reg = repo.listenAll(
            onChange = { list ->
                android.util.Log.d("INSTALLATIONS_VM", "Firestore trajo ${list.size} instalaciones")
                _all.value = list
            },
            onError = { e ->
                android.util.Log.e("INSTALLATIONS_VM", "Error Firestore", e)
                _error.value = e.message
            }
        )
    }

    fun stopListening() {
        reg?.remove()
        reg = null
    }

    // ------------- CRUD -------------

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { repo.delete(id) }
                .onFailure { _error.value = it.message ?: "No se pudo eliminar" }
        }
    }

    // ------------- Expand -------------

    fun toggleExpanded(id: String) {
        _expandedIds.update { set ->
            val m = set.toMutableSet()
            if (!m.add(id)) m.remove(id)
            m
        }
    }

    // ------------- Filtros desde UI -------------

    fun onQueryChanged(q: String) { _query.value = q }

    /** Spinner: "Todos", "Pagado", "No Pagado", "Parcial" */
    fun onStatusFilterChanged(value: String) { _statusFilter.value = value }

    fun setDateExact(d: LocalDate?) {
        _dateExact.value = d
        _dateFrom.value = null
        _dateTo.value = null
    }

    fun setDateRange(from: LocalDate?, to: LocalDate?) {
        _dateFrom.value = from
        _dateTo.value = to
        _dateExact.value = null
    }

    fun clearDates() {
        _dateExact.value = null
        _dateFrom.value = null
        _dateTo.value = null
    }

    fun currentDateFrom(): LocalDate? = _dateFrom.value
    fun currentDateTo(): LocalDate? = _dateTo.value

    // ------------- Mark paid/unpaid -------------

    fun markAllAccessoriesPaid(installationId: String, paid: Boolean) {
        viewModelScope.launch {
            try {
                repo.markAllAccessoriesPaidAndUpdateInstallation(installationId, paid)
                _error.emit(
                    if (paid) "Accesorios marcados como pagados ✅"
                    else "Accesorios marcados como NO pagados ✅"
                )
            } catch (e: Exception) {
                android.util.Log.e("INSTALL_MARK", "Error marcando", e)
                _error.emit("Error: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
        filterJob?.cancel()
    }
}

/* ======= Pequeñas clases para transportar datos ======= */
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
private data class Sext<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)

/* ================= Helpers ================= */

private fun Installation.matchesQuery(q: String): Boolean {
    val query = q.trim()
    if (query.isEmpty()) return true
    return (order?.toString().orEmpty().contains(query, true)) ||
            (serie.orEmpty().contains(query, true)) ||
            (plate.orEmpty().contains(query, true))
}

/**
 * Spinner:
 *  - "Todos" => true
 *  - "Pagado" => state == PAGADO (o "Pagado")
 *  - "No Pagado" => state == NO_PAGADO (o "No pagado")
 *  - "Parcial" => state == PARCIAL (o "Parcial")
 */
private fun Installation.matchesStatus(statusUi: String): Boolean {
    val s = state?.trim().orEmpty()

    fun eq(vararg values: String): Boolean =
        values.any { it.equals(s, ignoreCase = true) }

    return when (statusUi.trim()) {
        "Pagado" -> eq("PAGADO", "Pagado", "PAID", "paid")
        "No Pagado" -> eq("NO_PAGADO", "No pagado", "NO PAGADO", "UNPAID", "unpaid")
        "Parcial" -> eq("PARCIAL", "Parcial", "INCOMPLETO", "Incompleto")
        else -> true // "Todos"
    }
}

private fun Installation.matchesDates(
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

private fun Timestamp?.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
    if (this == null) return null
    return Instant.ofEpochSecond(seconds, nanoseconds.toLong())
        .atZone(zone)
        .toLocalDate()
}
