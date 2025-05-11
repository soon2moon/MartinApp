package com.example.wallpaperpro

import android.app.AlertDialog // Import für AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.wallpaperpro.databinding.FragmentImagesBinding

// Implementiert das neue Interface vom ImageAdapter
class ImagesFragment : Fragment(), ImageAdapter.OnImageActionListener {
    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageAdapter: ImageAdapter
    private var mainActivityInstance: MainActivity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivity) {
            mainActivityInstance = context
        } else {
            Log.e("ImagesFragment", "Fragment wurde nicht an MainActivity angehängt!")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (mainActivityInstance == null) {
            Log.e("ImagesFragment", "MainActivity-Instanz ist null. RecyclerView kann nicht initialisiert werden.")
            return
        }

        val spanCount = 3
        val spacingInDp = 8
        val spacingInPixels = (spacingInDp * resources.displayMetrics.density).toInt()

        // Adapter mit 'this' als Listener für onImageLongClicked initialisieren
        imageAdapter = ImageAdapter(
            mainActivityInstance!!.selectedImageUris,
            R.layout.item_gallery_image, // Spezielles Layout für die Galerie
            this // Das Fragment selbst ist der Listener
        )

        val layoutManager = GridLayoutManager(context, spanCount)
        binding.recyclerViewAllSelectedImages.layoutManager = layoutManager
        binding.recyclerViewAllSelectedImages.adapter = imageAdapter

        if (binding.recyclerViewAllSelectedImages.itemDecorationCount > 0) {
            binding.recyclerViewAllSelectedImages.removeItemDecorationAt(0)
        }
        binding.recyclerViewAllSelectedImages.addItemDecoration(
            GridSpacingItemDecoration(spanCount, spacingInPixels, false)
        )
    }

    // Implementierung der Interface-Methode aus ImageAdapter.OnImageActionListener
    override fun onImageLongClicked(uri: Uri, position: Int) {
        if (!isAdded || mainActivityInstance == null) return // Sicherstellen, dass Fragment aktiv ist

        AlertDialog.Builder(requireContext())
            .setTitle("Bild entfernen")
            .setMessage("Möchtest Du dieses Bild wirklich aus der Auswahl entfernen?")
            .setPositiveButton("Ja") { dialog, _ ->
                mainActivityInstance?.removeImageUri(uri)
                // Die UI-Aktualisierung (Adapter etc.) wird von removeImageUri in MainActivity ausgelöst
                dialog.dismiss()
            }
            .setNegativeButton("Nein") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    fun updateImageList() {
        if (!isAdded || _binding == null || mainActivityInstance == null) {
            Log.w("ImagesFragment", "updateImageList aufgerufen, aber Fragment nicht bereit oder MainActivity-Instanz fehlt.")
            return
        }
        if (::imageAdapter.isInitialized) {
            mainActivityInstance?.let { mainAct ->
                imageAdapter.updateData(mainAct.selectedImageUris)
            }
        } else {
            Log.w("ImagesFragment", "updateImageList: imageAdapter nicht initialisiert.")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("ImagesFragment", "onResume aufgerufen, rufe updateImageList")
        updateImageList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        mainActivityInstance = null
    }

    override fun onDeleteClicked(uri: Uri, position: Int) {
        if (!isAdded || mainActivityInstance == null) return

        AlertDialog.Builder(requireContext())
            .setTitle("Bild entfernen")
            .setMessage("Möchtest Du dieses Bild wirklich aus der Auswahl entfernen?")
            .setPositiveButton("Ja") { dialog, _ ->
                mainActivityInstance?.removeImageUri(uri)
                dialog.dismiss()
            }
            .setNegativeButton("Nein") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}