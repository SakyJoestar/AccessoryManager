package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.model.Installation
import com.example.accessoriesManager.model.InstalledAccessory
import com.example.accessoriesManager.model.Headquarter
import com.example.accessoriesManager.model.Vehicle
import com.example.accessoriesManager.repository.AccessoryRepository
import com.example.accessoriesManager.repository.HeadquarterRepository
import com.example.accessoriesManager.repository.InstallationRepository
import com.example.accessoriesManager.repository.VehicleRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InstallationFormViewModel @Inject constructor(
    private val installationRepository: InstallationRepository,
    private val headquarterRepository: HeadquarterRepository,
    private val vehicleRepository: VehicleRepository,
    private val accessoryRepository: AccessoryRepository
) : ViewModel() {

    // -------------------- UI STATE --------------------
    sealed class UiState {
        data object Idle : UiState()
        data object Saving : UiState()
        data class Success(val msg: String) : UiState()
        data class Error(val msg: String) : UiState()
        data class FieldError(val field: String, val msg: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    // -------------------- FORM DATA --------------------
    private val _form = MutableStateFlow<Installation?>(null)
    val form: StateFlow<Installation?> = _form.asStateFlow()

    // Options (para dropdowns)
    private val _headquarters = MutableStateFlow<List<Headquarter>>(emptyList())
    val headquarters: StateFlow<List<Headquarter>> = _headquarters.asStateFlow()

    private var vehiclesListener: ListenerRegistration? = null
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    private val _accessories = MutableStateFlow<List<Accessory>>(emptyList())
    val accessories: StateFlow<List<Accessory>> = _accessories.asStateFlow()

    // -------------------- Draft / selections --------------------
    private var selectedDate: Timestamp? = null

    // ✅ Strings (texto libre) + id opcional
    private var headquarterLabel: String? = null
    private var headquarterId: String? = null

    private var vehicleLabel: String? = null
    private var vehicleId: String? = null

    private var selectedAccessories: List<InstalledAccessory> = emptyList()

    private var paymentState: String? = "NO_PAGADO"
    private var commentDraft: String = ""

    private val _suggestedIncrement = MutableStateFlow<Int?>(null)
    val suggestedIncrement: StateFlow<Int?> = _suggestedIncrement.asStateFlow()

    init {
        refreshOptions()
        startListeningVehicles()
    }

    private fun refreshOptions() {
        viewModelScope.launch {
            try { _headquarters.value = headquarterRepository.getAll() } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try { _vehicles.value = vehicleRepository.getAll() } catch (_: Exception) {}
        }
        viewModelScope.launch {
            try { _accessories.value = accessoryRepository.getAll() } catch (_: Exception) {}
        }
    }

    // -------------------- Setters desde Fragment --------------------
    fun setDate(ts: Timestamp?) { selectedDate = ts }

    /** Texto libre (usuario escribe): limpia id porque ya no es selección exacta */
    fun setHeadquarterLabel(label: String?) {
        headquarterLabel = label?.trim()?.takeIf { it.isNotBlank() }
        headquarterId = null
    }

    fun setVehicleLabel(label: String?) {
        vehicleLabel = label?.trim()?.takeIf { it.isNotBlank() }
        vehicleId = null
    }

    /** Selección catálogo (usuario selecciona del dropdown): guarda id + label */
    fun setHeadquarterSelection(id: String?, label: String?) {
        headquarterId = id
        headquarterLabel = label?.trim()?.takeIf { it.isNotBlank() }
    }

    fun setVehicleSelection(id: String?, label: String?) {
        vehicleId = id
        vehicleLabel = label?.trim()?.takeIf { it.isNotBlank() }
    }

    // ✅ Si tú quieres setters separados (por si tu Fragment los llama)
    fun setHeadquarterId(id: String?) { headquarterId = id }
    fun setVehicleId(id: String?) { vehicleId = id }

    fun setAccessories(list: List<InstalledAccessory>) { selectedAccessories = list }
    fun setPaymentState(state: String?) { paymentState = state }
    fun setComment(value: String?) { commentDraft = value.orEmpty() }

    // -------------------- Load (edit mode) --------------------
    fun loadById(id: String) {
        viewModelScope.launch {
            try {
                val installation = installationRepository.getById(id)
                _form.value = installation

                selectedDate = installation?.date

                // ✅ retrocompat: headquarter/vehicle pueden ser String o Map (Any?)
                headquarterLabel = anyToHeadquarterLabel(installation?.headquarter).takeIf { it.isNotBlank() }
                headquarterId = installation?.headquarterId

                vehicleLabel = anyToVehicleLabel(installation?.vehicle).takeIf { it.isNotBlank() }
                vehicleId = installation?.vehicleId

                selectedAccessories = installation?.accessories.orEmpty()
                paymentState = installation?.state ?: "NO_PAGADO"
                commentDraft = installation?.comment.orEmpty()

                _suggestedIncrement.value = (installation?.increment ?: 0L).toInt()

            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Error cargando la instalación")
            }
        }
    }

    // ---------- Save / Update ----------
    fun save(
        id: String?,
        order: Int?,
        serie: String,
        plate: String,
        warehouse: String,
        condition: String?,
        increment: Int, // fallback si no se puede resolver por sede
        paymentValueRaw: String?, // lo dejo por compatibilidad
        total: Long,
        paidValue: Long,
        unPaidValue: Long
    ) {
        viewModelScope.launch {
            _state.value = UiState.Idle

            val orderStr = order?.toString().orEmpty().trim()
            val serieClean = serie.trim().uppercase()
            val plateClean = plate.trim().uppercase()
            val warehouseClean = warehouse.trim()

            val commentClean = commentDraft.trim()
            val commentToSave = commentClean.takeIf { it.isNotBlank() }

            val hasOrder = orderStr.isNotBlank()
            val hasSerie = serieClean.isNotBlank()
            val hasPlate = plateClean.isNotBlank()

            if (!hasOrder && !hasSerie && !hasPlate) {
                _state.value = UiState.FieldError(
                    "order_serie_plate",
                    "Debes llenar al menos uno: Orden, Serie o Placa"
                )
                return@launch
            }

            if (selectedDate == null) {
                _state.value = UiState.FieldError("date", "La fecha es obligatoria")
                return@launch
            }

            if (headquarterLabel.isNullOrBlank()) {
                _state.value = UiState.FieldError("headquarter", "La sede es obligatoria")
                return@launch
            }
            if (vehicleLabel.isNullOrBlank()) {
                _state.value = UiState.FieldError("vehicle", "El vehículo es obligatorio")
                return@launch
            }

            val selectedReal = selectedAccessories
                .map { it.copy(name = it.name?.trim()) }
                .filter {
                    val hasId = !it.accessoryId.isNullOrBlank()
                    val hasName = !it.name.isNullOrBlank()
                    (hasId || hasName) && it.price > 0L
                }

            if (selectedReal.isEmpty()) {
                _state.value = UiState.FieldError("accessories", "Debes agregar al menos un accesorio")
                return@launch
            }

            if (hasOrder && orderStr.length > 7) {
                _state.value = UiState.FieldError("order", "Orden: máximo 7 caracteres")
                return@launch
            }

            val serieRegex = Regex("^[A-Z0-9]{1,8}$")
            if (hasSerie && !serieRegex.matches(serieClean)) {
                _state.value = UiState.FieldError("serie", "Serie: solo mayúsculas y números (máx 8)")
                return@launch
            }

            val plateRegex = Regex("^[A-Z0-9]{6}$")
            if (hasPlate && !plateRegex.matches(plateClean)) {
                _state.value = UiState.FieldError("plate", "Placa: debe tener 6 caracteres (A-Z y 0-9)")
                return@launch
            }

            val warehouseRegex = Regex("^\\d{1,4}$")
            if (warehouseClean.isNotBlank() && !warehouseRegex.matches(warehouseClean)) {
                _state.value = UiState.FieldError("warehouse", "Bodega: solo números (máx 4)")
                return@launch
            }

            _state.value = UiState.Saving

            try {
                val now = Timestamp.now()

                val incFromRepo: Int? = headquarterId?.let { idHq ->
                    try { headquarterRepository.getIncrement(idHq) } catch (_: Exception) { null }
                }
                val inc = (incFromRepo ?: increment).toLong()

                val totalWorked = selectedReal.sumOf { it.price + inc }
                val totalPaid = selectedReal.filter { it.isPaid }.sumOf { it.price + inc }
                val totalUnpaid = totalWorked - totalPaid

                val current = if (!id.isNullOrBlank()) installationRepository.getById(id) else null

                val installation = Installation(
                    id = id,
                    order = order,
                    serie = serieClean.ifBlank { null },
                    plate = plateClean.ifBlank { null },
                    warehouse = warehouseClean.ifBlank { null },
                    condition = condition?.ifBlank { null },
                    date = selectedDate,

                    // ✅ se guarda texto (String) y el id opcional
                    headquarter = headquarterLabel,
                    headquarterId = headquarterId,

                    vehicle = vehicleLabel,
                    vehicleId = vehicleId,

                    accessories = selectedReal,
                    increment = inc,
                    state = paymentState,

                    totalWorked = totalWorked,
                    totalPaid = totalPaid,
                    totalUnpaid = totalUnpaid,

                    comment = commentToSave,

                    createdAt = current?.createdAt ?: now,
                    updatedAt = now
                )

                if (id.isNullOrBlank()) {
                    installationRepository.create(installation)
                    _state.value = UiState.Success("Instalación guardada")
                } else {
                    installationRepository.update(id, installation)
                    _state.value = UiState.Success("Instalación actualizada")
                }

            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Error guardando la instalación")
            }
        }
    }

    fun loadIncrementForHeadquarter(headquarterId: String, fallback: Int) {
        viewModelScope.launch {
            val inc = try {
                headquarterRepository.getIncrement(headquarterId)
            } catch (_: Exception) {
                0
            }
            _suggestedIncrement.value = if (inc > 0) inc else fallback
        }
    }

    fun startListeningVehicles() {
        if (vehiclesListener != null) return

        vehiclesListener = vehicleRepository.listenVehicles(
            onChange = { _vehicles.value = it },
            onError = { e ->
                android.util.Log.e("VM", "Error listening vehicles", e)
            }
        )
    }

    override fun onCleared() {
        super.onCleared()
        vehiclesListener?.remove()
        vehiclesListener = null
    }

    // -------------------- Retro helpers (Any? -> String) --------------------

    @Suppress("UNCHECKED_CAST")
    private fun anyToHeadquarterLabel(h: Any?): String {
        if (h == null) return ""
        return when (h) {
            is String -> h.trim()
            is Map<*, *> -> ((h["name"] as? String).orEmpty()).trim()
            else -> h.toString().trim()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun anyToVehicleLabel(v: Any?): String {
        if (v == null) return ""
        return when (v) {
            is String -> v.trim()
            is Map<*, *> -> {
                val make = (v["make"] as? String).orEmpty().trim()
                val model = (v["model"] as? String).orEmpty().trim()
                listOf(make, model).filter { it.isNotBlank() }.joinToString(" - ").trim()
            }
            else -> v.toString().trim()
        }
    }
}
