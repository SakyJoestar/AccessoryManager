package com.example.accessoriesmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesmanager.model.Installation
import com.example.accessoriesmanager.model.isPaidState
import com.example.accessoriesmanager.model.isPartialState
import com.example.accessoriesmanager.model.isUnpaidState
import com.example.accessoriesmanager.model.matchesDates
import com.example.accessoriesmanager.model.matchesQuery
import com.example.accessoriesmanager.model.matchesStatus
import com.example.accessoriesmanager.repository.InstallationRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

    private data class Filters(
        val query: String,
        val status: String,
        val dateExact: LocalDate?,
        val dateFrom: LocalDate?,
        val dateTo: LocalDate?
    )

    private val filters: Flow<Filters> = combine(
        _query, _statusFilter, _dateExact, _dateFrom, _dateTo
    ) { query, status, exact, from, to -> Filters(query, status, exact, from, to) }

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
            launch {
                combine(_all, filters) { all, f -> all to f }.collect { (all, f) ->
                    _items.value = all.asSequence()
                        .filter { it.matchesQuery(f.query) }
                        .filter { it.matchesStatus(f.status) }
                        .filter { it.matchesDates(f.dateExact, f.dateFrom, f.dateTo) }
                        .toList()
                }
            }

            // ---- SUMMARY: solo depende de _all + fechas ----
            launch {
                combine(_all, filters) { all, f -> all to f }.collect { (all, f) ->
                    val inDate = all.asSequence()
                        .filter { it.matchesDates(f.dateExact, f.dateFrom, f.dateTo) }
                        .toList()

                    val title = buildSummaryTitle(f.dateExact, f.dateFrom, f.dateTo)

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

    fun clearDates() {
        setDefaultLast7Days()
    }

    fun currentDateFrom(): LocalDate? = _dateFrom.value
    fun currentDateTo(): LocalDate? = _dateTo.value

    private fun setDefaultLast7Days() {
        val today = LocalDate.now(zone)
        _dateExact.value = null
        _dateFrom.value = today.minusDays(6)
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

        val isDefaultLast7 = exact == null && from == defaultFrom && to == defaultTo

        return when {
            isDefaultLast7 -> "Últimos 7 días"
            exact != null -> "Fecha: ${exact.format(fmt)}"
            from != null && to != null -> "${from.format(fmt)} - ${to.format(fmt)}"
            from != null -> "Desde: ${from.format(fmt)}"
            to != null -> "Hasta: ${to.format(fmt)}"
            else -> "Resumen"
        }
    }
}
