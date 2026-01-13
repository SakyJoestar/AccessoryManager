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
import com.example.accessoriesManager.model.Vehicle

class VehicleAdapter(
    private val onView: (Vehicle) -> Unit,
    private val onEdit: (Vehicle) -> Unit,
    private val onDelete: (Vehicle) -> Unit
) : ListAdapter<Vehicle, VehicleAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvModel = itemView.findViewById<TextView>(R.id.tvVehicleModel)
        private val tvMake = itemView.findViewById<TextView>(R.id.tvVehicleMake)
        private val btnEdit = itemView.findViewById<ImageButton>(R.id.btnEdit)
        private val btnDelete = itemView.findViewById<ImageButton>(R.id.btnDelete)

        fun bind(item: Vehicle) {
            tvModel.text = item.model ?: ""
            tvMake.text = item.make ?: ""

            itemView.setOnClickListener { onEdit(item) }
            // btnView.setOnClickListener { onView(item) }
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Vehicle>() {
            override fun areItemsTheSame(old: Vehicle, new: Vehicle) = old.id == new.id
            override fun areContentsTheSame(old: Vehicle, new: Vehicle) = old == new
        }
    }
}
