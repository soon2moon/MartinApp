package com.example.wallpaperpro

import android.app.AlertDialog // Import für AlertDialog, falls noch nicht vorhanden
import android.content.Context
import android.content.Intent // Für den ApplyAndExit Button (zum Home-Screen)
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView // Für Spinner
import android.widget.ArrayAdapter // Für Spinner
import android.widget.Toast // Für den ApplyAndExit Button-Toast
// import androidx.compose.ui.geometry.isEmpty
// import androidx.compose.ui.semantics.text
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager // Für RecyclerView
import com.example.wallpaperpro.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!! // Stellt sicher, dass _binding nicht null ist

    private var mainActivityInstance: MainActivity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivity) {
            mainActivityInstance = context
        } else {
            Log.e("SettingsFragment", "Host-Activity ist NICHT MainActivity!")
            // Optional: throw ClassCastException("$context must be MainActivity")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (mainActivityInstance == null) {
            Log.e("SettingsFragment", "MainActivity-Instanz ist null in onViewCreated. UI kann nicht initialisiert werden.")
            binding.textViewStatus.text = "Fehler: App-Kontext nicht gefunden."
            // UI-Elemente deaktivieren, um Abstürze zu vermeiden
            binding.buttonSelectImages.isEnabled = false
            binding.buttonSelectFolder.isEnabled = false
            binding.buttonStartNewCollection.isEnabled = false // Auch den neuen Button berücksichtigen
            binding.buttonApplyAndExit.isEnabled = false
            binding.spinnerDelay.isEnabled = false
            binding.switchAutoChange.isEnabled = false
            return
        }

        // UI-Elemente initialisieren und Listener setzen
        setupPreviewRecyclerView()
        setupDelaySpinner()
        setupAutoChangeSwitchListeners()
        setupWallpaperTargetRadioGroup() // NEUE Methode aufrufen
        setupActionButtons() // Enthält jetzt auch den Listener für buttonStartNewCollection

        // Initialen Zustand der UI-Elemente laden/anzeigen
        updateFragmentUI()
    }

    // NEUE Methode zum Einrichten der RadioGroup
    private fun setupWallpaperTargetRadioGroup() {
        mainActivityInstance?.let { mainAct ->
            val currentTarget =
                mainAct.prefs.getInt(mainAct.KEY_WALLPAPER_TARGET, mainAct.DEFAULT_WALLPAPER_TARGET)
            when (currentTarget) {
                mainAct.TARGET_HOME -> binding.radioButtonTargetHome.isChecked = true
                mainAct.TARGET_LOCK -> binding.radioButtonTargetLockscreen.isChecked = true
                mainAct.TARGET_BOTH -> binding.radioButtonTargetBoth.isChecked = true
                else -> binding.radioButtonTargetBoth.isChecked = true // Fallback
            }

            binding.radioGroupWallpaperTarget.setOnCheckedChangeListener { group, checkedId ->
                val newTarget = when (checkedId) {
                    R.id.radioButtonTargetHome -> mainAct.TARGET_HOME
                    R.id.radioButtonTargetLockscreen -> mainAct.TARGET_LOCK
                    R.id.radioButtonTargetBoth -> mainAct.TARGET_BOTH
                    else -> mainAct.DEFAULT_WALLPAPER_TARGET
                }
                mainAct.prefs.edit().putInt(mainAct.KEY_WALLPAPER_TARGET, newTarget).apply()
                Log.d("SettingsFragment", "Wallpaper-Ziel geändert auf: $newTarget")
                // Kein direkter Alarm-Neustart hier nötig, die Einstellung wird beim nächsten Wechsel verwendet.
            }
        }
    }

    private fun setupPreviewRecyclerView() {
        mainActivityInstance?.let { mainAct ->
            binding.recyclerViewSelectedImages.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            // Wichtig: Stelle sicher, dass imageAdapterForSettingsPreview in MainActivity
            // mit R.layout.item_selected_image initialisiert wird.
            binding.recyclerViewSelectedImages.adapter = mainAct.imageAdapterForSettingsPreview
            updatePreviewVisibility(mainAct.selectedImageUris)
        }
    }

    private fun setupDelaySpinner() {
        mainActivityInstance?.let { mainAct ->
            val delayLabels = resources.getStringArray(R.array.delay_options_labels)
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, delayLabels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerDelay.adapter = adapter

            val delayValuesMs = resources.getStringArray(R.array.delay_options_values_ms).map { it.toLong() }
            val currentDelay = mainAct.prefs.getLong(mainAct.KEY_DELAY_MS, mainAct.defaultDelayMs)
            val currentDelayIndex = delayValuesMs.indexOf(currentDelay).takeIf { it != -1 } ?: 0
            binding.spinnerDelay.setSelection(currentDelayIndex)

            binding.spinnerDelay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedDelayMs = delayValuesMs[position]
                    mainAct.prefs.edit().putLong(mainAct.KEY_DELAY_MS, selectedDelayMs).apply()
                    if (binding.switchAutoChange.isChecked && mainAct.selectedImageUris.isNotEmpty()) {
                        mainAct.checkExactAlarmPermissionAndSchedule()
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    private fun setupAutoChangeSwitchListeners() {
        mainActivityInstance?.let { mainAct ->
            // Der initiale Zustand wird in updateFragmentUI() gesetzt
            binding.switchAutoChange.setOnCheckedChangeListener { _, isChecked ->
                mainAct.setServiceActive(isChecked, false) // MainActivity speichert und handelt Logik
                if (isChecked) {
                    if (mainAct.selectedImageUris.isNotEmpty()) {
                        updateStatusText("Automatischer Wechsel wird gestartet...")
                        mainAct.checkExactAlarmPermissionAndSchedule()
                    } else {
                        updateStatusText("Bitte zuerst Bilder auswählen.")
                        binding.switchAutoChange.isChecked = false // Switch direkt zurücksetzen
                        mainAct.setServiceActive(false, false) // Service-Status in Prefs aktualisieren
                    }
                } else {
                    mainAct.cancelWallpaperAlarm() // MainActivity setzt globalen Status und cancelt Alarm
                    // Der Status-Text wird in der Regel von cancelWallpaperAlarm über updateSettingsFragmentStatus gesetzt.
                    // Man kann ihn hier aber auch explizit setzen, falls nötig:
                    updateStatusText("Automatischer Wechsel gestoppt.")
                }
            }
        }
    }

    private fun setupActionButtons() {
        mainActivityInstance?.let { mainAct ->
            binding.buttonSelectImages.setOnClickListener {
                // Ruft handleSelectedIndividualImageUris in MainActivity auf,
                // welche jetzt Bilder HINZUFÜGT.
                mainAct.pickImagesLauncher.launch("image/*")
            }
            binding.buttonSelectFolder.setOnClickListener {
                mainAct.pickDirectoryLauncher.launch(null)
            }

            // Listener für den Button "Auswahl zurücksetzen"
            binding.buttonStartNewCollection.setOnClickListener {
                AlertDialog.Builder(requireContext())
                    .setTitle("Auswahl zurücksetzen")
                    .setMessage("Möchtest Du wirklich alle ausgewählten Bilder entfernen und eine neue Auswahl beginnen?")
                    .setPositiveButton("Ja") { dialog, _ ->
                        mainAct.startNewImageCollection() // Diese Methode in MainActivity löscht die Auswahl
                        dialog.dismiss()
                    }
                    .setNegativeButton("Nein") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }

            binding.buttonApplyAndExit.setOnClickListener {
                val isServiceCurrentlyActive = binding.switchAutoChange.isChecked
                // Der aktuelle Zustand des Switches wurde bereits durch seinen Listener in den Prefs gespeichert
                // über mainAct.setServiceActive(isServiceCurrentlyActive, false).
                // Ein erneuter Aufruf hier ist optional, aber schadet nicht, um den letzten Stand zu sichern.
                mainAct.setServiceActive(isServiceCurrentlyActive, false)

                if (isServiceCurrentlyActive) {
                    if (mainAct.selectedImageUris.isNotEmpty()) {
                        Toast.makeText(requireContext(), "Einstellungen angewendet. Automatischer Wechsel aktiv.", Toast.LENGTH_LONG).show()
                        mainAct.checkExactAlarmPermissionAndSchedule()
                    } else {
                        Toast.makeText(requireContext(), "Keine Bilder ausgewählt. Automatischer Wechsel nicht gestartet.", Toast.LENGTH_LONG).show()
                        if (binding.switchAutoChange.isChecked) { // Falls der Switch noch an ist, obwohl keine Bilder da sind
                            binding.switchAutoChange.isChecked = false // Löst Listener aus, der setServiceActive(false) aufruft
                        } else { // Falls Switch schon aus war
                            mainAct.setServiceActive(false, false) // Nur Prefs aktualisieren
                        }
                        mainAct.cancelWallpaperAlarm() // Sicherstellen, dass kein Alarm läuft
                    }
                } else {
                    Toast.makeText(requireContext(), "Einstellungen gespeichert. Automatischer Wechsel ist deaktiviert.", Toast.LENGTH_LONG).show()
                    mainAct.cancelWallpaperAlarm()
                }

                // App in den Hintergrund schicken
                val homeIntent = Intent(Intent.ACTION_MAIN)
                homeIntent.addCategory(Intent.CATEGORY_HOME)
                homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(homeIntent)
            }
        }
    }

    // Wird von MainActivity aufgerufen (oder von onResume/onViewCreated), um die gesamte UI des Fragments zu aktualisieren
    fun updateFragmentUI() {
        if (!isAdded || _binding == null || mainActivityInstance == null) {
            Log.w("SettingsFragment", "updateFragmentUI aufgerufen, aber Fragment nicht bereit (notAdded: ${!isAdded}, bindingNull: ${_binding == null}, mainActivityNull: ${mainActivityInstance == null})")
            return
        }

        mainActivityInstance?.let { mainAct ->
            // RadioButton Zustand basierend auf Prefs setzen (wird schon in setupWallpaperTargetRadioGroup gemacht,
            // aber hier zur Sicherheit, falls das Fragment neu aufgebaut wird)
            val currentTarget = mainAct.prefs.getInt(mainAct.KEY_WALLPAPER_TARGET, mainAct.DEFAULT_WALLPAPER_TARGET)
            when (currentTarget) {
                mainAct.TARGET_HOME -> binding.radioButtonTargetHome.isChecked = true
                mainAct.TARGET_LOCK -> binding.radioButtonTargetLockscreen.isChecked = true
                mainAct.TARGET_BOTH -> binding.radioButtonTargetBoth.isChecked = true
            }
            // Switch-Zustand aktualisieren
            // Nur setzen, wenn es eine Abweichung gibt, um unnötige Listener-Aufrufe zu vermeiden (obwohl setOnCheckedChangeListener das meist richtig handhabt)
            if (binding.switchAutoChange.isChecked != mainAct.isServiceActive()) {
                binding.switchAutoChange.isChecked = mainAct.isServiceActive()
            }

            // RecyclerView Vorschau aktualisieren
            // Zugriff auf den Adapter über die mainAct Instanz
            mainAct.imageAdapterForSettingsPreview.updateData(mainAct.selectedImageUris)

            updatePreviewVisibility(mainAct.selectedImageUris)

            // Status-Text aktualisieren basierend auf globalem Zustand
            val status: String
            val currentServiceState = mainAct.isServiceActive()
            val currentDelayMs = mainAct.prefs.getLong(mainAct.KEY_DELAY_MS, mainAct.defaultDelayMs)
            val seconds = currentDelayMs / 1000

            if (currentServiceState && mainAct.selectedImageUris.isNotEmpty()) {
                status = "Automatischer Wechsel aktiv.\nNächster Wechsel in $seconds Sek."
            } else if (currentServiceState && mainAct.selectedImageUris.isEmpty()) {
                status = "Keine Bilder für aktiven Wechsel. Bitte auswählen."
                // Switch sollte hier bereits durch setServiceActive(false, true) in MainActivity aktualisiert worden sein
                if(binding.switchAutoChange.isChecked) binding.switchAutoChange.isChecked = false;
            } else if (mainAct.selectedImageUris.isEmpty()){
                status = "Bitte Bilder auswählen."
            } else { // Nicht aktiv, aber Bilder vorhanden
                status = "Automatischer Wechsel ist gestoppt."
            }
            binding.textViewStatus.text = status
        }
    }

    // Hilfsmethode zur Aktualisierung der Sichtbarkeit der RecyclerView-Vorschau
    fun updatePreviewVisibility(uris: List<Uri>) {
        if (_binding != null) { // Nur wenn Binding existiert
            binding.recyclerViewSelectedImages.visibility = if (uris.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    // Methode, um nur den Status-Text gezielt zu setzen (kann von MainActivity genutzt werden)
    fun updateStatusText(message: String) {
        if (_binding != null) { // Nur wenn Binding existiert
            binding.textViewStatus.text = message
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("SettingsFragment", "onResume aufgerufen, rufe updateFragmentUI")
        updateFragmentUI() // Stellt sicher, dass die UI den aktuellen Zustand widerspiegelt
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("SettingsFragment", "onDestroyView aufgerufen")
        _binding = null // Wichtig, um Memory Leaks zu vermeiden
    }

    override fun onDetach() {
        super.onDetach()
        mainActivityInstance = null // Referenz aufräumen
    }
}