package com.example.accessoriesManager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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

        // ✅ Garantiza mínimo 1 fila
        if (items.isEmpty()) {
            items.add(
                InstalledAccessory(
                    price = 0L,
                    isPaid = false
                )
            )
        }

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

        // ✅ Nunca permitir quedar en 0 filas
        if (items.size == 1) return

        // ✅ La primera nunca se elimina
        if (position == 0) return

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
        val binding = holder.binding
        val item = items[position]

        val cantRemove = position == 0

        // ✅ X deshabilitada/oculta en la primera
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

        /* ---------- Dropdown Accesorio ---------- */
        val names = options.map { it.name }
        val dropdownAdapter = ArrayAdapter(
            binding.root.context,
            android.R.layout.simple_list_item_1,
            names
        )
        binding.actAccessory.setAdapter(dropdownAdapter)
        binding.actAccessory.setText(item.name.orEmpty(), false)

        /* ---------- Precio (set inicial + watcher con separador) ---------- */
        holder.priceWatcher?.let { binding.etPrice.removeTextChangedListener(it) }
        holder.priceWatcher = ThousandsSeparatorTextWatcher(binding.etPrice)
        binding.etPrice.addTextChangedListener(holder.priceWatcher)

        // ✅ set inicial del precio SIN romper el cursor
        setTextSafely(binding.etPrice, item.price.toString())

        /* ---------- Estado habilitado según selección ---------- */
        val isAccessorySelected = !item.accessoryId.isNullOrBlank()

        // ✅ primero quitamos listener para no dispararlo al setear estado
        binding.chkPaid.setOnCheckedChangeListener(null)

        // ✅ set estado del checkbox (solo válido si hay accesorio)
        binding.chkPaid.isChecked = if (isAccessorySelected) item.isPaid else false

        // ✅ bloquea / desbloquea precio y pagado
        updateEnabledState(binding, isAccessorySelected)

        /* ---------- Pagado listener ---------- */
        binding.chkPaid.setOnCheckedChangeListener { _, checked ->
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnCheckedChangeListener

            // 🚫 seguridad extra
            if (items[pos].accessoryId.isNullOrBlank()) return@setOnCheckedChangeListener

            updateItem(pos, items[pos].copy(isPaid = checked))
        }

        /* ---------- Precio listener ---------- */
        binding.etPrice.doAfterTextChanged {
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@doAfterTextChanged

            // 🚫 si no hay accesorio seleccionado, ignorar cambios
            if (items[pos].accessoryId.isNullOrBlank()) return@doAfterTextChanged

            val price = parseLongClean(binding.etPrice.text?.toString())
            if (price == items[pos].price) return@doAfterTextChanged

            updateItem(pos, items[pos].copy(price = price))
        }

        // ✅ (Opcional) cerrar teclado cuando sales del precio
        binding.etPrice.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) hideKeyboard(v)
        }

        /* ---------- Accesorio seleccionado ---------- */
        binding.actAccessory.setOnItemClickListener { _, _, idx, _ ->
            val pos = holder.adapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnItemClickListener
            if (idx !in options.indices) return@setOnItemClickListener

            val opt = options[idx]

            val updated = items[pos].copy(
                accessoryId = opt.id,
                name = opt.name,
                price = opt.price,
                isPaid = false // ✅ importante
            )

            items[pos] = updated
            notifyItemChanged(pos)
            onChanged(items.toList())

            // ✅ ahora sí: habilitar precio y checkbox
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

    /* ---------- Helpers ---------- */

    private fun updateEnabledState(
        binding: ItemAccessoryRowBinding,
        enabled: Boolean
    ) {
        binding.etPrice.isEnabled = enabled
        binding.etPrice.alpha = if (enabled) 1f else 0.4f

        binding.chkPaid.isEnabled = enabled
        binding.chkPaid.alpha = if (enabled) 1f else 0.4f

        if (!enabled) {
            // ✅ si no hay accesorio seleccionado, forzar valores
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
