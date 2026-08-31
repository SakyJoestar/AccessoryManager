package com.example.accessoriesmanager.form

import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.accessoriesmanager.R
import com.example.accessoriesmanager.model.Headquarter
import com.example.accessoriesmanager.model.Vehicle
import com.example.accessoriesmanager.ui.showSnack
import com.example.accessoriesmanager.viewmodel.AccessoryViewModel
import com.example.accessoriesmanager.viewmodel.HeadquarterViewModel
import com.example.accessoriesmanager.viewmodel.VehicleViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the "create Headquarter/Vehicle/Accessory inline" dialog reachable from the
 * installation form's + buttons, and the create-event subscriptions that dismiss it on
 * success. [onHeadquarterCreated]/[onVehicleCreated] let the form apply the freshly created
 * entity to its own fields (selection id, increment, totals); accessory creation needs no
 * such feedback since the form's accessory list re-syncs from the catalog listener.
 */
class InstallationCreateController(
    private val fragment: Fragment,
    private val accessoryViewModel: AccessoryViewModel,
    private val headquarterViewModel: HeadquarterViewModel,
    private val vehicleViewModel: VehicleViewModel,
    private val onHeadquarterCreated: (Headquarter) -> Unit,
    private val onVehicleCreated: (Vehicle) -> Unit,
) {
    enum class CreateType { ACCESSORY, HEADQUARTER, VEHICLE }

    private var dialog: AlertDialog? = null
    private var dialogType: CreateType? = null

    fun showCreateDialog(type: CreateType, targetDropdown: AutoCompleteTextView?) {
        val dialogView = fragment.layoutInflater.inflate(R.layout.dialog_two_fields, null)

        val til1 = dialogView.findViewById<TextInputLayout>(R.id.tilField1)
        val til2 = dialogView.findViewById<TextInputLayout>(R.id.tilField2)
        val et1 = dialogView.findViewById<TextInputEditText>(R.id.etField1)
        val et2 = dialogView.findViewById<TextInputEditText>(R.id.etField2)

        when (type) {
            CreateType.ACCESSORY -> {
                til1.hint = "Nombre"
                til2.hint = "Precio"
                et2.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }

            CreateType.HEADQUARTER -> {
                til1.hint = "Nombre"
                til2.hint = "Incremento (opcional)"
                et2.inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }

            CreateType.VEHICLE -> {
                til1.hint = "Marca"
                til2.hint = "Modelo"
                et2.inputType = android.text.InputType.TYPE_CLASS_TEXT
            }
        }

        fun clearErrors() {
            til1.error = null
            til2.error = null
            til1.isErrorEnabled = false
            til2.isErrorEnabled = false
        }

        val title = when (type) {
            CreateType.ACCESSORY -> "Crear accesorio"
            CreateType.HEADQUARTER -> "Crear sede"
            CreateType.VEHICLE -> "Crear vehículo"
        }

        dialogType = type

        val newDialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()

        dialog = newDialog
        newDialog.setOnDismissListener {
            dialog = null
            dialogType = null
        }

        newDialog.setOnShowListener {
            newDialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                clearErrors()

                val v1 = et1.text?.toString()?.trim().orEmpty()
                val v2 = et2.text?.toString()?.trim().orEmpty()

                if (v1.isBlank()) {
                    til1.isErrorEnabled = true
                    til1.error = "Requerido"
                    return@setOnClickListener
                }

                if (type != CreateType.HEADQUARTER && v2.isBlank()) {
                    til2.isErrorEnabled = true
                    til2.error = "Requerido"
                    return@setOnClickListener
                }

                when (type) {
                    CreateType.ACCESSORY -> {
                        val price = v2.replace(".", "").toLongOrNull()
                        if (price == null) {
                            til2.isErrorEnabled = true
                            til2.error = "Debe ser número"
                            return@setOnClickListener
                        }
                        accessoryViewModel.createAccessory(name = v1, price = price)
                    }

                    CreateType.HEADQUARTER -> {
                        val inc = v2.replace(".", "").toLongOrNull() ?: 0L
                        headquarterViewModel.createHeadquarter(name = v1, increment = inc)
                        targetDropdown?.setText(v1, false)
                    }

                    CreateType.VEHICLE -> {
                        vehicleViewModel.createVehicle(make = v1, model = v2)
                        targetDropdown?.setText("$v1 - $v2", false)
                    }
                }
            }
        }

        newDialog.show()
    }

    /** Call from within the fragment's own `repeatOnLifecycle(STARTED)` scope. */
    fun observeCreateEvents(scope: CoroutineScope) {
        scope.launch {
            headquarterViewModel.createEvents.collect { ev ->
                when (ev) {
                    is HeadquarterViewModel.CreateEvent.Success -> {
                        onHeadquarterCreated(ev.headquarter)
                        fragment.showSnack("Sede creada")
                        if (dialogType == CreateType.HEADQUARTER) dialog?.dismiss()
                    }

                    is HeadquarterViewModel.CreateEvent.Error -> fragment.showSnack(ev.msg)
                }
            }
        }

        scope.launch {
            vehicleViewModel.createEvents.collect { ev ->
                when (ev) {
                    is VehicleViewModel.CreateEvent.Success -> {
                        onVehicleCreated(ev.vehicle)
                        fragment.showSnack("Vehículo creado")
                        if (dialogType == CreateType.VEHICLE) dialog?.dismiss()
                    }

                    is VehicleViewModel.CreateEvent.Error -> fragment.showSnack(ev.msg)
                }
            }
        }

        scope.launch {
            accessoryViewModel.createEvents.collect { ev ->
                when (ev) {
                    is AccessoryViewModel.CreateEvent.Success -> {
                        fragment.showSnack("Accesorio creado")
                        if (dialogType == CreateType.ACCESSORY) dialog?.dismiss()
                    }

                    is AccessoryViewModel.CreateEvent.Error -> fragment.showSnack(ev.msg)
                }
            }
        }
    }
}
