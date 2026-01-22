package com.example.accessoriesManager.form

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentFormBaseBinding
import com.example.accessoriesManager.adapter.InstalledAccessoryAdapter
import com.example.accessoriesManager.adapter.AccessoryAdapter
import com.example.accessoriesManager.model.InstalledAccessory
import com.example.accessoriesManager.ui.ThousandsSeparatorTextWatcher
import com.example.accessoriesManager.ui.showSnack
import com.example.accessoriesManager.viewmodel.InstallationFormViewModel
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.example.accessoriesManager.model.Accessory
import java.util.Calendar

@AndroidEntryPoint
class InstallationFormFragment : Fragment(R.layout.fragment_form_base) {

    private var _binding: FragmentFormBaseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstallationFormViewModel by viewModels()

    private var editId: String? = null

    private lateinit var accessoriesAdapter: InstalledAccessoryAdapter

    companion object {
        private const val ARG_ID = "installationId"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentFormBaseBinding.bind(view)

        // ✅ Inflar campos específicos del form de instalación
        layoutInflater.inflate(
            R.layout.form_installation_fields,
            binding.formFieldsContainer,
            true
        )

        // ---------- Find Views ----------
        val container = binding.formFieldsContainer

        val etOrder = container.findViewById<TextInputEditText>(R.id.etOrder)
        val etSerie = container.findViewById<TextInputEditText>(R.id.etSerie)
        val etPlate = container.findViewById<TextInputEditText>(R.id.etPlate)
        val etWarehouse = container.findViewById<TextInputEditText>(R.id.etWarehouse)

        val actCondition = container.findViewById<android.widget.AutoCompleteTextView>(R.id.actCondition)
        val etDate = container.findViewById<TextInputEditText>(R.id.etDate)

        val actHeadquarter = container.findViewById<android.widget.AutoCompleteTextView>(R.id.actHeadquarter)
        val etIncrement = container.findViewById<TextInputEditText>(R.id.etIncrement)

        val actVehicle = container.findViewById<android.widget.AutoCompleteTextView>(R.id.actVehicle)

        val rvAccessories = container.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvAccessories)
        val btnAddAccessory = container.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAddAccessory)

        val tgPayment = container.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.tgPaymentStatus)
        val btnPaid = container.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPaid)
        val btnNotPaid = container.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnNotPaid)
        val btnPartiallyPaid = container.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPartiallyPaid)

        val etPaymentValue = container.findViewById<TextInputEditText>(R.id.etPaymentValue)
        etPaymentValue.addTextChangedListener(ThousandsSeparatorTextWatcher(etPaymentValue))

        // Defaults
        etIncrement.setText("0")
        etPaymentValue.setText("0")

        // ---------- Edit mode ----------
        editId = arguments?.getString(ARG_ID)
        val isEditMode = !editId.isNullOrBlank()

        setTitles(isEditMode)
        binding.btnSave.text = if (isEditMode) "Actualizar" else "Guardar"

        if (isEditMode) {
            viewModel.loadById(editId!!)
        } else {
            // default: hoy
            setDateText(etDate, Calendar.getInstance())
        }

        val normalText = binding.btnSave.text

        // ---------- Condición dropdown (simple) ----------
        val conditions = listOf("Nuevo", "Usado", "Garantía", "Reproceso")
        val conditionAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            conditions
        )
        actCondition.setAdapter(conditionAdapter)

        // ---------- Date picker ----------
        etDate.setOnClickListener {
            showDatePicker { cal ->
                setDateText(etDate, cal)
                viewModel.setDate(fromCalendarToTimestamp(cal))
            }
        }

        // ---------- Accessories Recycler ----------
        accessoriesAdapter = InstalledAccessoryAdapter(
            options = emptyList(), // se setea cuando carguen options
            onChanged = { list ->
                viewModel.setAccessories(list)
            }
        )

        rvAccessories.layoutManager = LinearLayoutManager(requireContext())
        rvAccessories.adapter = accessoriesAdapter

        btnAddAccessory.setOnClickListener {
            accessoriesAdapter.addEmpty()
        }

        // ---------- Payment status (toggle exclusive) ----------
        // Guardamos string state en VM según selección
        tgPayment.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val state = when (checkedId) {
                btnPaid.id -> "PAGADO"
                btnNotPaid.id -> "NO_PAGADO"
                btnPartiallyPaid.id -> "ABONADO"
                else -> null
            }
            viewModel.setPaymentState(state)
        }

        // Mostrar/ocultar Valor según ABONADO
        fun updatePaymentValueVisibility() {
            val isPartial = tgPayment.checkedButtonId == btnPartiallyPaid.id
            container.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPaymentValue)
                ?.visibility = if (isPartial) View.VISIBLE else View.GONE
        }

        tgPayment.addOnButtonCheckedListener { _, _, _ ->
            updatePaymentValueVisibility()
        }
        updatePaymentValueVisibility()

        // ---------- Increment miles ----------
        etIncrement.addTextChangedListener(ThousandsSeparatorTextWatcher(etIncrement))

        // ---------- Save ----------
        binding.btnSave.setOnClickListener {
            // limpiar errores simples
            etOrder.error = null
            etSerie.error = null
            etPlate.error = null
            etWarehouse.error = null
            etIncrement.error = null

            val order = etOrder.text?.toString()?.toIntOrNull()
            val serie = etSerie.text?.toString().orEmpty()
            val plate = etPlate.text?.toString().orEmpty()
            val warehouse = etWarehouse.text?.toString().orEmpty()

            val incrementRaw = etIncrement.text?.toString()?.replace(".", "") ?: "0"
            val increment = incrementRaw.toIntOrNull() ?: 0

            val paymentValueRaw = etPaymentValue.text?.toString()?.replace(".", "") ?: "0"

            viewModel.save(
                id = editId,
                order = order,
                serie = serie,
                plate = plate,
                warehouse = warehouse,
                condition = actCondition.text?.toString(),
                increment = increment,
                paymentValueRaw = paymentValueRaw
            )
        }

        // ---------- VM state ----------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is InstallationFormViewModel.UiState.Idle -> {
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is InstallationFormViewModel.UiState.Saving -> {
                            binding.btnSave.isEnabled = false
                            binding.btnSave.text = if (isEditMode) "Actualizando..." else "Guardando..."
                        }

                        is InstallationFormViewModel.UiState.Success -> {
                            showSnack(state.msg)
                            hideKeyboard()

                            if (!isEditMode) {
                                // limpiar “suave”
                                etOrder.setText("")
                                etSerie.setText("")
                                etPlate.setText("")
                                etWarehouse.setText("")
                                actCondition.setText("", false)
                                actHeadquarter.setText("", false)
                                actVehicle.setText("", false)
                                etIncrement.setText("0")
                                accessoriesAdapter.submitList(emptyList())
                                tgPayment.check(btnNotPaid.id)
                                etPaymentValue.setText("0")
                                setDateText(etDate, Calendar.getInstance())
                                etOrder.requestFocus()
                            }

                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is InstallationFormViewModel.UiState.Error -> {
                            showSnack(state.msg)
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        else -> {}
                    }
                }
            }
        }

        // ---------- VM form: rellenar si edición ----------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.form.collect { installation ->
                    installation ?: return@collect

                    etOrder.setText(installation.order?.toString().orEmpty())
                    etSerie.setText(installation.serie.orEmpty())
                    etPlate.setText(installation.plate.orEmpty())
                    etWarehouse.setText(installation.warehouse.orEmpty())

                    actCondition.setText(installation.condition.orEmpty(), false)

                    // date
                    installation.date?.let { ts ->
                        val cal = Calendar.getInstance().apply { time = ts.toDate() }
                        setDateText(etDate, cal)
                    }

                    // sede / vehiculo (si son snapshot)
                    actHeadquarter.setText(installation.headquarter?.name.orEmpty(), false)
                    actVehicle.setText(installation.vehicle?.model.orEmpty(), false)

                    // accesorios
                    accessoriesAdapter.submitList(installation.accessories.orEmpty())

                    // estado pago
                    when (installation.state) {
                        "PAGADO" -> tgPayment.check(btnPaid.id)
                        "ABONADO" -> tgPayment.check(btnPartiallyPaid.id)
                        else -> tgPayment.check(btnNotPaid.id)
                    }
                    updatePaymentValueVisibility()
                }
            }
        }

        // ---------- Options: sedes, vehículos, accesorios ----------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.headquarters.collect { list ->
                        val names = list.map { it.name ?: "" }
                        val hqAdapter = android.widget.ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            names
                        )
                        actHeadquarter.setAdapter(hqAdapter)

                        actHeadquarter.setOnItemClickListener { _, _, idx, _ ->
                            viewModel.setHeadquarter(list[idx])
                        }
                    }
                }

                launch {
                    viewModel.vehicles.collect { list ->
                        val labels = list.map { "${it.make} ${it.model}" }
                        val vAdapter = android.widget.ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            labels
                        )
                        actVehicle.setAdapter(vAdapter)

                        actVehicle.setOnItemClickListener { _, _, idx, _ ->
                            viewModel.setVehicle(list[idx])
                        }
                    }
                }

                launch {
                    viewModel.accessories.collect { list ->
                        // Convertir a options del adapter
                        val opts = list.map {
                            Accessory(
                                id = it.id ?: "",
                                name = it.name ?: "",
                                price = it.price ?: 0
                            )
                        }
                        // recrear adapter de accesorios con nuevas opciones (más simple y seguro)
                        val current = accessoriesAdapter.getCurrent()
                        accessoriesAdapter = InstalledAccessoryAdapter(opts) { updated ->
                            viewModel.setAccessories(updated)
                        }
                        rvAccessories.adapter = accessoriesAdapter
                        accessoriesAdapter.submitList(current)
                    }
                }
            }
        }
    }

    private fun setTitles(isEdit: Boolean) {
        binding.tvFormTitle.text = if (isEdit) "Editar instalación" else "Nueva instalación"
        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title =
            if (isEdit) "Editar instalación" else "Nueva instalación"
    }

    private fun showDatePicker(onPicked: (Calendar) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                onPicked(picked)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setDateText(et: TextInputEditText, cal: Calendar) {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        et.setText(String.format("%04d-%02d-%02d", y, m, d))
    }

    private fun fromCalendarToTimestamp(cal: Calendar): Timestamp {
        return Timestamp(cal.time)
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
