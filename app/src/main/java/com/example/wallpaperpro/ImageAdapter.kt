package com.example.wallpaperpro

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.wallpaperpro.databinding.ItemSelectedImageBinding // Für item_selected_image.xml

class ImageAdapter(private var imageUris: List<Uri>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(val binding: ItemSelectedImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemSelectedImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = imageUris[position]
        // Einfaches Laden der URI. Für Produktion: Glide/Coil oder sorgfältiges Bitmap-Handling
        try {
            holder.binding.imageViewThumbnail.setImageURI(uri)
        } catch (e: Exception) {
            // Fallback, falls URI nicht direkt geladen werden kann (z.B. Berechtigungsfehler nach einiger Zeit)
            // Du könntest hier ein Platzhalterbild setzen
            holder.binding.imageViewThumbnail.setImageResource(R.mipmap.ic_launcher) // Beispiel-Fallback
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int = imageUris.size

    fun updateData(newImageUris: List<Uri>) {
        imageUris = newImageUris
        notifyDataSetChanged() // Einfachste Methode; für bessere Performance DiffUtil verwenden
    }
}