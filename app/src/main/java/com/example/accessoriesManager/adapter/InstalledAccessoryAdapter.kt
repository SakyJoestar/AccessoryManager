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
        items.add(
            InstalledAccessory(
                price = 0L,
                isPaid = false
            )
        )
        notifyItemInserted(items.lastIndex)
        onChanged(items.toList())
    }

    fun removeAt(position: Int) {
        if (position !in items.indices) return
        items.removeAt(position)
        notifyItemRemoved(position)
        onChanged(items.toList())
    }

    inner class VH(val binding: ItemAccessoryRowBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var priceWatcher: ThousandsSeparatorTextWatcher? = null
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

        /* ---------- Dropdown Accesorio ---------- */
        val names = options.map { it.name }
        val adapter = ArrayAdapter(
            b.root.context,
            android.R.layout.simple_list_item_1,
            names
        )
        b.actAccessory.setAdapter(adapter)
        b.actAccessory.setText(item.name.orEmpty(), false)

        /* ---------- Precio ---------- */
        setTextSafely(b.etPrice, item.price.toString())

        holder.priceWatcher?.let { b.etPrice.removeTextChangedListener(it) }
        holder.priceWatcher = ThousandsSeparatorTextWatcher(b.etPrice)
        b.etPrice.addTextChangedListener(holder.priceWatcher)

        /* ---------- Pagado ---------- */
        b.chkPaid.setOnCheckedChangeListener(null)
        b.chkPaid.isChecked = item.isPaid
        b.chkPaid.setOnCheckedChangeListener { _, checked ->
            updateItem(position, item.copy(isPaid = checked))
        }

        /* ---------- Precio listener ---------- */
        b.etPrice.doAfterTextChanged {
            if (holder.adapterPosition == RecyclerView.NO_POSITION) return@doAfterTextChanged

            val price = parseLongClean(b.etPrice.text?.toString())
            if (price == items[position].price) return@doAfterTextChanged

            updateItem(position, items[position].copy(price = price))
        }

        /* ---------- Accesorio seleccionado ---------- */
        b.actAccessory.setOnItemClickListener { _, _, idx, _ ->
            val opt = options[idx]

            val updated = items[position].copy(
                accessoryId = opt.id,
                name = opt.name,
                price = opt.price
            )
            items[position] = updated

            setTextSafely(b.etPrice, opt.price.toString())
            onChanged(items.toList())
        }

        b.btnRemove.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) removeAt(pos)
        }
    }

    private fun updateItem(position: Int, newItem: InstalledAccessory) {
        if (position !in items.indices) return
        items[position] = newItem
        onChanged(items.toList())
    }

    /* ---------- Helpers ---------- */

    private fun parseLongClean(text: String?): Long {
        val raw = text.orEmpty().replace(".", "").trim()
        return raw.toLongOrNull() ?: 0L
    }

    private fun setTextSafely(et: TextInputEditText, value: String) {
        val current = et.text?.toString().orEmpty()
        if (current == value) return
        et.setText(value)
        et.setSelection(et.text?.length ?: 0)
    }
}
