package com.example.wallpaperpro

import android.content.Context // Import für Context hinzufügen (für onAttach)
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.example.wallpaperpro.databinding.FragmentImagesBinding

class ImagesFragment : Fragment() {
    private var _binding: FragmentImagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageAdapter: ImageAdapter
    private var mainActivityInstance: MainActivity? = null // << HIER DEKLARIEREN

    override fun onAttach(context: Context) { // Methode zum sicheren Holen der Activity-Instanz
        super.onAttach(context)
        if (context is MainActivity) {
            mainActivityInstance = context
        } else {
            // Dieser Fall sollte nicht eintreten, wenn das Fragment immer von MainActivity gehostet wird.
            // Du könntest hier einen Fehler loggen oder eine Exception werfen,
            // da das Fragment ohne MainActivity nicht korrekt funktionieren kann.
            Log.e("ImagesFragment", "Fragment wurde nicht an MainActivity angehängt!")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentImagesBinding.inflate(inflater, container, false)
        // Die Initialisierung von mainActivityInstance erfolgt jetzt in onAttach.
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (mainActivityInstance == null) { // Prüfung, ob die Instanz in onAttach erfolgreich geholt wurde
            Log.e("ImagesFragment", "MainActivity-Instanz ist null in onViewCreated. RecyclerView kann nicht initialisiert werden.")
            // Optional: UI deaktivieren oder eine Fehlermeldung anzeigen
            return
        }

        val spanCount = 3
        val spacingInDp = 8
        val spacingInPixels = (spacingInDp * resources.displayMetrics.density).toInt()

        // Jetzt greifen wir auf die Eigenschaften der (hoffentlich) vorhandenen mainActivityInstance zu
        imageAdapter = ImageAdapter(mainActivityInstance!!.selectedImageUris, R.layout.item_gallery_image)
        // Das '!!' ist hier relativ sicher, da wir oben auf null prüfen und returnen.
        // Alternativ: mainActivityInstance?.let { /* Code hier rein */ }

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

    fun updateImageList() {
        if (!isAdded || _binding == null || mainActivityInstance == null) { // mainActivityInstance hier auch prüfen
            Log.w("ImagesFragment", "updateImageList aufgerufen, aber Fragment nicht bereit oder MainActivity-Instanz fehlt.")
            return
        }

        if (::imageAdapter.isInitialized) {
            // Hier ist die Prüfung 'hostActivity is MainActivity' nicht mehr nötig,
            // da wir schon mainActivityInstance haben, das diesen Typ hat (oder null ist).
            mainActivityInstance?.let { mainAct -> // Sicherer Zugriff mit let
                imageAdapter.updateData(mainAct.selectedImageUris)
            }
        } else {
            Log.w("ImagesFragment", "updateImageList: imageAdapter nicht initialisiert.")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("ImagesFragment", "onResume aufgerufen, rufe updateImageList")
        updateImageList() // Stellt sicher, dass die Liste aktuell ist
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() { // Hier die Referenz zur Activity aufräumen
        super.onDetach()
        mainActivityInstance = null
    }
}