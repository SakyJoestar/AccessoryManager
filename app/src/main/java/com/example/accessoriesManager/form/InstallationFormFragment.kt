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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentFormBaseBinding
import com.example.accessoriesManager.adapter.InstalledAccessoryAdapter
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.model.Headquarter
import com.example.accessoriesManager.model.InstalledAccessory
import com.example.accessoriesManager.ui.ThousandsSeparatorTextWatcher
import com.example.accessoriesManager.ui.showSnack
import com.example.accessoriesManager.viewmodel.InstallationFormViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class InstallationFormFragment : Fragment(R.layout.fragment_form_base) {

    private var _binding: FragmentFormBaseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstallationFormViewModel by viewModels()

    private var editId: String? = null
    private lateinit var accessoriesAdapter: InstalledAccessoryAdapter

    private var isAutoToggling = false

    var hqCache: List<Headquarter> = emptyList()

    // Mantener accesorios actuales
    private var currentAccessories: List<InstalledAccessory> = emptyList()

    companion object {
        private const val ARG_ID = "installationId"
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
        val etIncrement = container.findViewById<TextInputEditText>(R.id.etIncrement)

        val actVehicle = container.findViewById<AutoCompleteTextView>(R.id.actVehicle)

        val rvAccessories = container.findViewById<RecyclerView>(R.id.rvAccessories)
        val btnAddAccessory = container.findViewById<MaterialButton>(R.id.btnAddAccessory)

        val tgPayment = container.findViewById<MaterialButtonToggleGroup>(R.id.tgPaymentStatus)
        val btnPaid = container.findViewById<MaterialButton>(R.id.btnPaid)
        val btnNotPaid = container.findViewById<MaterialButton>(R.id.btnNotPaid)
        val btnPartiallyPaid = container.findViewById<MaterialButton>(R.id.btnPartiallyPaid)

        // ✅ Totales (según tus IDs)
        val etTotalWorked = container.findViewById<TextInputEditText>(R.id.etTotalWorked) // Total trabajado
        val etPaid = container.findViewById<TextInputEditText>(R.id.etPaid)               // Total pagado
        val etUnpaid = container.findViewById<TextInputEditText>(R.id.etUnpaid)           // Total no pagado

        // ✅ Solo lectura (no se editan a mano)
        makeReadOnly(etTotalWorked)
        makeReadOnly(etPaid)
        makeReadOnly(etUnpaid)

        // ✅ El usuario NO debería tocar el toggle (se maneja automático)
        btnPaid.isClickable = false
        btnNotPaid.isClickable = false
        btnPartiallyPaid.isClickable = false

        // Defaults
        etIncrement.setText("0")
        etIncrement.addTextChangedListener(ThousandsSeparatorTextWatcher(etIncrement))

        // ---------- Edit mode ----------
        editId = arguments?.getString(ARG_ID)
        val isEditMode = !editId.isNullOrBlank()

        setTitles(isEditMode)
        binding.btnSave.text = if (isEditMode) "Actualizar" else "Guardar"

        if (isEditMode) {
            viewModel.loadById(editId!!)
        } else {
            setDateText(etDate, Calendar.getInstance())
        }

        val normalText = binding.btnSave.text

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

        // ---------- Accessories Recycler ----------
        accessoriesAdapter = InstalledAccessoryAdapter(
            options = emptyList(),
            onChanged = { list ->
                currentAccessories = list
                viewModel.setAccessories(list)

                // ✅ 1) Actualizar los 3 totales SIEMPRE
                updateTotalsUI(list, etTotalWorked, etPaid, etUnpaid)

                // ✅ 2) Auto-toggle según checkboxes
                autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, list)
            }
        )

        rvAccessories.layoutManager = LinearLayoutManager(requireContext())
        rvAccessories.adapter = accessoriesAdapter

        if (!isEditMode) {
            ensureAtLeastOneAccessoryRow()
        }

        btnAddAccessory.setOnClickListener {
            accessoriesAdapter.addEmpty()
        }

        //Mayusculas
        etSerie.doAfterTextChanged {
            val up = it?.toString()?.uppercase().orEmpty()
            if (up != it.toString()) etSerie.setText(up).also { etSerie.setSelection(up.length) }
        }

        etPlate.doAfterTextChanged {
            val up = it?.toString()?.uppercase().orEmpty()
            if (up != it.toString()) etPlate.setText(up).also { etPlate.setSelection(up.length) }
        }

        // ---------- Toggle: solo para guardar state ----------
        tgPayment.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (isAutoToggling) return@addOnButtonCheckedListener

            val state = when (tgPayment.checkedButtonId) {
                btnPaid.id -> "PAGADO"
                btnNotPaid.id -> "NO_PAGADO"
                btnPartiallyPaid.id -> "ABONADO"
                else -> null
            }
            viewModel.setPaymentState(state)
        }

        // ---------- Save ----------
        binding.btnSave.setOnClickListener {
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

            // ✅ Totales calculados por checkboxes
            val total = totalWorked()
            val paidValue = totalPaid()
            val unPaidValue = totalUnpaid()

            viewModel.save(
                id = editId,
                order = order,
                serie = serie,
                plate = plate,
                warehouse = warehouse,
                condition = actCondition.text?.toString(),
                increment = increment,
                paymentValueRaw = paidValue.toString(), // (si lo sigues usando, aquí va lo pagado)
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

                                accessoriesAdapter.submitList(listOf(InstalledAccessory()))
                                currentAccessories = accessoriesAdapter.getCurrent()

                                // ✅ recalcular totales en limpio
                                updateTotalsUI(currentAccessories, etTotalWorked, etPaid, etUnpaid)

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

                        // ✅ AQUÍ VA TU BLOQUE
                        is InstallationFormViewModel.UiState.FieldError -> {
                            when (state.field) {
                                "headquarter" -> actHeadquarter.error = state.msg
                                "vehicle" -> actVehicle.error = state.msg
                                "date" -> etDate.error = state.msg
                                "order" -> etOrder.error = state.msg
                                "serie" -> etSerie.error = state.msg
                                "plate" -> etPlate.error = state.msg
                                "warehouse" -> etWarehouse.error = state.msg

                                "order_serie_plate" -> {
                                    etOrder.error = state.msg
                                    etSerie.error = state.msg
                                    etPlate.error = state.msg
                                }
                            }

                            // por si venías de Saving
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

                    installation.date?.let { ts ->
                        val cal = Calendar.getInstance().apply { time = ts.toDate() }
                        setDateText(etDate, cal)
                    }

                    actHeadquarter.setText(installation.headquarter?.name.orEmpty(), false)
                    val inc = installation.headquarter?.increment ?: 0
                    setTextSafely(etIncrement, formatMoneyDots(inc.toLong()))

                    val mk = installation.vehicle?.make.orEmpty()
                    val md = installation.vehicle?.model.orEmpty()
                    actVehicle.setText(if (mk.isNotBlank() && md.isNotBlank()) "$mk - $md" else md, false)

                    val list = installation.accessories.orEmpty()
                    val safeList = if (list.isEmpty()) listOf(InstalledAccessory()) else list
                    accessoriesAdapter.submitList(safeList)
                    currentAccessories = accessoriesAdapter.getCurrent()

                    // ✅ forzar cálculo + auto toggle
                    updateTotalsUI(currentAccessories, etTotalWorked, etPaid, etUnpaid)
                    autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, currentAccessories)
                }
            }
        }

        // ---------- Options: sedes, vehículos, accesorios ----------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.headquarters.collect { list ->
                        val names = list.map { it.name ?: "" }
                        actHeadquarter.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
                        )

                        actHeadquarter.setOnItemClickListener { _, _, idx, _ ->
                            val hq = list[idx]
                            viewModel.setHeadquarter(hq)

                            val incHq = hq.increment ?: 0
                            setTextSafely(etIncrement, formatMoneyDots(incHq.toLong()))
                            viewModel.loadIncrementForHeadquarter(hq.id ?: "", incHq)

                            hideKeyboardFrom(actHeadquarter)
                            actHeadquarter.clearFocus()
                        }
                    }
                }

                launch {
                    viewModel.vehicles.collect { list ->
                        Log.d("VEHICLES", "vehicles size = ${list.size}")
                        val labels = list.map { "${it.make} - ${it.model}" }

                        actVehicle.setAdapter(
                            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
                        )

                        actVehicle.setOnItemClickListener { _, _, idx, _ ->
                            viewModel.setVehicle(list[idx])
                            hideKeyboardFrom(actVehicle)
                            actVehicle.clearFocus()
                        }
                    }
                }

                launch {
                    viewModel.accessories.collect { list ->
                        val opts = list.map {
                            Accessory(
                                id = it.id ?: "",
                                name = it.name ?: "",
                                price = it.price ?: 0
                            )
                        }

                        val current = accessoriesAdapter.getCurrent()
                        val safeCurrent = if (current.isEmpty()) listOf(InstalledAccessory()) else current

                        accessoriesAdapter = InstalledAccessoryAdapter(opts) { updated ->
                            currentAccessories = updated
                            viewModel.setAccessories(updated)

                            updateTotalsUI(updated, etTotalWorked, etPaid, etUnpaid)
                            autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, updated)
                        }

                        rvAccessories.adapter = accessoriesAdapter
                        accessoriesAdapter.submitList(safeCurrent)
                        currentAccessories = accessoriesAdapter.getCurrent()

                        ensureAtLeastOneAccessoryRow()

                        updateTotalsUI(currentAccessories, etTotalWorked, etPaid, etUnpaid)
                        autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, currentAccessories)
                    }
                }

                launch {
                    viewModel.suggestedIncrement.collect { inc ->
                        inc ?: return@collect
                        if (inc <= 0) return@collect
                        setTextSafely(etIncrement, formatMoneyDots(inc.toLong()))
                    }
                }
            }
        }
    }

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

    // ✅ Totales reales
    private fun totalWorked(): Long = currentAccessories.sumOf { it.price ?: 0L }
    private fun totalPaid(): Long = currentAccessories.filter { it.isPaid }.sumOf { it.price ?: 0L }
    private fun totalUnpaid(): Long = totalWorked() - totalPaid()

    private fun updateTotalsUI(
        accessories: List<InstalledAccessory>,
        etTotal: TextInputEditText,
        etPaid: TextInputEditText,
        etUnpaid: TextInputEditText
    ) {
        val total = accessories.sumOf { it.price ?: 0L }
        val paid = accessories.filter { it.isPaid }.sumOf { it.price ?: 0L }
        val unpaid = total - paid

        setTextSafely(etTotal, formatMoneyDots(total))
        setTextSafely(etPaid, formatMoneyDots(paid))
        setTextSafely(etUnpaid, formatMoneyDots(unpaid))
    }

    private fun autoSetPaymentToggle(
        tg: MaterialButtonToggleGroup,
        btnPaid: MaterialButton,
        btnNotPaid: MaterialButton,
        btnPartiallyPaid: MaterialButton,
        accessories: List<InstalledAccessory>
    ) {
        val paidCount = accessories.count { it.isPaid }
        val targetId = when {
            accessories.isEmpty() || paidCount == 0 -> btnNotPaid.id
            paidCount == accessories.size -> btnPaid.id
            else -> btnPartiallyPaid.id
        }

        if (tg.checkedButtonId == targetId) return

        isAutoToggling = true
        tg.check(targetId)
        isAutoToggling = false

        val state = when (targetId) {
            btnPaid.id -> "PAGADO"
            btnNotPaid.id -> "NO_PAGADO"
            btnPartiallyPaid.id -> "ABONADO"
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
}
