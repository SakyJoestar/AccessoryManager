package com.example.accessoriesManager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.model.Headquarter
import com.example.accessoriesManager.model.Installation
import com.example.accessoriesManager.model.InstalledAccessory
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

        // Si luego quieres validaciones específicas:
        data class FieldError(val field: String, val msg: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    // -------------------- FORM DATA --------------------
    private val _form = MutableStateFlow<Installation?>(null)
    val form: StateFlow<Installation?> = _form.asStateFlow()

    // Headquarters
    private val _headquarters = MutableStateFlow<List<Headquarter>>(emptyList())
    val headquarters: StateFlow<List<Headquarter>> = _headquarters.asStateFlow()

    // Vehicles
    private var vehiclesListener: ListenerRegistration? = null
    private val _vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val vehicles: StateFlow<List<Vehicle>> = _vehicles.asStateFlow()

    //Accessories
    private val _accessories = MutableStateFlow<List<Accessory>>(emptyList())
    val accessories: StateFlow<List<Accessory>> = _accessories.asStateFlow()

    // -------------------- Draft / selections --------------------
    private var selectedDate: Timestamp? = null
    private var selectedHeadquarter: Headquarter? = null
    private var selectedVehicle: Vehicle? = null
    private var selectedAccessories: List<InstalledAccessory> = emptyList()
    private var paymentState: String? = "NO_PAGADO" // default


    init {
        // Cargar combos
        refreshOptions()

        startListeningVehicles()
    }

    private fun refreshOptions() {
        viewModelScope.launch {
            try {
                _headquarters.value = headquarterRepository.getAll() // <-- ajusta si tu repo se llama distinto
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            try {
                _vehicles.value = vehicleRepository.getAll()
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            try {
                _accessories.value = accessoryRepository.getAll()
            } catch (_: Exception) {}
        }
    }

    // -------------------- Setters desde Fragment --------------------
    fun setDate(ts: Timestamp?) {
        selectedDate = ts
    }

    fun setHeadquarter(hq: Headquarter?) {
        selectedHeadquarter = hq
    }

    fun setVehicle(vehicle: Vehicle?) {
        selectedVehicle = vehicle
    }

    fun setAccessories(list: List<InstalledAccessory>) {
        selectedAccessories = list
    }

    fun setPaymentState(state: String?) {
        paymentState = state
    }

    // -------------------- Load (edit mode) --------------------
    fun loadById(id: String) {
        viewModelScope.launch {
            try {
                val installation = installationRepository.getById(id)
                _form.value = installation

                // llenar drafts para que el usuario edite sin perder estado
                selectedDate = installation?.date
                selectedHeadquarter = installation?.headquarter
                selectedVehicle = installation?.vehicle
                selectedAccessories = installation?.accessories.orEmpty()
                paymentState = installation?.state ?: "NO_PAGADO"

            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Error cargando la instalación")
            }
        }
    }

    // -------------------- Save / Update --------------------
    fun save(
        id: String?,
        order: Int?,
        serie: String,
        plate: String,
        warehouse: String,
        condition: String?,
        increment: Int,
        paymentValueRaw: String?
    ) {
        viewModelScope.launch {
            // Reset state
            _state.value = UiState.Idle

            // --------- Validaciones rápidas (mínimas) ---------
            if (order == null || order <= 0) {
                _state.value = UiState.FieldError("order", "La orden debe ser un número válido")
                return@launch
            }
            if (plate.isBlank()) {
                _state.value = UiState.FieldError("plate", "La placa es obligatoria")
                return@launch
            }
            if (selectedHeadquarter == null) {
                _state.value = UiState.FieldError("headquarter", "Selecciona una sede")
                return@launch
            }
            if (selectedVehicle == null) {
                _state.value = UiState.FieldError("vehicle", "Selecciona un vehículo")
                return@launch
            }
            if (selectedDate == null) {
                _state.value = UiState.FieldError("date", "Selecciona una fecha")
                return@launch
            }


            val paymentValue = paymentValueRaw
                ?.replace(".", "")
                ?.trim()
                ?.toIntOrNull()

            // Si está ABONADO, podrías exigir paymentValue > 0 (opcional)
            if (paymentState == "ABONADO" && (paymentValue == null || paymentValue <= 0)) {
                _state.value = UiState.FieldError("paymentValue", "Si es abonado, ingresa un valor válido")
                return@launch
            }

            _state.value = UiState.Saving

            try {
                val now = Timestamp.now()

                // Si estás editando, conserva createdAt si ya existe
                val current = if (!id.isNullOrBlank()) installationRepository.getById(id) else null

                val installation = Installation(
                    id = id,
                    order = order,
                    serie = serie.ifBlank { null },
                    plate = plate.ifBlank { null },
                    warehouse = warehouse.ifBlank { null },
                    condition = condition?.ifBlank { null },
                    date = selectedDate,
                    headquarter = selectedHeadquarter,
                    vehicle = selectedVehicle,
                    state = paymentState,
                    createdAt = current?.createdAt ?: now,
                    updatedAt = now
                )

                if (id.isNullOrBlank()) {
                    installationRepository.create(installation)
                    _state.value = UiState.Success("Instalación guardada")
                    _form.value = null
                } else {
                    installationRepository.update(id, installation)
                    _state.value = UiState.Success("Instalación actualizada")
                    _form.value = installation
                }

            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Error guardando la instalación")
            }
        }
    }

    private val _suggestedIncrement = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    val suggestedIncrement: kotlinx.coroutines.flow.StateFlow<Int?> = _suggestedIncrement


    // Traer el incremento del headquarter
    fun loadIncrementForHeadquarter(headquarterId: String, fallback: Int) {
        viewModelScope.launch {
            val inc = try {
                headquarterRepository.getIncrement(headquarterId) // último de installations
            } catch (_: Exception) {
                0
            }

            // ✅ nunca emitir 0 si hay fallback
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
}
