package com.example.accessoriesManager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.accesorymanager.databinding.ItemInstallationClosedBinding
import com.example.accesorymanager.databinding.ItemInstallationOpenBinding
import com.example.accessoriesManager.model.Installation
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class InstallationAdapter(
    private val onToggleExpand: (String) -> Unit,
    private val onEdit: (Installation) -> Unit,
    private val onDelete: (Installation) -> Unit,
    private val onMark: (Installation) -> Unit,
) : ListAdapter<Installation, RecyclerView.ViewHolder>(Diff) {

    private var expandedIds: Set<String> = emptySet()

    fun submitWithExpanded(list: List<Installation>, expanded: Set<String>) {
        val old = expandedIds
        expandedIds = expanded

        submitList(list) {
            // 🔥 cambia viewType (open/closed) => notifyItemChanged sin payload
            val changed = (old - expandedIds) + (expandedIds - old)
            changed.forEach { id ->
                val pos = currentList.indexOfFirst { it.id == id }
                if (pos != -1) notifyItemChanged(pos)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        val expanded = item.id != null && expandedIds.contains(item.id)
        return if (expanded) VT_OPEN else VT_CLOSED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == VT_OPEN) {
            val b = ItemInstallationOpenBinding.inflate(inflater, parent, false)
            OpenVH(b)
        } else {
            val b = ItemInstallationClosedBinding.inflate(inflater, parent, false)
            ClosedVH(b)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        val expanded = item.id != null && expandedIds.contains(item.id)

        when (holder) {
            is ClosedVH -> holder.bind(item, expanded)
            is OpenVH -> holder.bind(item, expanded)
        }
    }

    // -------------------- CLOSED --------------------

    inner class ClosedVH(
        private val binding: ItemInstallationClosedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Installation, expanded: Boolean) = with(binding) {
            cardInstallation.setOnClickListener { item.id?.let(onToggleExpand) }

            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            btnCheck.setOnClickListener { onMark(item)}

            tvOrdenValue.text = item.order?.toString() ?: "-"
            tvSerieValue.text = item.serie.orEmpty().ifBlank { "-" }
            tvPlacaValue.text = item.plate.orEmpty().ifBlank { "-" }

            val make = item.vehicle?.make?.trim()
            val model = item.vehicle?.model?.trim()
            tvMarcaModelo.text = listOf(make, model)
                .filter { !it.isNullOrBlank() }
                .joinToString(" ")
                .ifEmpty { "-" }

            val unpaid = item.totalUnpaid ?: 0L
            tvPagadoValue.text = if (unpaid == 0L) "Sí" else "No"

            tvFecha.text = item.date?.toDate()
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDate()
                ?.format(dateFormatter)
                ?: "-"

            tvTotalValue.text = money(item.totalWorked ?: 0L)
            tvTotalPagadoValue.text = money(item.totalPaid ?: 0L)
            tvTotalNoPagadoValue.text = money(item.totalUnpaid ?: 0L)
        }
    }

    // -------------------- OPEN --------------------

    inner class OpenVH(
        private val binding: ItemInstallationOpenBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val accessoriesAdapter = OpenAccessoriesAdapter()

        fun bind(item: Installation, expanded: Boolean) = with(binding) {
            cardInstallation.setOnClickListener { item.id?.let(onToggleExpand) }

            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            btnCheck.setOnClickListener { onMark(item)}

            tvOrdenValue.text = item.order?.toString() ?: "-"
            tvSerieValue.text = item.serie.orEmpty().ifBlank { "-" }
            tvPlacaValue.text = item.plate.orEmpty().ifBlank { "-" }

            val make = item.vehicle?.make?.trim()
            val model = item.vehicle?.model?.trim()
            tvMarcaModelo.text = listOf(make, model)
                .filter { !it.isNullOrBlank() }
                .joinToString(" ")
                .ifEmpty { "-" }

            // Condición (si está en tu modelo)
            tvCondicionValue.text = item.condition.orEmpty().ifBlank { "-" }

            // Sede/Bodega (según lo que tengas realmente en tu modelo)
            tvSedeValue.text = item.headquarter?.name.orEmpty().ifBlank { "-" }
            tvBodegaValue.text = item.warehouse.orEmpty().ifBlank { "-" }

            val unpaid = item.totalUnpaid ?: 0L
            tvPagadoValue.text = if (unpaid == 0L) "Sí" else "No"

            tvFecha.text = item.date?.toDate()
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDate()
                ?.format(dateFormatter)
                ?: "-"

            // Totales footer del open
            tvTotalValue.text = money(item.totalWorked ?: 0L)
            tvTotalPagadoValue.text = money(item.totalPaid ?: 0L)
            tvTotalNoPagadoValue.text = money(item.totalUnpaid ?: 0L)

            // Accesorios

            android.util.Log.d(
                "OPEN_ACCESSORIES",
                "id=${item.id} accessories=${item.accessories?.size ?: 0} data=${item.accessories}"
            )

            rvAccessories.layoutManager =
                androidx.recyclerview.widget.LinearLayoutManager(root.context)

            rvAccessories.adapter = accessoriesAdapter
            rvAccessories.setHasFixedSize(false)
            rvAccessories.isNestedScrollingEnabled = false

            accessoriesAdapter.submit(item.accessories)
        }
    }

    private fun money(value: Long): String = "$ ${numberFormatter.format(value)}"

    companion object {
        private const val VT_CLOSED = 0
        private const val VT_OPEN = 1

        private val Diff = object : DiffUtil.ItemCallback<Installation>() {
            override fun areItemsTheSame(oldItem: Installation, newItem: Installation): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Installation, newItem: Installation): Boolean =
                oldItem == newItem
        }

        private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        private val numberFormatter = NumberFormat
            .getNumberInstance(Locale("es", "CO"))
            .apply {
                maximumFractionDigits = 0
                minimumFractionDigits = 0
            }
    }
}
