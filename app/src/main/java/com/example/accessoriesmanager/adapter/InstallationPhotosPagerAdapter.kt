package com.example.accessoriesmanager.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.accessoriesmanager.R

class InstallationPhotosPagerAdapter(
    private val items: MutableList<Uri>,
    private val onPhotoClick: (Uri) -> Unit,
    private val onRemoveClick: (Int, Uri) -> Unit
) : RecyclerView.Adapter<InstallationPhotosPagerAdapter.PhotoPagerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoPagerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installation_photo_pager, parent, false)
        return PhotoPagerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoPagerViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(newItems: List<Uri>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class PhotoPagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPhoto: ImageView = itemView.findViewById(R.id.ivPhotoPager)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btnRemovePhotoPager)

        fun bind(uri: Uri, position: Int) {
            Glide.with(itemView.context)
                .load(uri)
                .centerCrop()
                .into(ivPhoto)

            ivPhoto.setOnClickListener {
                onPhotoClick(uri)
            }

            btnRemove.setOnClickListener {
                onRemoveClick(position, uri)
            }
        }
    }
}