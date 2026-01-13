package com.example.accessoriesManager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.accesorymanager.R
import com.example.accessoriesManager.model.Accessory
import java.text.NumberFormat
import java.util.Locale

class AccessoryAdapter(
    private val onView: (Accessory) -> Unit,
    private val onEdit: (Accessory) -> Unit,
    private val onDelete: (Accessory) -> Unit
) : ListAdapter<Accessory, AccessoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_accessory_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvAccessoryName)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvAccessoryPrice)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(item: Accessory) {
            tvName.text = item.name

            tvPrice.text = "$ ${
                NumberFormat.getInstance(Locale("es", "CO")).format(item.price)
            }"

            itemView.setOnClickListener { onEdit(item) }
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Accessory>() {
            override fun areItemsTheSame(old: Accessory, new: Accessory) = old.id == new.id
            override fun areContentsTheSame(old: Accessory, new: Accessory) = old == new
        }
    }
}
