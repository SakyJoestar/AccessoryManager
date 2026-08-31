package com.example.accessoriesmanager.view.fragment

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import android.transition.AutoTransition
import android.transition.TransitionManager
import com.example.accessoriesmanager.R
import com.example.accessoriesmanager.databinding.FragmentInstallationsBinding
import com.example.accessoriesmanager.adapter.InstallationAdapter
import com.example.accessoriesmanager.report.ExportResult
import com.example.accessoriesmanager.report.ReportFilter
import com.example.accessoriesmanager.report.ReportRange
import com.example.accessoriesmanager.ui.showSnack
import com.example.accessoriesmanager.viewmodel.InstallationViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@AndroidEntryPoint
class InstallationsFragment : Fragment() {

    private var _binding: FragmentInstallationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstallationViewModel by viewModels()
    private lateinit var adapter: InstallationAdapter

    private val statusOptions = listOf("Todos", "Pagado", "No Pagado", "Parcial")

    private val moneyFmt = NumberFormat.getNumberInstance(Locale("es", "CO")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }

    private fun money(v: Long) = "$ ${moneyFmt.format(v)}"

    private val dateFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    private fun LocalDate.formatUi(): String = this.format(dateFormatter)

    // Scroll al volver del form
    private var pendingScrollToTop = false

    // Summary toggle
    private var summaryOpen = false

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

        // -------------------- Adapter --------------------
        adapter = InstallationAdapter(
            onToggleExpand = { id -> viewModel.toggleExpanded(id) },
            onEdit = { installation ->
                val bundle = Bundle().apply {
                    putString("installationId", installation.id)
                }
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
                showMarkAllDialog(installation)
            }
        )

        binding.rvInstallations.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.rvInstallations.adapter = adapter
        binding.rvInstallations.setHasFixedSize(false)

        setupFiltersUi()

        binding.btnExportReport.setOnClickListener { showExportDialog() }

        // -------------------- Summary toggle --------------------
        val summary = binding.includeSummary
        summary.sectionInstallations.isVisible = false
        summaryOpen = false

        summary.cardSummary.setOnClickListener {
            summaryOpen = !summaryOpen
            TransitionManager.beginDelayedTransition(
                summary.cardSummary,
                AutoTransition()
            )
            summary.sectionInstallations.isVisible = summaryOpen
        }

        // -------------------- Listener realtime --------------------
        viewModel.startListening()

        // Flag scroll al crear
        val navController = findNavController()
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<Boolean>("scrollToTop")
            ?.observe(viewLifecycleOwner) { shouldScroll ->
                if (shouldScroll == true) {
                    pendingScrollToTop = true
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.remove<Boolean>("scrollToTop")
                }
            }

        // -------------------- Lista + expand + scroll --------------------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.items, viewModel.expandedIds) { list, expanded ->
                    list to expanded
                }.collect { (list, expanded) ->
                    adapter.submitWithExpanded(list, expanded) {
                        if (pendingScrollToTop) {
                            pendingScrollToTop = false
                            binding.rvInstallations.scrollToPosition(0)
                        }
                    }
                }
            }
        }

        // -------------------- Errores --------------------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { msg ->
                    msg?.let { showSnack(it) }
                }
            }
        }

        // -------------------- Filtro bloqueado offline --------------------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.offlineFilterBlocked.collect {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Sin conexión")
                        .setMessage(
                            "No se pueden buscar instalaciones más antiguas que las últimas 10 " +
                                "guardadas localmente. Conéctate a internet para ver el historial completo."
                        )
                        .setPositiveButton("Entendido", null)
                        .show()
                }
            }
        }

        // -------------------- Reporte exportado --------------------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportResult.collect { result ->
                    Snackbar.make(
                        binding.root,
                        "Guardado en ${result.folder}/${result.fileName}",
                        Snackbar.LENGTH_LONG
                    ).setAction("Compartir") {
                        shareExcelFile(result.uri, result.mimeType)
                    }.show()
                }
            }
        }

        // -------------------- Summary data --------------------
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.summary.collect { s ->
                    summary.tvSummaryTitle.text = s.title
                    summary.tvTotalTrabajadoValue.text = money(s.totalWorked)
                    summary.tvPagadoValue.text = money(s.totalPaid)
                    summary.tvNoPagadoValue.text = money(s.totalUnpaid)

                    summary.tvPagadasCount.text = s.paidCount.toString()
                    summary.tvIncompletasCount.text = s.partialCount.toString()
                    summary.tvNoPagadasCount.text = s.unpaidCount.toString()
                }
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
        binding.etSearch.doAfterTextChanged {
            viewModel.onQueryChanged(it?.toString().orEmpty())
        }

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            statusOptions
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerStatus.adapter = spinnerAdapter
        binding.spinnerStatus.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.onStatusFilterChanged(statusOptions[position])
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

        binding.btnClearDates.setOnClickListener {
            viewModel.clearDates()
            binding.etDateExact.setText("")
            binding.etDateFrom.setText("")
            binding.etDateTo.setText("")
            binding.etSearch.setText("")
            binding.spinnerStatus.setSelection(0)
            viewModel.onStatusFilterChanged("Todos")
        }
    }

    private fun showDatePicker(
        title: String,
        onSelected: (LocalDate) -> Unit
    ) {
        val today = LocalDate.now()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                onSelected(LocalDate.of(year, month + 1, day))
            },
            today.year,
            today.monthValue - 1,
            today.dayOfMonth
        ).apply { setTitle(title) }.show()
    }

    // -------------------- Exportar Excel --------------------

    private val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    private fun showExportDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_export_report, null)
        val rgRange = view.findViewById<RadioGroup>(R.id.rgExportRange)
        val containerMonth = view.findViewById<View>(R.id.containerExportMonth)
        val spinnerMonth = view.findViewById<Spinner>(R.id.spinnerExportMonth)
        val spinnerYear = view.findViewById<Spinner>(R.id.spinnerExportYear)
        val spinnerStatus = view.findViewById<Spinner>(R.id.spinnerExportStatus)

        val today = LocalDate.now()

        spinnerMonth.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, monthNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerMonth.setSelection(today.monthValue - 1)

        val years = (today.year downTo today.year - 4).toList()
        spinnerYear.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, years
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerStatus.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, statusOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinnerStatus.setSelection(statusOptions.indexOf(binding.spinnerStatus.selectedItem as? String).coerceAtLeast(0))

        rgRange.setOnCheckedChangeListener { _, checkedId ->
            containerMonth.isVisible = checkedId == R.id.rbExportMonth
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Exportar a Excel")
            .setView(view)
            .setPositiveButton("Generar") { _, _ ->
                val range = when (rgRange.checkedRadioButtonId) {
                    R.id.rbExportAll -> ReportRange.All
                    R.id.rbExportMonth -> {
                        val year = spinnerYear.selectedItem as Int
                        val month = spinnerMonth.selectedItemPosition + 1
                        ReportRange.Month(year, month)
                    }
                    else -> {
                        val exact = viewModel.currentDateExact()
                        val from = exact ?: viewModel.currentDateFrom()
                        val to = exact ?: viewModel.currentDateTo()
                        if (from != null && to != null) ReportRange.Range(from, to) else ReportRange.All
                    }
                }
                val status = spinnerStatus.selectedItem as String
                viewModel.exportReport(ReportFilter(range, status))
                showSnack("Generando reporte…")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun shareExcelFile(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartir reporte"))
    }

    // -------------------- Dialogs --------------------

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

    private fun showMarkAllDialog(installation: com.example.accessoriesmanager.model.Installation) {
        val id = installation.id.orEmpty()
        if (id.isBlank()) return

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
                viewModel.markAllAccessoriesPaid(id, targetPaid)
            }
            .setNegativeButton("No", null)
            .show()
    }
}
