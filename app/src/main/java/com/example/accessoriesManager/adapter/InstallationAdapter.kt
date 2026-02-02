package com.example.accessoriesManager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.accesorymanager.databinding.ItemInstallationClosedBinding
import com.example.accessoriesManager.model.Installation
import com.example.accessoriesManager.model.Vehicle
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class InstallationAdapter(
    private val onToggleExpand: (String) -> Unit,
    private val onEdit: (Installation) -> Unit,
    private val onDelete: (Installation) -> Unit
) : ListAdapter<Installation, InstallationAdapter.VH>(Diff) {

    private var expandedIds: Set<String> = emptySet()

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")



    /** Llama esto desde el Fragment al colectar el state del VM */
    fun submitWithExpanded(list: List<Installation>, expanded: Set<String>) {
        expandedIds = expanded
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemInstallationClosedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        android.util.Log.d("INSTALLATIONS_ADAPTER", "bind pos=$position id=${getItem(position).id}")
        holder.bind(getItem(position))
    }

    inner class VH(
        private val b: ItemInstallationClosedBinding
    ) : RecyclerView.ViewHolder(b.root) {

        fun bind(item: Installation) = with(b) {
            val id = item.id
            val expanded = id != null && expandedIds.contains(id)

            // Click en toda la card
            cardInstallation.setOnClickListener {
                id?.let(onToggleExpand)
            }

            // Acciones siempre visibles
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }

            // ====== Binds de texto ======
            tvOrdenValue.text = item.order?.toString() ?: "-"
            tvSerieValue.text = item.serie ?: "-"
            tvPlacaValue.text = item.plate ?: "-"

            tvMarcaModelo.text = item.vehicle?.displayName ?: "-"

            val unpaid = item.totalUnpaid ?: 0L
            tvPagadoValue.text = if (unpaid > 0L) "No" else "Sí"

            val formattedDate = item.date
                ?.toDate()
                ?.toInstant()
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDate()
                ?.format(formatter)
                ?: "-"

            tvFecha.text = formattedDate

            tvTotalValue.text = money(item.totalWorked ?: 0L)
            tvTotalPagadoValue.text = money(item.totalPaid ?: 0L)
            tvTotalNoPagadoValue.text = money(unpaid)

            // ====== Expand/Collapse visual ======
            // Si agregas layoutDetails en el XML, aquí lo controlas:
            if (hasDetailsContainer()) {
                layoutDetails.visibility = if (expanded) View.VISIBLE else View.GONE
            }

            // Si quieres cambiar el icono del botón según expandido:
            // (asumiendo que tienes dos drawables: ic_expand_more / ic_expand_less)
            // btnExpand.setImageResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }

        /** Evita crashear si aún no agregaste layoutDetails */
        private fun hasDetailsContainer(): Boolean {
            return try {
                b.layoutDetails
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<Installation>() {
            override fun areItemsTheSame(oldItem: Installation, newItem: Installation): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Installation, newItem: Installation): Boolean =
                oldItem == newItem
        }

        private val currency: NumberFormat =
            NumberFormat.getCurrencyInstance(Locale("es", "CO"))

        private fun money(value: Long): String = currency.format(value)

        private val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        private fun Timestamp?.formatShortDate(): String {
            if (this == null) return "-"
            return df.format(this.toDate())
        }

        private fun buildMarcaModelo(item: Installation): String {
            // Ajusta según tu modelo real Vehicle
            val v = item.vehicle ?: return "-"
            // Si Vehicle tiene brand/model, cámbialo a esos campos
            return v.toString()
        }
    }
}
