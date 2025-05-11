package com.example.wallpaperpro

import android.net.Uri
import android.view.LayoutInflater
import android.view.View // Import für View hinzufügen
import android.view.ViewGroup
import android.widget.ImageView // Import für ImageView hinzufügen
import androidx.recyclerview.widget.RecyclerView
// Die spezifischen Binding-Klassen werden hier nicht mehr direkt verwendet,
// da wir generischer auf das ImageView zugreifen.

class ImageAdapter(
    private var imageUris: List<Uri>,
    private val itemLayoutResId: Int // ID der zu verwendenden Layout-Datei (z.B. R.layout.item_selected_image)
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    // Der ViewHolder ist jetzt generischer, da er nur ein ImageView erwartet.
    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageViewThumbnail: ImageView = itemView.findViewById(R.id.imageViewThumbnail)
        // Stelle sicher, dass beide Layout-Dateien (item_selected_image.xml und item_gallery_image.xml)
        // ein ImageView mit der ID "@+id/imageViewThumbnail" enthalten.
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(itemLayoutResId, parent, false) // Verwendet die übergebene Layout-ID
        return ImageViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = imageUris[position]
        try {
            holder.imageViewThumbnail.setImageURI(uri)
        } catch (e: Exception) {
            holder.imageViewThumbnail.setImageResource(R.mipmap.ic_launcher) // Beispiel-Fallback
            e.printStackTrace()
        }
    }

    override fun getItemCount(): Int = imageUris.size

    fun updateData(newImageUris: List<Uri>) {
        imageUris = newImageUris
        notifyDataSetChanged()
    }
}