package com.example.accessoriesManager.form

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentFormBaseBinding
import com.example.accessoriesManager.adapter.InstalledAccessoryAdapter
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.model.Headquarter
import com.example.accessoriesManager.model.InstalledAccessory
import com.example.accessoriesManager.model.Vehicle
import com.example.accessoriesManager.ui.ThousandsSeparatorTextWatcher
import com.example.accessoriesManager.ui.showSnack
import com.example.accessoriesManager.viewmodel.AccessoryViewModel
import com.example.accessoriesManager.viewmodel.HeadquarterViewModel
import com.example.accessoriesManager.viewmodel.InstallationFormViewModel
import com.example.accessoriesManager.viewmodel.VehicleViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class InstallationFormFragment : Fragment(R.layout.fragment_form_base) {

    private var _binding: FragmentFormBaseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstallationFormViewModel by viewModels()
    private val accessoryViewModel: AccessoryViewModel by viewModels()
    private val headquarterViewModel: HeadquarterViewModel by viewModels()
    private val vehicleViewModel: VehicleViewModel by viewModels()

    private var editId: String? = null
    private lateinit var accessoriesAdapter: InstalledAccessoryAdapter

    private var isAutoToggling = false
    private var currentAccessories: List<InstalledAccessory> = emptyList()

    // ✅ ids opcionales (solo si selecciona del catálogo)
    private var selectedHqId: String? = null
    private var selectedVehicleId: String? = null

    private lateinit var etIncrement: TextInputEditText
    private lateinit var etTotalWorked: TextInputEditText
    private lateinit var etPaid: TextInputEditText
    private lateinit var etUnpaid: TextInputEditText
    private lateinit var etComment: TextInputEditText

    private lateinit var tgPayment: MaterialButtonToggleGroup
    private lateinit var btnPaid: MaterialButton
    private lateinit var btnNotPaid: MaterialButton
    private lateinit var btnPartiallyPaid: MaterialButton

    // ✅ Dialog se cierra SOLO en Success
    private var createDialog: androidx.appcompat.app.AlertDialog? = null
    private var createDialogType: CreateType? = null

    companion object {
        private const val ARG_ID = "installationId"
        private const val TAG = "InstallationForm"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentFormBaseBinding.bind(view)

        layoutInflater.inflate(
            R.layout.form_installation_fields,
            binding.formFieldsContainer,
            true
        )

        val container = binding.formFieldsContainer

        val etOrder = container.findViewById<TextInputEditText>(R.id.etOrder)
        val etSerie = container.findViewById<TextInputEditText>(R.id.etSerie)
        val etPlate = container.findViewById<TextInputEditText>(R.id.etPlate)
        val etWarehouse = container.findViewById<TextInputEditText>(R.id.etWarehouse)

        val actCondition = container.findViewById<AutoCompleteTextView>(R.id.actCondition)
        val etDate = container.findViewById<TextInputEditText>(R.id.etDate)

        val actHeadquarter = container.findViewById<AutoCompleteTextView>(R.id.actHeadquarter)
        etIncrement = container.findViewById(R.id.etIncrement)

        val actVehicle = container.findViewById<AutoCompleteTextView>(R.id.actVehicle)

        val rvAccessories = container.findViewById<RecyclerView>(R.id.rvAccessories)
        val btnAddAccessory = container.findViewById<MaterialButton>(R.id.btnAddAccessory)

        tgPayment = container.findViewById(R.id.tgPaymentStatus)
        btnPaid = container.findViewById(R.id.btnPaid)
        btnNotPaid = container.findViewById(R.id.btnNotPaid)
        btnPartiallyPaid = container.findViewById(R.id.btnPartiallyPaid)

        val tilOrder = container.findViewById<TextInputLayout>(R.id.tilOrder)
        val tilSerie = container.findViewById<TextInputLayout>(R.id.tilSerie)
        val tilPlate = container.findViewById<TextInputLayout>(R.id.tilPlate)

        val tilDate = container.findViewById<TextInputLayout>(R.id.tilDate)
        val tilHeadquarter = container.findViewById<TextInputLayout>(R.id.tilHeadquarter)
        val tilVehicle = container.findViewById<TextInputLayout>(R.id.tilVehicle)

        etTotalWorked = container.findViewById(R.id.etTotalWorked)
        etPaid = container.findViewById(R.id.etPaid)
        etUnpaid = container.findViewById(R.id.etUnpaid)
        etComment = container.findViewById(R.id.etComment)

        val btnAddHeadquarter = container.findViewById<MaterialButton>(R.id.btnAddHeadquarter)
        val btnAddVehicle = container.findViewById<MaterialButton>(R.id.btnAddVehicle)
        val btnAddAccessoryInline = container.findViewById<MaterialButton>(R.id.btnAddAccessoryInline)

        btnAddHeadquarter.setOnClickListener { showCreateDialog(CreateType.HEADQUARTER, actHeadquarter) }
        btnAddVehicle.setOnClickListener { showCreateDialog(CreateType.VEHICLE, actVehicle) }
        btnAddAccessoryInline.setOnClickListener { showCreateDialog(CreateType.ACCESSORY, null) }

        // ✅ Solo lectura
        makeReadOnly(etTotalWorked)
        makeReadOnly(etPaid)
        makeReadOnly(etUnpaid)

        // ✅ Toggle se maneja automático
        btnPaid.isClickable = false
        btnNotPaid.isClickable = false
        btnPartiallyPaid.isClickable = false

        // ---------- Limpiar errores ----------
        fun clearGroupErrors() {
            clearError(tilOrder); clearError(tilSerie); clearError(tilPlate)
        }
        etOrder.doAfterTextChanged { clearGroupErrors() }
        etSerie.doAfterTextChanged { clearGroupErrors() }
        etPlate.doAfterTextChanged { clearGroupErrors() }

        // ---------- Defaults ----------
        etIncrement.setText("0")
        etIncrement.addTextChangedListener(ThousandsSeparatorTextWatcher(etIncrement))

        etIncrement.doAfterTextChanged {
            updateTotalsUI(currentAccessories, etTotalWorked, etPaid, etUnpaid)
            autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, currentAccessories)
        }

        // ---------- Edit mode ----------
        editId = arguments?.getString(ARG_ID)
        val isEditMode = !editId.isNullOrBlank()

        setTitles(isEditMode)
        binding.btnSave.text = if (isEditMode) "Actualizar" else "Guardar"

        if (isEditMode) {
            viewModel.loadById(editId!!)
        } else {
            val cal = Calendar.getInstance()
            setDateText(etDate, cal)
            viewModel.setDate(fromCalendarToTimestamp(cal))
        }

        val normalText = binding.btnSave.text

        // Uppercase
        etSerie.doAfterTextChanged {
            val text = it?.toString() ?: return@doAfterTextChanged
            val upper = text.uppercase()
            if (text != upper) {
                etSerie.setText(upper)
                etSerie.setSelection(upper.length)
            }
        }

        etPlate.doAfterTextChanged {
            val text = it?.toString() ?: return@doAfterTextChanged
            val upper = text.uppercase()
            if (text != upper) {
                etPlate.setText(upper)
                etPlate.setSelection(upper.length)
            }
        }

        etComment.doAfterTextChanged { viewModel.setComment(it?.toString()) }

        // ---------- Condición ----------
        val conditions = listOf("Nuevo", "Usado", "Taller")
        actCondition.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, conditions)
        )
        actCondition.setOnItemClickListener { _, _, _, _ ->
            hideKeyboardFrom(actCondition)
            actCondition.clearFocus()
        }

        // ---------- Date picker ----------
        etDate.setOnClickListener {
            showDatePicker { cal ->
                setDateText(etDate, cal)
                viewModel.setDate(fromCalendarToTimestamp(cal))
            }
        }

        // ---------- Sede / Vehículo texto libre ----------
        // Si escribe, lo tratamos como libre => id null
        actHeadquarter.doAfterTextChanged {
            val label = it?.toString()?.trim()
            selectedHqId = null
            viewModel.setHeadquarterLabel(label)
        }

        actVehicle.doAfterTextChanged {
            val label = it?.toString()?.trim()
            selectedVehicleId = null
            viewModel.setVehicleLabel(label)
        }

        // ---------- Accessories Recycler ----------
        accessoriesAdapter = InstalledAccessoryAdapter(
            options = emptyList(),
            onChanged = { list ->
                currentAccessories = list
                viewModel.setAccessories(list)

                updateTotalsUI(list, etTotalWorked, etPaid, etUnpaid)
                autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, list)
            }
        )

        rvAccessories.layoutManager = LinearLayoutManager(requireContext())
        rvAccessories.adapter = accessoriesAdapter

        if (!isEditMode) ensureAtLeastOneAccessoryRow()
        btnAddAccessory.setOnClickListener { accessoriesAdapter.addEmpty() }

        // ---------- Toggle: solo para guardar state ----------
        tgPayment.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (isAutoToggling) return@addOnButtonCheckedListener

            val state = when (tgPayment.checkedButtonId) {
                btnPaid.id -> "PAGADO"
                btnNotPaid.id -> "NO_PAGADO"
                btnPartiallyPaid.id -> "PARCIAL"
                else -> null
            }
            viewModel.setPaymentState(state)
        }

        // ---------- Save ----------
        binding.btnSave.setOnClickListener {
            tilOrder.error = null; tilSerie.error = null; tilPlate.error = null

            viewModel.setComment(etComment.text?.toString())

            val hqLabel = actHeadquarter.text?.toString()?.trim()
            val vLabel = actVehicle.text?.toString()?.trim()

            // ✅ si hay id => selection; si no => label libre
            if (!selectedHqId.isNullOrBlank()) {
                viewModel.setHeadquarterSelection(selectedHqId, hqLabel)
            } else {
                viewModel.setHeadquarterLabel(hqLabel)
            }

            if (!selectedVehicleId.isNullOrBlank()) {
                viewModel.setVehicleSelection(selectedVehicleId, vLabel)
            } else {
                viewModel.setVehicleLabel(vLabel)
            }

            val order = etOrder.text?.toString()?.toIntOrNull()
            val serie = etSerie.text?.toString().orEmpty()
            val plate = etPlate.text?.toString().orEmpty()
            val warehouse = etWarehouse.text?.toString().orEmpty()

            val increment = etIncrement.text?.toString().orEmpty()
                .filter { it.isDigit() }
                .toIntOrNull() ?: 0

            val total = totalWorked()
            val paidValue = totalPaid()
            val unPaidValue = totalUnpaid()

//            val rawInc = etIncrement.text?.toString()
//            Log.d("INC_UI", "etIncrement raw='$rawInc' length=${rawInc?.length} viewId=${etIncrement.id}")
//
//            val increment2 = rawInc.orEmpty().filter { it.isDigit() }.toIntOrNull() ?: 0
//            Log.d("INC_UI", "parsed increment=$increment2")
//            showSnack("Guardando")

            viewModel.save(
                id = editId,
                order = order,
                serie = serie,
                plate = plate,
                warehouse = warehouse,
                condition = actCondition.text?.toString(),
                increment = increment,
                paymentValueRaw = paidValue.toString(),
                total = total,
                paidValue = paidValue,
                unPaidValue = unPaidValue
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
                                etOrder.setText("")
                                etSerie.setText("")
                                etPlate.setText("")
                                etWarehouse.setText("")
                                actCondition.setText("", false)
                                actHeadquarter.setText("", false)
                                actVehicle.setText("", false)
                                etIncrement.setText("0")
                                etComment.setText("")

                                selectedHqId = null
                                selectedVehicleId = null
                                viewModel.setHeadquarterLabel(null)
                                viewModel.setVehicleLabel(null)

                                accessoriesAdapter.submitList(listOf(InstalledAccessory()))
                                currentAccessories = accessoriesAdapter.getCurrent()

                                updateTotalsUI(currentAccessories, etTotalWorked, etPaid, etUnpaid)

                                setDateText(etDate, Calendar.getInstance())
                                viewModel.setDate(fromCalendarToTimestamp(Calendar.getInstance()))
                                etOrder.requestFocus()
                            }

                            if (editId.isNullOrBlank()) {
                                findNavController().previousBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                            }
                            findNavController().popBackStack()

                            return@collect
                        }

                        is InstallationFormViewModel.UiState.Error -> {
                            Log.e("INSTALL_SAVE", "Error: ${state.msg}")
                            showSnack(state.msg)
                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }

                        is InstallationFormViewModel.UiState.FieldError -> {
                            Log.e("INSTALL_SAVE", "FieldError field=${state.field} msg=${state.msg}")
                            clearError(tilOrder); clearError(tilSerie); clearError(tilPlate)
                            clearError(tilDate); clearError(tilHeadquarter); clearError(tilVehicle)

                            when (state.field) {
                                "order_serie_plate" ->
                                    setGroupErrorNoText(state.msg, tilOrder, tilSerie, tilPlate)

                                "order" -> markError(tilOrder, state.msg)
                                "serie" -> markError(tilSerie, state.msg)
                                "plate" -> markError(tilPlate, state.msg)
                                "date" -> markError(tilDate, state.msg)
                                "headquarter" -> markError(tilHeadquarter, state.msg)
                                "vehicle" -> markError(tilVehicle, state.msg)

                                "accessories" -> {
                                    showSnack(state.msg)
                                    accessoriesAdapter.showAccessoryRequiredErrorOnFirstRow()
                                    rvAccessories.scrollToPosition(0)
                                }
                            }

                            binding.btnSave.isEnabled = true
                            binding.btnSave.text = normalText
                        }
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
                    etComment.setText(installation.comment.orEmpty())
                    viewModel.setComment(installation.comment.orEmpty())

                    installation.date?.let { ts ->
                        val cal = Calendar.getInstance().apply { time = ts.toDate() }
                        setDateText(etDate, cal)
                        viewModel.setDate(ts)
                    }

                    // ✅ sede / vehículo retrocompatibles (String o Map)
                    val hqLabel = anyToHeadquarterLabel(installation.headquarter)
                    actHeadquarter.setText(hqLabel, false)

                    selectedHqId = installation.headquarterId
                    viewModel.setHeadquarterId(selectedHqId)
                    viewModel.setHeadquarterLabel(hqLabel.ifBlank { null })

                    val vehicleLabel = anyToVehicleLabel(installation.vehicle)
                    actVehicle.setText(vehicleLabel, false)

                    selectedVehicleId = installation.vehicleId
                    viewModel.setVehicleId(selectedVehicleId)
                    viewModel.setVehicleLabel(vehicleLabel.ifBlank { null })
                    val inc = (installation.increment ?: 0L)
                    setTextSafely(etIncrement, formatMoneyDots(inc))

                    val list = installation.accessories.orEmpty()
                    val safeList = list.ifEmpty { listOf(InstalledAccessory()) }
                    accessoriesAdapter.submitList(safeList)
                    currentAccessories = accessoriesAdapter.getCurrent()

                    updateTotalsUI(currentAccessories, etTotalWorked, etPaid, etUnpaid)
                    autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, currentAccessories)
                }
            }
        }

        // ---------- Options + create events ----------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    headquarterViewModel.items.collect { list ->
                        val names = list.map { it.name.orEmpty() }
                        actHeadquarter.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
                        )

                        actHeadquarter.setOnItemClickListener { _, _, idx, _ ->
                            val hq: Headquarter = list[idx]
                            selectedHqId = hq.id
                            viewModel.setHeadquarterSelection(hq.id, hq.name.orEmpty())

                            val inc = hq.increment.toLong()
                            setTextSafely(etIncrement, formatMoneyDots(inc))

                            updateTotalsUI(currentAccessories, etTotalWorked, etPaid, etUnpaid)
                            autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, currentAccessories)

                            hideKeyboardFrom(actHeadquarter)
                            actHeadquarter.clearFocus()
                        }
                    }
                }

                launch {
                    vehicleViewModel.items.collect { list ->
                        val labels = list.map { "${it.make} - ${it.model}" }
                        actVehicle.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                        )

                        actVehicle.setOnItemClickListener { _, _, idx, _ ->
                            val v: Vehicle = list[idx]
                            selectedVehicleId = v.id
                            val label = "${v.make} - ${v.model}"
                            viewModel.setVehicleSelection(v.id, label)

                            hideKeyboardFrom(actVehicle)
                            actVehicle.clearFocus()
                        }
                    }
                }

                launch {
                    accessoryViewModel.items.collect { list ->
                        val opts = list.map {
                            Accessory(
                                id = it.id ?: "",
                                name = it.name ?: "",
                                price = it.price ?: 0
                            )
                        }
                        accessoriesAdapter.updateOptions(opts)
                    }
                }

                launch {
                    viewModel.suggestedIncrement.collect { inc ->
                        inc ?: return@collect
                        if (inc <= 0) return@collect

                        setTextSafely(etIncrement, formatMoneyDots(inc.toLong()))
                        updateTotalsUI(currentAccessories, etTotalWorked, etPaid, etUnpaid)
                        autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, currentAccessories)
                    }
                }

                // ✅ cerrar dialog SOLO en Success
                launch {
                    headquarterViewModel.createEvents.collect { ev ->
                        when (ev) {
                            is HeadquarterViewModel.CreateEvent.Success -> {
                                val hq = ev.headquarter
                                selectedHqId = hq.id
                                viewModel.setHeadquarterSelection(hq.id, hq.name.orEmpty())
                                actHeadquarter.setText(hq.name.orEmpty(), false)

                                val inc = (hq.increment ?: 0).toLong()
                                setTextSafely(etIncrement, formatMoneyDots(inc))

                                updateTotalsUI(currentAccessories, etTotalWorked, etPaid, etUnpaid)
                                autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, currentAccessories)

                                showSnack("Sede creada")
                                if (createDialogType == CreateType.HEADQUARTER) createDialog?.dismiss()
                            }

                            is HeadquarterViewModel.CreateEvent.Error -> showSnack(ev.msg)
                        }
                    }
                }

                launch {
                    vehicleViewModel.createEvents.collect { ev ->
                        when (ev) {
                            is VehicleViewModel.CreateEvent.Success -> {
                                val v = ev.vehicle
                                selectedVehicleId = v.id
                                val label = "${v.make} - ${v.model}"

                                viewModel.setVehicleSelection(v.id, label)
                                actVehicle.setText(label, false)

                                showSnack("Vehículo creado")
                                if (createDialogType == CreateType.VEHICLE) createDialog?.dismiss()
                            }

                            is VehicleViewModel.CreateEvent.Error -> showSnack(ev.msg)
                        }
                    }
                }

                launch {
                    accessoryViewModel.createEvents.collect { ev ->
                        when (ev) {
                            is AccessoryViewModel.CreateEvent.Success -> {
                                showSnack("Accesorio creado")
                                if (createDialogType == CreateType.ACCESSORY) createDialog?.dismiss()
                            }

                            is AccessoryViewModel.CreateEvent.Error -> showSnack(ev.msg)
                        }
                    }
                }
            }
        }
    }

    // -------------------- Totales con incremento --------------------

    private fun getIncrementValue(): Long {
        val raw = etIncrement.text?.toString()?.replace(".", "")?.trim().orEmpty()
        return raw.toLongOrNull() ?: 0L
    }

    private fun selectedAccessoriesOnly(list: List<InstalledAccessory>): List<InstalledAccessory> =
        list.filter {
            val hasId = !it.accessoryId.isNullOrBlank()
            val hasName = !it.name.isNullOrBlank()
            (hasId || hasName) && it.price > 0L
        }

    private fun totalWorked(): Long {
        val inc = getIncrementValue()
        val list = selectedAccessoriesOnly(currentAccessories)
        return list.sumOf { it.price + inc }
    }

    private fun totalPaid(): Long {
        val inc = getIncrementValue()
        val list = selectedAccessoriesOnly(currentAccessories)
        return list.filter { it.isPaid }.sumOf { it.price + inc }
    }

    private fun totalUnpaid(): Long = totalWorked() - totalPaid()

    private fun updateTotalsUI(
        accessories: List<InstalledAccessory>,
        etTotal: TextInputEditText,
        etPaid: TextInputEditText,
        etUnpaid: TextInputEditText,
    ) {
        val inc = getIncrementValue()
        val selected = selectedAccessoriesOnly(accessories)

        val total = selected.sumOf { it.price + inc }
        val paid = selected.filter { it.isPaid }.sumOf { it.price + inc }
        val unpaid = total - paid

        setTextSafely(etTotal, formatMoneyDots(total))
        setTextSafely(etPaid, formatMoneyDots(paid))
        setTextSafely(etUnpaid, formatMoneyDots(unpaid))
    }

    // -------------------- UI helpers --------------------

    private fun makeReadOnly(et: TextInputEditText) {
        et.isFocusable = false
        et.isFocusableInTouchMode = false
        et.isClickable = false
        et.isCursorVisible = false
    }

    private fun ensureAtLeastOneAccessoryRow() {
        val current = accessoriesAdapter.getCurrent()
        if (current.isEmpty()) {
            accessoriesAdapter.submitList(listOf(InstalledAccessory()))
            currentAccessories = accessoriesAdapter.getCurrent()
        }
    }

    private fun autoSetPaymentToggle(
        tg: MaterialButtonToggleGroup,
        btnPaid: MaterialButton,
        btnNotPaid: MaterialButton,
        btnPartiallyPaid: MaterialButton,
        accessories: List<InstalledAccessory>
    ) {
        val selected = selectedAccessoriesOnly(accessories)

        val inc = getIncrementValue()
        val total = selected.sumOf { it.price + inc }
        val paid = selected.filter { it.isPaid }.sumOf { it.price + inc }
        val unpaid = total - paid

        val targetId = when {
            selected.isEmpty() || total <= 0L -> btnNotPaid.id
            paid <= 0L -> btnNotPaid.id
            unpaid <= 0L -> btnPaid.id
            else -> btnPartiallyPaid.id
        }

        if (tg.checkedButtonId == targetId) return

        isAutoToggling = true
        tg.check(targetId)
        isAutoToggling = false

        val state = when (targetId) {
            btnPaid.id -> "PAGADO"
            btnNotPaid.id -> "NO_PAGADO"
            btnPartiallyPaid.id -> "PARCIAL"
            else -> null
        }
        viewModel.setPaymentState(state)
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
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)
        et.setText(String.format("%02d/%02d/%04d", d, m, y))
    }

    private fun fromCalendarToTimestamp(cal: Calendar): Timestamp = Timestamp(cal.time)

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatMoneyDots(value: Long): String {
        val s = value.toString()
        val sb = StringBuilder()
        var count = 0
        for (i in s.length - 1 downTo 0) {
            sb.append(s[i])
            count++
            if (count == 3 && i != 0) {
                sb.append('.')
                count = 0
            }
        }
        return sb.reverse().toString()
    }

    private fun setTextSafely(et: TextInputEditText, value: String) {
        val current = et.text?.toString().orEmpty()
        if (current == value) return
        et.setText(value)
        et.setSelection(et.text?.length ?: 0)
    }

    private fun hideKeyboardFrom(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun setGroupErrorNoText(msg: String, vararg tils: TextInputLayout) {
        tils.forEach { it.isErrorEnabled = true; it.error = msg }
        showSnack(msg)
    }

    private fun markError(til: TextInputLayout, msg: String) {
        til.isErrorEnabled = true
        til.error = msg
        showSnack(msg)
    }

    private fun clearError(til: TextInputLayout) {
        til.error = null
        til.isErrorEnabled = false
    }

    private enum class CreateType { ACCESSORY, HEADQUARTER, VEHICLE }

    private fun showCreateDialog(type: CreateType, targetDropdown: AutoCompleteTextView?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_two_fields, null)

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

        createDialogType = type

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create()

        createDialog = dialog
        dialog.setOnDismissListener {
            createDialog = null
            createDialogType = null
        }

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
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

        dialog.show()
    }

    override fun onStart() {
        super.onStart()
        accessoryViewModel.startListening()
        headquarterViewModel.startListening()
        vehicleViewModel.startListening()
    }

    override fun onStop() {
        super.onStop()
        accessoryViewModel.stopListening()
        headquarterViewModel.stopListening()
        vehicleViewModel.stopListening()
    }

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
