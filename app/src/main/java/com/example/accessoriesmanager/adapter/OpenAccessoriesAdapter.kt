package com.example.accessoriesmanager.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.accessoriesmanager.databinding.ItemAccessoryRowOpenBinding
import com.example.accessoriesmanager.model.InstalledAccessory
import java.text.NumberFormat
import java.util.Locale

class OpenAccessoriesAdapter :
    ListAdapter<InstalledAccessory, OpenAccessoriesAdapter.VH>(Diff) {

    private var indexOffset = 1

    fun submit(list: List<InstalledAccessory>?) {
        submitList(list.orEmpty())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAccessoryRowOpenBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position), position + indexOffset)
    }

    inner class VH(
        private val binding: ItemAccessoryRowOpenBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: InstalledAccessory, index: Int) = with(binding) {
            tvIndex.text = index.toString()
            tvName.text = item.name.orEmpty().ifBlank { "-" }
            tvPrice.text = money(item.price)
            tvPaid.text = if (item.isPaid) "Sí" else "No"
        }
    }

    private fun money(value: Long): String = "$ ${numberFormatter.format(value)}"

    companion object {
        private val Diff = object : DiffUtil.ItemCallback<InstalledAccessory>() {
            override fun areItemsTheSame(old: InstalledAccessory, new: InstalledAccessory): Boolean =
                old.accessoryId == new.accessoryId && old.name == new.name

            override fun areContentsTheSame(old: InstalledAccessory, new: InstalledAccessory): Boolean =
                old == new
        }

        private val numberFormatter = NumberFormat
            .getNumberInstance(Locale("es", "CO"))
            .apply {
                maximumFractionDigits = 0
                minimumFractionDigits = 0
            }
    }
}
