package com.example.wallpaperpro

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.wallpaperpro.databinding.FragmentImagesBinding
// Import für GridSpacingItemDecoration, falls nicht im selben Paket

class ImagesFragment : Fragment() {
    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!
    private lateinit var imageAdapter: ImageAdapter
    private var mainActivityInstance: MainActivity? = null // Hinzugefügt für Zugriff

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesBinding.inflate(inflater, container, false)
        if (activity is MainActivity) { // Hole MainActivity Instanz sicher
            mainActivityInstance = activity as MainActivity
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (mainActivityInstance == null) {
            Log.e("ImagesFragment", "MainActivity-Instanz ist null. RecyclerView kann nicht initialisiert werden.")
            return
        }

        val spanCount = 3 // Anzahl der Spalten, passend zu deinem XML
        // Konvertiere dp in Pixel für den Abstand
        val spacingInDp = 8 // Setze den gewünschten Abstand in dp
        val spacingInPixels = (spacingInDp * resources.displayMetrics.density).toInt()

        imageAdapter = ImageAdapter(mainActivityInstance!!.selectedImageUris) // !! ist hier sicher wegen der Prüfung oben

        val layoutManager = GridLayoutManager(context, spanCount)
        binding.recyclerViewAllSelectedImages.layoutManager = layoutManager
        binding.recyclerViewAllSelectedImages.adapter = imageAdapter

        // Entferne alte ItemDecorations, falls vorhanden, um Duplikate zu vermeiden
        while (binding.recyclerViewAllSelectedImages.itemDecorationCount > 0) {
            binding.recyclerViewAllSelectedImages.removeItemDecorationAt(0)
        }
        // Füge die neue ItemDecoration hinzu
        binding.recyclerViewAllSelectedImages.addItemDecoration(
            GridSpacingItemDecoration(spanCount, spacingInPixels, true) // true für includeEdge
        )
    }

    fun updateImageList() {
        if (!isAdded || _binding == null || mainActivityInstance == null) {
            Log.w("ImagesFragment", "updateImageList aufgerufen, aber Fragment nicht bereit.")
            return
        }
        if (::imageAdapter.isInitialized) {
            imageAdapter.updateData(mainActivityInstance!!.selectedImageUris)
        } else {
            Log.w("ImagesFragment", "updateImageList: imageAdapter nicht initialisiert.")
        }
    }

    override fun onResume() {
        super.onResume()
        // Stelle sicher, dass die Liste aktuell ist, wenn das Fragment wieder sichtbar wird
        Log.d("ImagesFragment", "onResume aufgerufen, rufe updateImageList")
        updateImageList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}