package com.example.accessoriesmanager.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.accessoriesmanager.R

class InstallationPhotosAdapter(
    private val onPhotoClick: (Uri) -> Unit,
    private val onRemoveClick: (Uri) -> Unit
) : ListAdapter<Uri, InstallationPhotosAdapter.PhotoViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installation_photo, parent, false)
        return PhotoViewHolder(view, onPhotoClick, onRemoveClick)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PhotoViewHolder(
        itemView: View,
        private val onPhotoClick: (Uri) -> Unit,
        private val onRemoveClick: (Uri) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivPhoto: ImageView = itemView.findViewById(R.id.ivPhoto)
        private val btnRemovePhoto: ImageButton = itemView.findViewById(R.id.btnRemovePhoto)

        fun bind(uri: Uri) {
            Glide.with(itemView.context)
                .load(uri)
                .fitCenter()
                .into(ivPhoto)

            ivPhoto.setOnClickListener {
                onPhotoClick(uri)
            }

            btnRemovePhoto.setOnClickListener {
                onRemoveClick(uri)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            return oldItem == newItem
        }
    }
}