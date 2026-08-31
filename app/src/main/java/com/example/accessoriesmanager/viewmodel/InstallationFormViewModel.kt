package com.example.accessoriesmanager.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.accessoriesmanager.connectivity.ConnectivityObserver
import com.example.accessoriesmanager.model.Accessory
import com.example.accessoriesmanager.model.Installation
import com.example.accessoriesmanager.model.InstalledAccessory
import com.example.accessoriesmanager.model.Headquarter
import com.example.accessoriesmanager.model.Vehicle
import com.example.accessoriesmanager.model.headquarterLabelFromAny
import com.example.accessoriesmanager.model.vehicleLabelFromAny
import com.example.accessoriesmanager.form.InstallationTotalsCalculator
import com.example.accessoriesmanager.repository.AccessoryRepository
import com.example.accessoriesmanager.repository.HeadquarterRepository
import com.example.accessoriesmanager.repository.InstallationRepository
import com.example.accessoriesmanager.repository.VehicleRepository
import com.example.accessoriesmanager.ui.FormUiState
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
    private val accessoryRepository: AccessoryRepository,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    // -------------------- UI STATE --------------------
    private val _state = MutableStateFlow<FormUiState>(FormUiState.Idle)
    val state: StateFlow<FormUiState> = _state.asStateFlow()

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
            try {
                _headquarters.value = headquarterRepository.getAll()
            } catch (e: Exception) {
                Log.w("InstallationFormVM", "No se pudieron cargar las sedes", e)
            }
        }
        viewModelScope.launch {
            try {
                _vehicles.value = vehicleRepository.getAll()
            } catch (e: Exception) {
                Log.w("InstallationFormVM", "No se pudieron cargar los vehículos", e)
            }
        }
        viewModelScope.launch {
            try {
                _accessories.value = accessoryRepository.getAll()
            } catch (e: Exception) {
                Log.w("InstallationFormVM", "No se pudieron cargar los accesorios", e)
            }
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
                headquarterLabel = headquarterLabelFromAny(installation?.headquarter).takeIf { it.isNotBlank() }
                headquarterId = installation?.headquarterId

                vehicleLabel = vehicleLabelFromAny(installation?.vehicle).takeIf { it.isNotBlank() }
                vehicleId = installation?.vehicleId

                selectedAccessories = installation?.accessories.orEmpty()
                paymentState = installation?.state ?: "NO_PAGADO"
                commentDraft = installation?.comment.orEmpty()

                _suggestedIncrement.value = (installation?.increment ?: 0L).toInt()

            } catch (e: Exception) {
                _state.value = FormUiState.Error(e.message ?: "Error cargando la instalación")
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
        photoUris: List<Uri> = emptyList(),
        existingPhotoUrls: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _state.value = FormUiState.Idle

            val commentToSave = commentDraft.trim().takeIf { it.isNotBlank() }

            val input = InstallationFormInput(
                order = order,
                serie = serie,
                plate = plate,
                warehouse = warehouse,
                date = selectedDate,
                headquarterLabel = headquarterLabel,
                vehicleLabel = vehicleLabel,
                accessories = selectedAccessories
            )

            val validated = when (val result = InstallationFormValidator.validate(input)) {
                is InstallationFormValidation.Invalid -> {
                    _state.value = FormUiState.FieldError(result.error.field, result.error.message)
                    return@launch
                }
                is InstallationFormValidation.Valid -> result.form
            }

            if (photoUris.isNotEmpty() && !connectivityObserver.isOnline.value) {
                _state.value = FormUiState.Error(
                    "No se pueden subir fotos sin conexión — inténtalo cuando tengas internet"
                )
                return@launch
            }

            _state.value = FormUiState.Saving

            try {
                val now = Timestamp.now()
                val inc = increment.toLong()
                val totals = InstallationTotalsCalculator.compute(validated.accessories, inc)

                val current = if (!id.isNullOrBlank()) installationRepository.getById(id) else null

                // ✅ id resuelto de antemano: para instalaciones nuevas se reserva localmente
                // (sin red) así las fotos se suben a la carpeta correcta antes de escribir el doc.
                val resolvedId = id?.takeIf { it.isNotBlank() } ?: installationRepository.newInstallationId()

                val uploadedPhotoUrls = if (photoUris.isNotEmpty()) {
                    installationRepository.uploadPhotos(resolvedId, photoUris)
                } else {
                    emptyList()
                }
                val allPhotos = existingPhotoUrls + uploadedPhotoUrls

                val installation = Installation(
                    id = resolvedId,
                    order = order,
                    serie = validated.serie,
                    plate = validated.plate,
                    warehouse = validated.warehouse,
                    condition = condition?.ifBlank { null },
                    date = selectedDate,

                    // ✅ se guarda texto (String) y el id opcional
                    headquarter = headquarterLabel,
                    headquarterId = headquarterId,

                    vehicle = vehicleLabel,
                    vehicleId = vehicleId,

                    accessories = validated.accessories,
                    photos = allPhotos.ifEmpty { null },
                    increment = inc,
                    state = paymentState,

                    totalWorked = totals.total,
                    totalPaid = totals.paid,
                    totalUnpaid = totals.unpaid,

                    comment = commentToSave,

                    createdAt = current?.createdAt ?: now,
                    updatedAt = now
                )

                if (id.isNullOrBlank()) {
                    installationRepository.create(installation)
                    _state.value = FormUiState.Success("Instalación guardada")
                } else {
                    installationRepository.update(id, installation)
                    _state.value = FormUiState.Success("Instalación actualizada")
                }

            } catch (e: Exception) {
                _state.value = FormUiState.Error(e.message ?: "Error guardando la instalación")
            }
        }
    }

    fun loadIncrementForHeadquarter(headquarterId: String, fallback: Int) {
        viewModelScope.launch {
            val inc = try {
                headquarterRepository.getIncrement(headquarterId)
            } catch (e: Exception) {
                Log.w("InstallationFormVM", "No se pudo resolver el incremento de la sede $headquarterId", e)
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
}
