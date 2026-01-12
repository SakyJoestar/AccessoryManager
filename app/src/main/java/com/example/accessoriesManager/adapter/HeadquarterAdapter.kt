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
import com.example.accessoriesManager.model.Headquarter

class HeadquarterAdapter(
    private val onView: (Headquarter) -> Unit,
    private val onEdit: (Headquarter) -> Unit,
    private val onDelete: (Headquarter) -> Unit
) : ListAdapter<Headquarter, HeadquarterAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_headquarter_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName = itemView.findViewById<TextView>(R.id.tvHeadquarterName)
        private val tvInc = itemView.findViewById<TextView>(R.id.tvIncrement)
//        private val btnView = itemView.findViewById<ImageButton>(R.id.btnView)
        private val btnEdit = itemView.findViewById<ImageButton>(R.id.btnEdit)
        private val btnDelete = itemView.findViewById<ImageButton>(R.id.btnDelete)

        fun bind(item: Headquarter) {
            tvName.text = item.name
            tvInc.text = "Incremento: ${item.increment}"
            itemView.setOnClickListener { onEdit(item) }
//          btnView.setOnClickListener { onView(item) }
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Headquarter>() {
            override fun areItemsTheSame(old: Headquarter, new: Headquarter) = old.id == new.id
            override fun areContentsTheSame(old: Headquarter, new: Headquarter) = old == new
        }
    }
}
