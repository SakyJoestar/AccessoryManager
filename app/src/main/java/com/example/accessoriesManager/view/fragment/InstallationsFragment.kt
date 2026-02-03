package com.example.accessoriesManager.view.fragment

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.accesorymanager.R
import com.example.accesorymanager.databinding.FragmentInstallationsBinding
import com.example.accessoriesManager.ui.showSnack
import com.example.accessoriesManager.adapter.InstallationAdapter
import com.example.accessoriesManager.viewmodel.InstallationViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@AndroidEntryPoint
class InstallationsFragment : Fragment() {

    private var _binding: FragmentInstallationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstallationViewModel by viewModels()
    private lateinit var adapter: InstallationAdapter

    private val statusOptions = listOf("Todos", "Pagado", "No Pagado", "Parcial")

    private val moneyFmt = NumberFormat.getNumberInstance(Locale("es","CO")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    private fun money(v: Long) = "$ ${moneyFmt.format(v)}"

    private val dateFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    private fun LocalDate.formatUi(): String {
        return this.format(dateFormatter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInstallationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = InstallationAdapter(
            onToggleExpand = { id ->
                viewModel.toggleExpanded(id)
                // opcional si tu adapter tiene notifyExpandChanged(id)
                // adapter.notifyExpandChanged(id)
            },
            onEdit = { installation ->
                val bundle = Bundle().apply { putString("installationId", installation.id) }
                findNavController().navigate(
                    R.id.action_installationsFragment_to_installationFormFragment,
                    bundle
                )
            },
            onDelete = { installation ->
                val id = installation.id.orEmpty()
                if (id.isNotBlank()) showDeleteDialog(id)
            },
            onMark = { installation ->
                showMarkAllDialog(installation) // ✅ nuevo
            }
        )

        binding.rvInstallations.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.rvInstallations.adapter = adapter
        binding.rvInstallations.setHasFixedSize(false)

        setupFiltersUi()

        // 🔴 Igual que en AccessoriesFragment
        android.util.Log.d("INSTALLATIONS_FRAG", "onViewCreated -> startListening()")
        viewModel.startListening()

        // ===== LISTA + EXPAND =====
        // Opción 1 (RECOMENDADA): items + expandedIds (si tu VM expone expandedIds)
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            combine(viewModel.items, viewModel.expandedIds) { list, expanded ->
                list to expanded
            }.collect { (list, expanded) ->
                adapter.submitWithExpanded(list, expanded)
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.error.collect { msg ->
                msg?.let { showSnack(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            viewModel.summary.collect { s ->
                binding.includeSummary.tvSummaryTitle.text = s.title

                binding.includeSummary.tvTotalTrabajadoValue.text = money(s.totalWorked)
                binding.includeSummary.tvPagadoValue.text = money(s.totalPaid)
                binding.includeSummary.tvNoPagadoValue.text = money(s.totalUnpaid)

                binding.includeSummary.tvPagadasCount.text = s.paidCount.toString()
                binding.includeSummary.tvIncompletasCount.text = s.partialCount.toString()
                binding.includeSummary.tvNoPagadasCount.text = s.unpaidCount.toString()
            }
        }
    }

    override fun onDestroyView() {
        viewModel.stopListening()
        _binding = null
        super.onDestroyView()
    }

    // -------------------- UI Filtros --------------------

    private fun setupFiltersUi() {
        // Search
        binding.etSearch.doAfterTextChanged {
            viewModel.onQueryChanged(it?.toString().orEmpty())
        }

        // Spinner
// Spinner (Estado)
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            statusOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerStatus.adapter = spinnerAdapter

        binding.spinnerStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selected = statusOptions.getOrNull(position) ?: "Todos"
                viewModel.onStatusFilterChanged(selected)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.etDateExact.setOnClickListener {
            showDatePicker("Fecha exacta") { date ->
                viewModel.setDateExact(date)

                binding.etDateExact.setText(date.formatUi())
                binding.etDateFrom.setText("")
                binding.etDateTo.setText("")
            }
        }


        binding.etDateFrom.setOnClickListener {
            showDatePicker("Desde") { date ->
                val to = viewModel.currentDateTo() ?: date
                viewModel.setDateRange(from = date, to = to)

                binding.etDateFrom.setText(date.formatUi())
                binding.etDateExact.setText("")
            }
        }

        binding.etDateTo.setOnClickListener {
            showDatePicker("Hasta") { date ->
                val from = viewModel.currentDateFrom() ?: date
                viewModel.setDateRange(from = from, to = date)

                binding.etDateTo.setText(date.formatUi())
                binding.etDateExact.setText("")
            }
        }

        // Limpiar
        binding.btnClearDates.setOnClickListener {
            viewModel.clearDates()
            binding.etDateExact.setText("")
            binding.etDateFrom.setText("")
            binding.etDateTo.setText("")
            binding.etSearch.setText("")

            // ✅ Reset de spinner a "Todos"
            binding.spinnerStatus.setSelection(0)
            viewModel.onStatusFilterChanged("Todos")
        }
    }

    private fun formatDate(date: Date): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("America/Bogota")
        return sdf.format(date)
    }

    private fun startOfDay(date: Date): Date {
        val tz = java.util.TimeZone.getTimeZone("America/Bogota")
        return java.util.Calendar.getInstance(tz).apply {
            time = date
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.time
    }

    private fun endOfDay(date: Date): Date {
        val tz = java.util.TimeZone.getTimeZone("America/Bogota")
        return java.util.Calendar.getInstance(tz).apply {
            time = date
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.time
    }

    // 👇 DatePicker que devuelve fecha "segura" (12:00) para que no se corra al día anterior
    private fun showDatePicker(
        title: String,
        onSelected: (LocalDate) -> Unit
    ) {
        val today = LocalDate.now()

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val picked = LocalDate.of(
                    year,
                    month + 1, // DatePicker usa 0-based
                    dayOfMonth
                )
                onSelected(picked)
            },
            today.year,
            today.monthValue - 1,
            today.dayOfMonth
        ).apply {
            setTitle(title)
        }.show()
    }
    // -------------------- Delete dialog --------------------

    private fun showDeleteDialog(id: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar instalación")
            .setMessage("¿Quieres eliminar la instalación?")
            .setPositiveButton("Sí") { _, _ ->
                viewModel.delete(id)
                showSnack("Instalación eliminada ✅")
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showMarkAllDialog(installation: com.example.accessoriesManager.model.Installation) {
        val id = installation.id.orEmpty()
        if (id.isBlank()) return

        // Decide si vamos a marcar como pagados o no pagados
        val state = installation.state?.trim()?.lowercase().orEmpty()
        val isPaid = (installation.totalUnpaid ?: 0L) == 0L
        val targetPaid = !isPaid

        val title = if (targetPaid) "Marcar como pagados" else "Marcar como NO pagados"
        val message = if (targetPaid) {
            "¿Desea marcar como pagados todos los accesorios de esta instalación?"
        } else {
            "¿Desea marcar como NO pagados todos los accesorios de esta instalación?"
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Sí") { _, _ ->
                viewModel.markAllAccessoriesPaid(installationId = id, paid = targetPaid)
            }
            .setNegativeButton("No", null)
            .show()
    }

}
