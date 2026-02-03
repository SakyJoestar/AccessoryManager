package com.example.accessoriesManager.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.accesorymanager.databinding.ItemAccessoryRowBinding
import com.example.accessoriesManager.model.Accessory
import com.example.accessoriesManager.model.InstalledAccessory
import com.example.accessoriesManager.ui.ThousandsSeparatorTextWatcher
import com.google.android.material.textfield.TextInputEditText

class InstalledAccessoryAdapter(
    options: List<Accessory>,
    private val onChanged: (List<InstalledAccessory>) -> Unit
) : RecyclerView.Adapter<InstalledAccessoryAdapter.VH>() {

    private var options: List<Accessory> = options
    private var isProgrammaticAccessoryTextChange = false

    fun updateOptions(newOptions: List<Accessory>) {
        options = newOptions
        notifyDataSetChanged()
    }

    private val items = mutableListOf<InstalledAccessory>()
    private var showAccessoryRequiredErrorOnFirstRow = false

    fun showAccessoryRequiredErrorOnFirstRow() {
        showAccessoryRequiredErrorOnFirstRow = true
        notifyItemChanged(0)
    }

    fun clearAccessoryRequiredError() {
        if (!showAccessoryRequiredErrorOnFirstRow) return
        showAccessoryRequiredErrorOnFirstRow = false
        notifyItemChanged(0)
    }

    fun submitList(list: List<InstalledAccessory>) {
        items.clear()
        items.addAll(list)

        if (items.isEmpty()) items.add(InstalledAccessory(price = 0L, isPaid = false))

        notifyDataSetChanged()
        onChanged(items.toList())
    }

    fun getCurrent(): List<InstalledAccessory> = items.toList()

    fun addEmpty() {
        items.add(InstalledAccessory(price = 0L, isPaid = false))
        notifyItemInserted(items.lastIndex)
        onChanged(items.toList())
    }

    fun removeAt(position: Int) {
        if (position !in items.indices) return
        if (items.size == 1) return
        if (position == 0) return

        items.removeAt(position)
        notifyItemRemoved(position)
        onChanged(items.toList())
    }

    inner class VH(val binding: ItemAccessoryRowBinding) : RecyclerView.ViewHolder(binding.root) {
        var priceWatcher: ThousandsSeparatorTextWatcher? = null
        var priceTextWatcher: TextWatcher? = null
        var accessoryTextWatcher: TextWatcher? = null
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
        val binding = holder.binding
        val item = items[position]

        val cantRemove = position == 0

        val isAccessorySelected =
            !item.accessoryId.isNullOrBlank() || !item.name.isNullOrBlank()

        // ---------- Error primer row ----------
        binding.tilAccessory?.let { til ->
            val showError = (position == 0) && showAccessoryRequiredErrorOnFirstRow && !isAccessorySelected
            til.isErrorEnabled = showError
            til.error = if (showError) "Selecciona un accesorio" else null
        }

        // ---------- Remove ----------
        binding.btnRemove.apply {
            isEnabled = !cantRemove
            alpha = if (cantRemove) 0.3f else 1f
            visibility = if (cantRemove) View.INVISIBLE else View.VISIBLE
            setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                removeAt(pos)
            }
        }

        // ---------- Dropdown ----------
        val names = options.map { it.name.orEmpty() }
        binding.actAccessory.setAdapter(
            ArrayAdapter(binding.root.context, android.R.layout.simple_list_item_1, names)
        )

        // quitar watcher previo (por reciclaje)
        holder.accessoryTextWatcher?.let { binding.actAccessory.removeTextChangedListener(it) }

        isProgrammaticAccessoryTextChange = true
        binding.actAccessory.setText(item.name.orEmpty(), false)
        isProgrammaticAccessoryTextChange = false

        // ✅ watcher texto libre (1 solo por VH)
        val accessoryWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: Editable?) {
                if (isProgrammaticAccessoryTextChange) return
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return

                val typed = editable?.toString()?.trim().orEmpty()

                if (typed.isBlank()) {
                    val updated = items[pos].copy(
                        accessoryId = null,
                        name = null,
                        price = 0L,
                        isPaid = false
                    )
                    if (updated != items[pos]) {
                        items[pos] = updated
                        notifyItemChanged(pos)
                        onChanged(items.toList())
                    }
                    updateEnabledState(binding, false)
                    return
                }

                val match = options.firstOrNull { it.name.orEmpty().equals(typed, ignoreCase = true) }

                val updated = if (match != null) {
                    items[pos].copy(
                        accessoryId = match.id,
                        name = match.name,
                        price = match.price
                    )
                } else {
                    // texto libre: id null, name typed, no borres el precio
                    items[pos].copy(
                        accessoryId = null,
                        name = typed
                    )
                }

                if (updated == items[pos]) return

                items[pos] = updated
                notifyItemChanged(pos)
                onChanged(items.toList())

                val enabled = !updated.accessoryId.isNullOrBlank() || !updated.name.isNullOrBlank()
                updateEnabledState(binding, enabled)
                if (enabled) clearAccessoryRequiredError()
            }
        }

        holder.accessoryTextWatcher = accessoryWatcher
        binding.actAccessory.addTextChangedListener(accessoryWatcher)

        // ---------- Precio (watcher puntos) ----------
        holder.priceWatcher?.let { binding.etPrice.removeTextChangedListener(it) }
        holder.priceWatcher = ThousandsSeparatorTextWatcher(binding.etPrice)
        binding.etPrice.addTextChangedListener(holder.priceWatcher)

        // quitar watcher previo de lógica
        holder.priceTextWatcher?.let { binding.etPrice.removeTextChangedListener(it) }

        setTextSafely(binding.etPrice, item.price.toString())

        // ✅ watcher precio (1 por VH)
        val priceLogicWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return

                val canEdit = !items[pos].accessoryId.isNullOrBlank() || !items[pos].name.isNullOrBlank()
                if (!canEdit) return

                val price = parseLongClean(binding.etPrice.text?.toString())
                if (price == items[pos].price) return

                updateItem(pos, items[pos].copy(price = price))
            }
        }
        holder.priceTextWatcher = priceLogicWatcher
        binding.etPrice.addTextChangedListener(priceLogicWatcher)

        binding.etPrice.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val canEdit = !items[pos].accessoryId.isNullOrBlank() || !items[pos].name.isNullOrBlank()
                    if (canEdit) {
                        val raw = binding.etPrice.text?.toString().orEmpty().replace(".", "").trim()
                        if (raw.isBlank()) {
                            setTextSafely(binding.etPrice, "0")
                            updateItem(pos, items[pos].copy(price = 0L))
                        }
                    }
                }
                hideKeyboard(v)
            }
        }

        // ---------- Pagado ----------
        binding.chkPaid.setOnCheckedChangeListener(null)
        binding.chkPaid.isChecked = if (isAccessorySelected) item.isPaid else false
        updateEnabledState(binding, isAccessorySelected)

        binding.chkPaid.setOnCheckedChangeListener { _, checked ->
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener

            val canEdit = !items[pos].accessoryId.isNullOrBlank() || !items[pos].name.isNullOrBlank()
            if (!canEdit) return@setOnCheckedChangeListener

            updateItem(pos, items[pos].copy(isPaid = checked))
        }

        // ---------- Selección catálogo ----------
        binding.actAccessory.setOnItemClickListener { _, _, idx, _ ->
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnItemClickListener
            if (idx !in options.indices) return@setOnItemClickListener

            val opt = options[idx]

            val updated = items[pos].copy(
                accessoryId = opt.id,
                name = opt.name,
                price = opt.price,
                isPaid = false // ✅ recomendado para evitar arrastrar estado
            )

            items[pos] = updated
            notifyItemChanged(pos)
            onChanged(items.toList())

            clearAccessoryRequiredError()
            updateEnabledState(binding, true)

            hideKeyboard(binding.actAccessory)
            binding.actAccessory.clearFocus()
        }
    }

    private fun updateItem(position: Int, newItem: InstalledAccessory) {
        if (position !in items.indices) return
        items[position] = newItem
        onChanged(items.toList())
    }

    private fun updateEnabledState(binding: ItemAccessoryRowBinding, enabled: Boolean) {
        binding.etPrice.isEnabled = enabled
        binding.etPrice.alpha = if (enabled) 1f else 0.4f

        binding.chkPaid.isEnabled = enabled
        binding.chkPaid.alpha = if (enabled) 1f else 0.4f

        if (!enabled) {
            binding.etPrice.setText("0")
            binding.chkPaid.isChecked = false
        }
    }

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

    private fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
