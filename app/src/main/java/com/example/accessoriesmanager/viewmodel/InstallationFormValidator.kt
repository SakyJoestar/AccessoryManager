package com.example.accessoriesmanager.viewmodel

import com.example.accessoriesmanager.model.InstalledAccessory
import com.google.firebase.Timestamp

data class InstallationFormInput(
    val order: Int?,
    val serie: String,
    val plate: String,
    val warehouse: String,
    val date: Timestamp?,
    val headquarterLabel: String?,
    val vehicleLabel: String?,
    val accessories: List<InstalledAccessory>
)

data class InstallationFormError(val field: String, val message: String)

data class ValidatedInstallationForm(
    val serie: String?,
    val plate: String?,
    val warehouse: String?,
    val accessories: List<InstalledAccessory>
)

sealed class InstallationFormValidation {
    data class Valid(val form: ValidatedInstallationForm) : InstallationFormValidation()
    data class Invalid(val error: InstallationFormError) : InstallationFormValidation()
}

/** Field-level validation rules for the installation form. Pure: no Android, no Firebase. */
object InstallationFormValidator {

    private val SERIE_REGEX = Regex("^[A-Z0-9]{1,8}$")
    private val PLATE_REGEX = Regex("^[A-Z0-9]{6}$")
    private val WAREHOUSE_REGEX = Regex("^\\d{1,4}$")

    fun validate(input: InstallationFormInput): InstallationFormValidation {
        val orderStr = input.order?.toString().orEmpty().trim()
        val serieClean = input.serie.trim().uppercase()
        val plateClean = input.plate.trim().uppercase()
        val warehouseClean = input.warehouse.trim()

        val hasOrder = orderStr.isNotBlank()
        val hasSerie = serieClean.isNotBlank()
        val hasPlate = plateClean.isNotBlank()

        if (!hasOrder && !hasSerie && !hasPlate) {
            return invalid("order_serie_plate", "Debes llenar al menos uno: Orden, Serie o Placa")
        }

        if (input.date == null) {
            return invalid("date", "La fecha es obligatoria")
        }

        if (input.headquarterLabel.isNullOrBlank()) {
            return invalid("headquarter", "La sede es obligatoria")
        }
        if (input.vehicleLabel.isNullOrBlank()) {
            return invalid("vehicle", "El vehículo es obligatorio")
        }

        val billableAccessories = input.accessories
            .map { it.copy(name = it.name?.trim()) }
            .filter {
                val hasId = !it.accessoryId.isNullOrBlank()
                val hasName = !it.name.isNullOrBlank()
                (hasId || hasName) && it.price > 0L
            }

        if (billableAccessories.isEmpty()) {
            return invalid("accessories", "Debes agregar al menos un accesorio")
        }

        if (hasOrder && orderStr.length > 7) {
            return invalid("order", "Orden: máximo 7 caracteres")
        }

        if (hasSerie && !SERIE_REGEX.matches(serieClean)) {
            return invalid("serie", "Serie: solo mayúsculas y números (máx 8)")
        }

        if (hasPlate && !PLATE_REGEX.matches(plateClean)) {
            return invalid("plate", "Placa: debe tener 6 caracteres (A-Z y 0-9)")
        }

        if (warehouseClean.isNotBlank() && !WAREHOUSE_REGEX.matches(warehouseClean)) {
            return invalid("warehouse", "Bodega: solo números (máx 4)")
        }

        return InstallationFormValidation.Valid(
            ValidatedInstallationForm(
                serie = serieClean.ifBlank { null },
                plate = plateClean.ifBlank { null },
                warehouse = warehouseClean.ifBlank { null },
                accessories = billableAccessories
            )
        )
    }

    private fun invalid(field: String, message: String) =
        InstallationFormValidation.Invalid(InstallationFormError(field, message))
}
