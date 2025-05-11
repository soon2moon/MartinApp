package com.example.wallpaperpro

import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ImageAdapter(
    private var imageUris: List<Uri>,
    private val itemLayoutResId: Int,
    private val imageActionListener: OnImageActionListener? = null
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    interface OnImageActionListener {
        fun onImageLongClicked(uri: Uri, position: Int) // Für langes Drücken auf das gesamte Item
        fun onDeleteClicked(uri: Uri, position: Int)    // Für Klick auf das separate Lösch-Icon
    }

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageViewThumbnail: ImageView = itemView.findViewById(R.id.imageViewThumbnail)
        // Hole eine Referenz zum Lösch-Icon, falls es im Layout existiert
        val imageViewDeleteIcon: ImageView? = itemView.findViewById(R.id.imageViewDeleteIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(itemLayoutResId, parent, false)
        return ImageViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = imageUris[position]
        try {
            holder.imageViewThumbnail.setImageURI(uri)
        } catch (e: Exception) {
            holder.imageViewThumbnail.setImageResource(R.mipmap.ic_launcher)
            Log.e("ImageAdapter", "Fehler beim Laden von Bild-URI: $uri", e)
        }

        // Listener für langen Klick auf das gesamte Item (falls Du das noch willst)
        holder.itemView.setOnLongClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                imageActionListener?.onImageLongClicked(imageUris[currentPosition], currentPosition)
            }
            true
        }

        // Listener für Klick auf das separate Lösch-Icon
        // Dieser wird nur gesetzt, wenn das imageViewDeleteIcon im Layout gefunden wurde
        holder.imageViewDeleteIcon?.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                imageActionListener?.onDeleteClicked(imageUris[currentPosition], currentPosition)
            }
        }
    }

    override fun getItemCount(): Int = imageUris.size

    fun updateData(newImageUris: List<Uri>) {
        imageUris = newImageUris
        notifyDataSetChanged()
    }
}