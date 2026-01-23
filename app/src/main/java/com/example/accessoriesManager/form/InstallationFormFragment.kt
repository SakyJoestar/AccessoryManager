package com.example.accessoriesManager.form

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
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

    private var editId: String? = null

    private lateinit var accessoriesAdapter: InstalledAccessoryAdapter

    var hqCache: List<Headquarter> = emptyList()

    // ✅ Mantener accesorios actuales para calcular total
    private var currentAccessories: List<InstalledAccessory> = emptyList()

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

        val tgPayment = container.findViewById<MaterialButtonToggleGroup>(R.id.tgPaymentStatus)
        val btnPaid = container.findViewById<MaterialButton>(R.id.btnPaid)
        val btnNotPaid = container.findViewById<MaterialButton>(R.id.btnNotPaid)
        val btnPartiallyPaid = container.findViewById<MaterialButton>(R.id.btnPartiallyPaid)

        btnPaid.isClickable = false
        btnNotPaid.isClickable = false
        btnPartiallyPaid.isClickable = false

        val tilPaymentValue = container.findViewById<TextInputLayout>(R.id.tilPaymentValue)
        val etPaymentValue = container.findViewById<TextInputEditText>(R.id.etPaymentValue)

        // ✅ SIEMPRE VISIBLE
        tilPaymentValue.visibility = View.VISIBLE

        // watcher de miles
        etPaymentValue.addTextChangedListener(ThousandsSeparatorTextWatcher(etPaymentValue))

        // Defaults
        etIncrement.setText("0")
        // (lo setea la regla del toggle más abajo)

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
        val conditions = listOf("Nuevo", "Usado", "Taller")
        val conditionAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            conditions
        )
        actCondition.setAdapter(conditionAdapter)

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

                // ✅ 1) Auto-seleccionar toggle según checkboxes
                autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, list)

                // ✅ 2) Aplicar regla del total según toggle (tu lógica actual)
                applyPaymentRule(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, etPaymentValue)
            }
        )

        rvAccessories.layoutManager = LinearLayoutManager(requireContext())
        rvAccessories.adapter = accessoriesAdapter

        // ✅ Siempre iniciar con 1 fila si NO es edición
        if (!isEditMode) {
            ensureAtLeastOneAccessoryRow()
        }

        btnAddAccessory.setOnClickListener {
            accessoriesAdapter.addEmpty()
        }

        // ---------- Payment status (toggle exclusive) ----------
        tgPayment.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            // ✅ Aplicar regla UI
            applyPaymentRule(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, etPaymentValue)

            // Guardamos string state en VM según selección
            val state = when (tgPayment.checkedButtonId) {
                btnPaid.id -> "PAGADO"
                btnNotPaid.id -> "NO_PAGADO"
                btnPartiallyPaid.id -> "ABONADO"
                else -> null
            }
            viewModel.setPaymentState(state)
        }

        // ✅ regla inicial (si ya hay uno marcado por default)
        applyPaymentRule(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, etPaymentValue)

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

            // ✅ Si está vacío (ABONADO), normalizamos a 0 (o ajusta en VM si quieres null)
            val paymentValueRaw = etPaymentValue.text?.toString()?.replace(".", "")?.trim().orEmpty()
            val paymentValueNormalized = if (paymentValueRaw.isBlank()) "0" else paymentValueRaw

            viewModel.save(
                id = editId,
                order = order,
                serie = serie,
                plate = plate,
                warehouse = warehouse,
                condition = actCondition.text?.toString(),
                increment = increment,
                paymentValueRaw = paymentValueNormalized
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

                                // ✅ NO dejar vacío: dejar 1 fila siempre
                                accessoriesAdapter.submitList(listOf(InstalledAccessory()))
                                currentAccessories = accessoriesAdapter.getCurrent()

                                // Estado pago a NO_PAGADO y aplicar regla (pone 0)
                                tgPayment.check(btnNotPaid.id)
                                applyPaymentRule(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, etPaymentValue)

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

                    // sede / vehiculo
                    actHeadquarter.setText(installation.headquarter?.name.orEmpty(), false)
                    val inc = installation.headquarter?.increment ?: 0
                    setTextSafely(etIncrement, formatMoneyDots(inc.toLong()))

                    val mk = installation.vehicle?.make.orEmpty()
                    val md = installation.vehicle?.model.orEmpty()
                    actVehicle.setText(if (mk.isNotBlank() && md.isNotBlank()) "$mk - $md" else md, false)

                    // accesorios
                    val list = installation.accessories.orEmpty()
                    val safeList = if (list.isEmpty()) listOf(InstalledAccessory()) else list
                    accessoriesAdapter.submitList(safeList)
                    currentAccessories = accessoriesAdapter.getCurrent()
                    autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, currentAccessories)
                    applyPaymentRule(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, etPaymentValue)

//                    // estado pago
//                    when (installation.state) {
//                        "PAGADO" -> tgPayment.check(btnPaid.id)
//                        "ABONADO" -> tgPayment.check(btnPartiallyPaid.id)
//                        else -> tgPayment.check(btnNotPaid.id)
//                    }

                    // ✅ aplicar regla UI según estado (pagado suma / no pagado 0 / abonado vacío)
                    applyPaymentRule(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, etPaymentValue)
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
                            val hq = list[idx]
                            viewModel.setHeadquarter(hq)

                            // ✅ 1) Rellenar INMEDIATO con el incremento de la sede
                            val incHq = hq.increment ?: 0
                            setTextSafely(etIncrement, formatMoneyDots(incHq.toLong()))

                            // ✅ 2) (Opcional) pedir al VM el último incremento usado en instalaciones
                            viewModel.loadIncrementForHeadquarter(hq.id ?: "", incHq)

                            hideKeyboardFrom(actHeadquarter)
                            actHeadquarter.clearFocus()
                        }
                    }
                }

                launch {
                    viewModel.vehicles.collect { list ->
                        android.util.Log.d("VEHICLES", "vehicles size = ${list.size}")
                        val labels = list.map { "${it.make} - ${it.model}" }
                        val vAdapter = android.widget.ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            labels
                        )
                        actVehicle.setAdapter(vAdapter)

                        actVehicle.setOnItemClickListener { _, _, idx, _ ->
                            viewModel.setVehicle(list[idx])
                            hideKeyboardFrom(actVehicle)
                            actVehicle.clearFocus()
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

                        // mantener lo que ya estaba escrito
                        val current = accessoriesAdapter.getCurrent()
                        val safeCurrent = if (current.isEmpty()) listOf(InstalledAccessory()) else current

                        accessoriesAdapter = InstalledAccessoryAdapter(opts) { updated ->
                            currentAccessories = updated
                            viewModel.setAccessories(updated)

                            autoSetPaymentToggle(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, updated)
                            applyPaymentRule(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, etPaymentValue)
                        }
                        rvAccessories.adapter = accessoriesAdapter
                        accessoriesAdapter.submitList(safeCurrent)
                        currentAccessories = accessoriesAdapter.getCurrent()

                        ensureAtLeastOneAccessoryRow()

                        // ✅ si está en PAGADO, recalcular con las nuevas options/lista
                        applyPaymentRule(tgPayment, btnPaid, btnNotPaid, btnPartiallyPaid, etPaymentValue)
                    }
                }

                launch {
                    viewModel.suggestedIncrement.collect { inc ->
                        inc ?: return@collect

                        // ✅ Si llega 0, ignorarlo (evita que pise el incremento real)
                        if (inc <= 0) return@collect

                        setTextSafely(etIncrement, formatMoneyDots(inc.toLong()))
                    }
                }
            }
        }
    }

    /**
     * ✅ Garantiza que el recycler tenga al menos 1 card.
     */
    private fun ensureAtLeastOneAccessoryRow() {
        val current = accessoriesAdapter.getCurrent()
        if (current.isEmpty()) {
            accessoriesAdapter.submitList(listOf(InstalledAccessory()))
            currentAccessories = accessoriesAdapter.getCurrent()
        }
    }

    /**
     * ✅ Total de accesorios (sumatoria precios)
     */
    private fun accessoriesTotal(): Long {
        return currentAccessories.sumOf { it.price ?: 0L }
    }

    /**
     * ✅ Regla UI del campo Total instalación
     */
    private fun applyPaymentRule(
        tgPayment: MaterialButtonToggleGroup,
        btnPaid: MaterialButton,
        btnNotPaid: MaterialButton,
        btnPartiallyPaid: MaterialButton,
        etPaymentValue: TextInputEditText
    ) {
        when (tgPayment.checkedButtonId) {
            btnPaid.id -> {
                val total = accessoriesTotal()
                setTextSafely(etPaymentValue, formatMoneyDots(total))
            }

            btnNotPaid.id -> {
                setTextSafely(etPaymentValue, "0")
            }

            btnPartiallyPaid.id -> {
                val paidTotal = paidAccessoriesTotal()
                setTextSafely(etPaymentValue, formatMoneyDots(paidTotal))
            }

            else -> {
                // si no hay nada seleccionado (por alguna razón), lo dejamos en 0
                setTextSafely(etPaymentValue, "0")
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
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val m = cal.get(Calendar.MONTH) + 1
        val y = cal.get(Calendar.YEAR)

        et.setText(String.format("%02d/%02d/%04d", d, m, y))
    }

    private fun fromCalendarToTimestamp(cal: Calendar): Timestamp {
        return Timestamp(cal.time)
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /* ---------- Helpers UI ---------- */

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
        val imm = requireContext()
            .getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun autoSetPaymentToggle(
        tg: MaterialButtonToggleGroup,
        btnPaid: MaterialButton,
        btnNotPaid: MaterialButton,
        btnPartiallyPaid: MaterialButton,
        accessories: List<InstalledAccessory>
    ) {
        if (accessories.isEmpty()) {
            // si no hay accesorios, lo más lógico: NO
            if (tg.checkedButtonId != btnNotPaid.id) tg.check(btnNotPaid.id)
            return
        }

        val paidCount = accessories.count { it.isPaid }
        val targetId = when {
            paidCount == 0 -> btnNotPaid.id
            paidCount == accessories.size -> btnPaid.id
            else -> btnPartiallyPaid.id
        }

        // ✅ Evitar re-check innecesario (y loops)
        if (tg.checkedButtonId != targetId) {
            tg.check(targetId)
        }
    }

    private fun paidAccessoriesTotal(): Long {
        return currentAccessories
            .filter { it.isPaid }
            .sumOf { it.price ?: 0L }
    }

}
