package com.example.accessoriesManager.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.accesorymanager.R
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
            OpenVH(ItemInstallationOpenBinding.inflate(inflater, parent, false))
        } else {
            ClosedVH(ItemInstallationClosedBinding.inflate(inflater, parent, false))
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

    // -------------------- Estado / Color --------------------

    private enum class PayState { PAID, INCOMPLETE, UNPAID }

    private fun normalizePayState(item: Installation): PayState {
        val s = item.state?.trim()?.lowercase().orEmpty()

        return when (s) {
            "paid", "pagado", "pago", "pagada", "pagado_total", "pagado_ok",
            "pagado✅", "pagado_okay", "pagado " -> PayState.PAID

            "incomplete", "incompleto", "abonado", "abono", "partial", "parcial",
            "parcialmente_pagado", "abonado " -> PayState.INCOMPLETE

            "unpaid", "no_pagado", "nopagado", "no pagado", "pendiente", "no_pagada" -> PayState.UNPAID

            // Compatibilidad con tus toggles
            "pagado" -> PayState.PAID
            "no_pagado" -> PayState.UNPAID
            "abonado" -> PayState.INCOMPLETE

            else -> {
                // Fallback por totales (tu UI ya se basa en totalUnpaid)
                val total = item.totalWorked ?: 0L
                val unpaid = item.totalUnpaid ?: 0L
                val paid = item.totalPaid ?: 0L

                when {
                    total <= 0L -> PayState.UNPAID
                    unpaid == 0L -> PayState.PAID
                    paid == 0L -> PayState.UNPAID
                    else -> PayState.INCOMPLETE
                }
            }
        }
    }

    private fun payLabel(ps: PayState): String = when (ps) {
        PayState.PAID -> "Sí"
        PayState.INCOMPLETE -> "Incompleto"
        PayState.UNPAID -> "No"
    }

    private fun bgColorRes(ps: PayState): Int = when (ps) {
        PayState.PAID -> R.color.inst_paid_bg
        PayState.INCOMPLETE -> R.color.inst_incomplete_bg
        PayState.UNPAID -> R.color.inst_unpaid_bg
    }

    // -------------------- CLOSED --------------------

    inner class ClosedVH(
        private val binding: ItemInstallationClosedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Installation, expanded: Boolean) = with(binding) {
            // Clicks
            cardInstallation.setOnClickListener { item.id?.let(onToggleExpand) }
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            btnCheck.setOnClickListener { onMark(item) }

            // Estado + color
            val ps = normalizePayState(item)
            tvPagadoValue.text = payLabel(ps)
            cardInstallation.setCardBackgroundColor(
                ContextCompat.getColor(root.context, bgColorRes(ps))
            )

            // Textos
            tvOrdenValue.text = item.order?.toString() ?: "-"
            tvSerieValue.text = item.serie.orEmpty().ifBlank { "-" }
            tvPlacaValue.text = item.plate.orEmpty().ifBlank { "-" }

            val make = item.vehicle?.make?.trim()
            val model = item.vehicle?.model?.trim()
            tvMarcaModelo.text = listOf(make, model)
                .filter { !it.isNullOrBlank() }
                .joinToString(" ")
                .ifEmpty { "-" }

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

        init {
            // Evita recrear en cada bind
            binding.rvAccessories.layoutManager =
                androidx.recyclerview.widget.LinearLayoutManager(binding.root.context)
            binding.rvAccessories.adapter = accessoriesAdapter
            binding.rvAccessories.setHasFixedSize(false)
            binding.rvAccessories.isNestedScrollingEnabled = false
        }

        fun bind(item: Installation, expanded: Boolean) = with(binding) {
            // Clicks
            cardInstallation.setOnClickListener { item.id?.let(onToggleExpand) }
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
            btnCheck.setOnClickListener { onMark(item) }

            // Estado + color
            val ps = normalizePayState(item)
            tvPagadoValue.text = payLabel(ps)
            cardInstallation.setCardBackgroundColor(
                ContextCompat.getColor(root.context, bgColorRes(ps))
            )

            // Textos
            tvOrdenValue.text = item.order?.toString() ?: "-"
            tvSerieValue.text = item.serie.orEmpty().ifBlank { "-" }
            tvPlacaValue.text = item.plate.orEmpty().ifBlank { "-" }

            val make = item.vehicle?.make?.trim()
            val model = item.vehicle?.model?.trim()
            tvMarcaModelo.text = listOf(make, model)
                .filter { !it.isNullOrBlank() }
                .joinToString(" ")
                .ifEmpty { "-" }

            tvCondicionValue.text = item.condition.orEmpty().ifBlank { "-" }
            tvSedeValue.text = item.headquarter?.name.orEmpty().ifBlank { "-" }
            tvBodegaValue.text = item.warehouse.orEmpty().ifBlank { "-" }

            tvFecha.text = item.date?.toDate()
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDate()
                ?.format(dateFormatter)
                ?: "-"

            tvTotalValue.text = money(item.totalWorked ?: 0L)
            tvTotalPagadoValue.text = money(item.totalPaid ?: 0L)
            tvTotalNoPagadoValue.text = money(item.totalUnpaid ?: 0L)

            // Accesorios
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
