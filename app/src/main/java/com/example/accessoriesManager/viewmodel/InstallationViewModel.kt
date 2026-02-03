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
import java.time.format.DateTimeFormatter
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class InstallationViewModel @Inject constructor(
    private val repo: InstallationRepository
) : ViewModel() {

    lateinit var dateExact: Date

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

    // ----- Items filtrados para la lista -----
    private val _items = MutableStateFlow<List<Installation>>(emptyList())
    val items: StateFlow<List<Installation>> = _items.asStateFlow()

    // ----- Summary -----
    data class SummaryUi(
        val title: String,
        val totalWorked: Long,
        val totalPaid: Long,
        val totalUnpaid: Long,
        val paidCount: Int,
        val partialCount: Int,
        val unpaidCount: Int
    )

    private val _summary = MutableStateFlow(
        SummaryUi(
            title = "Últimos 7 días",
            totalWorked = 0L,
            totalPaid = 0L,
            totalUnpaid = 0L,
            paidCount = 0,
            partialCount = 0,
            unpaidCount = 0
        )
    )
    val summary: StateFlow<SummaryUi> = _summary.asStateFlow()

    private var reg: ListenerRegistration? = null
    private var filterJob: Job? = null

    private val zone: ZoneId = ZoneId.systemDefault()
    private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    init {
        // ✅ Por defecto: últimos 7 días (hoy - 6 ... hoy)
        setDefaultLast7Days()

        filterJob = viewModelScope.launch {
            // ---- LISTA: aplica query + status + fechas ----
            val f1 = _all.combine(_query) { all, q -> all to q }
            val f2 = f1.combine(_statusFilter) { (all, q), status -> Triple(all, q, status) }
            val f3 = f2.combine(_dateExact) { t, exact -> Quad(t.first, t.second, t.third, exact) }
            val f4 = f3.combine(_dateFrom) { qd, from -> Quint(qd.a, qd.b, qd.c, qd.d, from) }
            val f5 = f4.combine(_dateTo) { qi, to -> Sext(qi.a, qi.b, qi.c, qi.d, qi.e, to) }

            // ---- SUMMARY: solo depende de _all + fechas ----
            val s1 = _all.combine(_dateExact) { all, exact -> all to exact }
            val s2 = s1.combine(_dateFrom) { (all, exact), from -> Triple(all, exact, from) }
            val s3 = s2.combine(_dateTo) { (all, exact, from), to -> Triple3(all, exact, from, to) }

            // collector lista
            launch {
                f5.collect { data ->
                    val all = data.a
                    val q = data.b
                    val status = data.c
                    val exact = data.d
                    val from = data.e
                    val to = data.f

                    _items.value = all.asSequence()
                        .filter { it.matchesQuery(q) }
                        .filter { it.matchesStatus(status) }
                        .filter { it.matchesDates(exact, from, to) }
                        .toList()
                }
            }

            // collector summary
            launch {
                s3.collect { data ->
                    val all = data.all
                    val exact = data.exact
                    val from = data.from
                    val to = data.to

                    val inDate = all.asSequence()
                        .filter { it.matchesDates(exact, from, to) }
                        .toList()

                    val title = buildSummaryTitle(exact, from, to)

                    val totalWorked = inDate.sumOf { it.totalWorked ?: 0L }
                    val totalPaid = inDate.sumOf { it.totalPaid ?: 0L }
                    val totalUnpaid = inDate.sumOf { it.totalUnpaid ?: 0L }

                    val paidCount = inDate.count { it.isPaidState() }
                    val unpaidCount = inDate.count { it.isUnpaidState() }
                    val partialCount = inDate.count { it.isPartialState() }

                    _summary.value = SummaryUi(
                        title = title,
                        totalWorked = totalWorked,
                        totalPaid = totalPaid,
                        totalUnpaid = totalUnpaid,
                        paidCount = paidCount,
                        partialCount = partialCount,
                        unpaidCount = unpaidCount
                    )
                }
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

    /**
     * ✅ Te recomiendo que "Limpiar filtros de fecha" vuelva a últimos 7 días,
     * porque tú quieres que ese sea el default del app.
     */
    fun clearDates() {
        setDefaultLast7Days()
    }

    fun currentDateFrom(): LocalDate? = _dateFrom.value
    fun currentDateTo(): LocalDate? = _dateTo.value

    private fun setDefaultLast7Days() {
        val today = LocalDate.now(zone)
        _dateExact.value = null
        _dateFrom.value = today.minusDays(6) // incluye hoy: 7 días
        _dateTo.value = today
    }

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

    // -------------------- Summary title --------------------

    private fun buildSummaryTitle(exact: LocalDate?, from: LocalDate?, to: LocalDate?): String {
        val today = LocalDate.now(zone)
        val defaultFrom = today.minusDays(6)
        val defaultTo = today

        val isDefaultLast7 =
            exact == null && from == defaultFrom && to == defaultTo

        return when {
            isDefaultLast7 -> "Últimos 7 días"
            exact != null -> "Fecha: ${exact.format(fmt)}"
            from != null && to != null -> "${from.format(fmt)} - ${to.format(fmt)}"
            from != null -> "Desde: ${from.format(fmt)}"
            to != null -> "Hasta: ${to.format(fmt)}"
            else -> "Resumen" // (no debería pasar con default last7)
        }
    }
}

/* ======= Clases para transportar datos ======= */
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
private data class Quint<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
private data class Sext<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)
private data class Triple3<A, B, C, D>(val all: A, val exact: B, val from: C, val to: D)

/* ================= Helpers ================= */

private fun Installation.matchesQuery(q: String): Boolean {
    val query = q.trim()
    if (query.isEmpty()) return true

    val qLower = query.lowercase()

    fun contains(value: String?): Boolean =
        value?.trim()?.lowercase()?.contains(qLower) == true

    // directos
    if (order?.toString()?.contains(query, ignoreCase = true) == true) return true
    if (contains(plate)) return true
    if (contains(serie)) return true

    // vehículo
    if (contains(vehicle?.make)) return true
    if (contains(vehicle?.model)) return true
    if (contains(vehicle?.displayName)) return true

    // sede
    if (contains(headquarter?.name)) return true

    // accesorios
    val acc = accessories.orEmpty()
    if (acc.any { contains(it.name) }) return true
    if (acc.any { contains(it.accessoryId) }) return true // opcional

    return false
}

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

private fun Installation.isPaidState(): Boolean {
    val s = state?.trim().orEmpty()
    return s.equals("PAGADO", true) || s.equals("paid", true) || s.equals("Pagado", true)
}

private fun Installation.isUnpaidState(): Boolean {
    val s = state?.trim().orEmpty()
    return s.equals("NO_PAGADO", true) || s.equals("unpaid", true) || s.equals("No pagado", true)
}

private fun Installation.isPartialState(): Boolean {
    val s = state?.trim().orEmpty()
    return s.equals("PARCIAL", true) || s.equals("incompleto", true) || s.equals("incomplete", true)
}

private fun Timestamp?.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
    if (this == null) return null
    return Instant.ofEpochSecond(seconds, nanoseconds.toLong())
        .atZone(zone)
        .toLocalDate()
}
