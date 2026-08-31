package com.example.accessoriesmanager.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.accessoriesmanager.R

class PhotoFullscreenPagerAdapter(
    private val items: MutableList<Uri>
) : RecyclerView.Adapter<PhotoFullscreenPagerAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_fullscreen, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    /** Removes the photo at [position] and notifies the adapter. Returns the removed uri, or null if out of range. */
    fun removeAt(position: Int): Uri? {
        if (position !in items.indices) return null
        val removed = items.removeAt(position)
        notifyItemRemoved(position)
        return removed
    }

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPhoto: ImageView = itemView.findViewById(R.id.ivPhotoFullscreen)

        fun bind(uri: Uri) {
            Glide.with(itemView.context)
                .load(uri)
                .fitCenter()
                .into(ivPhoto)
        }
    }
}
