package com.example.accessoriesManager.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.example.accesorymanager.databinding.ItemAccessoryRowBinding
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.model.InstalledAccessory
import com.example.accessoriesManager.ui.ThousandsSeparatorTextWatcher
import com.google.android.material.textfield.TextInputEditText

class InstalledAccessoryAdapter(
    private val options: List<Accessory>,
    private val onChanged: (List<InstalledAccessory>) -> Unit
) : RecyclerView.Adapter<InstalledAccessoryAdapter.VH>() {

    private val items = mutableListOf<InstalledAccessory>()

    fun submitList(list: List<InstalledAccessory>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
        onChanged(items.toList())
    }

    fun getCurrent(): List<InstalledAccessory> = items.toList()

    fun addEmpty() {
        items.add(InstalledAccessory(quantity = 1, isPaid = false, total = 0))
        notifyItemInserted(items.lastIndex)
        onChanged(items.toList())
    }

    fun removeAt(position: Int) {
        if (position !in items.indices) return
        items.removeAt(position)
        notifyItemRemoved(position)
        onChanged(items.toList())
    }

    inner class VH(val binding: ItemAccessoryRowBinding) : RecyclerView.ViewHolder(binding.root) {
        // Watchers para evitar duplicados por reciclaje
        var priceWatcher: ThousandsSeparatorTextWatcher? = null
        var qtyWatcherAttached = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAccessoryRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = holder.binding
        val item = items[position]

        // ---------- Dropdown ----------
        val names = options.map { it.name }
        val adapter = ArrayAdapter(b.root.context, android.R.layout.simple_list_item_1, names)
        b.actAccessory.setAdapter(adapter)
        b.actAccessory.setText(item.name.orEmpty(), false)

        // ---------- Precio (derivamos un "unitPrice" temporal) ----------
        val unitPrice = inferUnitPrice(item.total, item.quantity)
        setTextSafely(b.etPrice, unitPrice.toString())

        // Formateo miles (evitar añadir múltiples watchers)
        holder.priceWatcher?.let { b.etPrice.removeTextChangedListener(it) }
        holder.priceWatcher = ThousandsSeparatorTextWatcher(b.etPrice)
        b.etPrice.addTextChangedListener(holder.priceWatcher)

        // ---------- Cantidad ----------
        setTextSafely(b.etQty, item.quantity.toString())

        // ---------- Check pagado ----------
        b.chkPaid.setOnCheckedChangeListener(null)
        b.chkPaid.isChecked = item.isPaid
        b.chkPaid.setOnCheckedChangeListener { _, checked ->
            updateItem(position, item.copy(isPaid = checked))
        }

        // ---------- Total (UI) ----------
        b.tvTotal.text = "Total: ${item.total}"

        // ---------- Listeners (precio / qty) ----------
        // Usamos doAfterTextChanged (simple) pero cuidando loops con setTextSafely

        b.etPrice.doAfterTextChanged {
            if (holder.adapterPosition == RecyclerView.NO_POSITION) return@doAfterTextChanged

            val price = parseIntClean(b.etPrice.text?.toString())
            val qty = parseIntClean(b.etQty.text?.toString(), default = 1).coerceAtLeast(1)

            val newTotal: Long = (price * qty).toLong()
            if (newTotal.toLong() == items[position].total && items[position].quantity == qty) return@doAfterTextChanged

            val updated = items[position].copy(
                quantity = qty,
                total = newTotal
            )
            items[position] = updated
            b.tvTotal.text = "Total: ${updated.total}"
            onChanged(items.toList())
        }

        b.etQty.doAfterTextChanged {
            if (holder.adapterPosition == RecyclerView.NO_POSITION) return@doAfterTextChanged

            val qty = parseIntClean(b.etQty.text?.toString(), default = 1).coerceAtLeast(1)
            val price = parseIntClean(b.etPrice.text?.toString())

            val newTotal: Long = (price * qty).toLong()
            if (newTotal.toLong() == items[position].total && items[position].quantity == qty) return@doAfterTextChanged

            val updated = items[position].copy(
                quantity = qty,
                total = newTotal
            )
            items[position] = updated
            b.tvTotal.text = "Total: ${updated.total}"
            onChanged(items.toList())
        }

        b.actAccessory.setOnItemClickListener { _, _, idx, _ ->
            val opt = options[idx]

            // si eliges un accesorio, seteamos id+name y usamos su precio por defecto
            val qty = items[position].quantity.coerceAtLeast(1)
            val newTotal = opt.price * qty

            val updated = items[position].copy(
                accessoryId = opt.id,
                name = opt.name,
                total = newTotal
            )
            items[position] = updated

            // actualizar precio mostrado (temporal) y total
            setTextSafely(b.etPrice, opt.price.toString())
            b.tvTotal.text = "Total: ${updated.total}"

            onChanged(items.toList())
        }
    }

    private fun updateItem(position: Int, newItem: InstalledAccessory) {
        if (position !in items.indices) return
        items[position] = newItem
        notifyItemChanged(position)
        onChanged(items.toList())
    }

    private fun parseIntClean(text: String?, default: Int = 0): Int {
        val raw = text.orEmpty().replace(".", "").trim()
        return raw.toIntOrNull() ?: default
    }

    private fun inferUnitPrice(total: Long, qty: Int): Int {
        val q = if (qty <= 0) 1 else qty
        return (if (total <= 0) 0 else (total / q)) as Int
    }

    /**
     * Evita loops: solo setea si cambió realmente
     */
    private fun setTextSafely(et: TextInputEditText, value: String) {
        val current = et.text?.toString().orEmpty()
        if (current == value) return
        et.setText(value)
        et.setSelection(et.text?.length ?: 0)
    }
}
