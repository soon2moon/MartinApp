package com.example.wallpaperpro

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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager // Für RecyclerView
import com.example.wallpaperpro.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!! // Stellt sicher, dass _binding nicht null ist

    private var mainActivityInstance: MainActivity? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        // Hole die MainActivity-Instanz sicher
        if (activity is MainActivity) {
            mainActivityInstance = activity as MainActivity
        } else {
            Log.e("SettingsFragment", "Host-Activity ist NICHT MainActivity! UI-Funktionalität ist eingeschränkt.")
            // In einem realen Szenario könntest du hier das UI deaktivieren oder eine Fehlermeldung anzeigen
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (mainActivityInstance == null) {
            // Dieser Fall sollte durch die Prüfung in onCreateView eigentlich nicht eintreten,
            // aber zur Sicherheit, falls das Fragment ohne korrekte Activity verwendet wird.
            Log.e("SettingsFragment", "MainActivity-Instanz ist null in onViewCreated. UI kann nicht initialisiert werden.")
            binding.textViewStatus.text = "Fehler: App-Kontext nicht gefunden."
            // Deaktiviere Buttons oder andere Interaktionen, um Abstürze zu vermeiden
            binding.buttonSelectImages.isEnabled = false
            binding.buttonSelectFolder.isEnabled = false
            binding.buttonApplyAndExit.isEnabled = false
            binding.spinnerDelay.isEnabled = false
            binding.switchAutoChange.isEnabled = false
            return
        }

        // UI-Elemente initialisieren und Listener setzen
        setupPreviewRecyclerView()
        setupDelaySpinner()
        setupAutoChangeSwitchListeners() // Listener separat einrichten
        setupActionButtons()

        // Initialen Zustand der UI-Elemente laden/anzeigen
        updateFragmentUI()
    }

    private fun setupPreviewRecyclerView() {
        mainActivityInstance?.let { mainAct ->
            binding.recyclerViewSelectedImages.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
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

            // Setze die initiale Auswahl basierend auf den SharedPreferences der MainActivity
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
                        mainAct.setServiceActive(false, false) // Sicherstellen, dass es auch in Prefs AUS ist
                    }
                } else {
                    mainAct.cancelWallpaperAlarm() // MainActivity setzt globalen Status und cancelt Alarm
                    updateStatusText("Automatischer Wechsel gestoppt.") // Status im Fragment setzen
                }
            }
        }
    }

    private fun setupActionButtons() {
        mainActivityInstance?.let { mainAct ->
            binding.buttonSelectImages.setOnClickListener {
                mainAct.pickImagesLauncher.launch("image/*")
            }
            binding.buttonSelectFolder.setOnClickListener {
                mainAct.pickDirectoryLauncher.launch(null)
            }
            binding.buttonApplyAndExit.setOnClickListener {
                val isServiceCurrentlyActive = binding.switchAutoChange.isChecked
                mainAct.setServiceActive(isServiceCurrentlyActive, false) // Sicherstellen, dass der aktuelle Switch-Stand gespeichert ist

                if (isServiceCurrentlyActive) {
                    if (mainAct.selectedImageUris.isNotEmpty()) {
                        Toast.makeText(requireContext(), "Einstellungen angewendet. Automatischer Wechsel aktiv.", Toast.LENGTH_LONG).show()
                        mainAct.checkExactAlarmPermissionAndSchedule() // Stellt sicher, dass der Alarm läuft
                    } else {
                        Toast.makeText(requireContext(), "Keine Bilder ausgewählt. Automatischer Wechsel nicht gestartet.", Toast.LENGTH_LONG).show()
                        // Der Switch sollte bereits durch die obige Logik (falls keine Bilder) auf false sein
                        // und setServiceActive wurde entsprechend aufgerufen.
                        if (binding.switchAutoChange.isChecked) binding.switchAutoChange.isChecked = false; // explizit
                        mainAct.setServiceActive(false, false) // doppelt hält besser für Prefs
                        mainAct.cancelWallpaperAlarm()
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
            // Switch-Zustand aktualisieren
            val currentCheckedState = binding.switchAutoChange.isChecked
            val serviceActiveState = mainAct.isServiceActive()
            if (currentCheckedState != serviceActiveState) {
                binding.switchAutoChange.isChecked = serviceActiveState
            }

            // RecyclerView Vorschau aktualisieren
            // Der Adapter in MainActivity (imageAdapterForSettingsPreview) sollte bereits die aktuellen Daten haben.
            // Wir müssen nur dem Adapter in diesem Fragment sagen, dass er sich neu zeichnen soll,
            // oder die Sichtbarkeit anpassen.
            mainAct.imageAdapterForSettingsPreview.updateData(mainAct.selectedImageUris) // Sicherstellen, dass der Adapter die neuesten Daten hat
            updatePreviewVisibility(mainAct.selectedImageUris)

            // Status-Text aktualisieren basierend auf globalem Zustand
            val status: String
            val currentServiceState = mainAct.isServiceActive() // Hole den aktuellen Status
            val currentDelayMs = mainAct.prefs.getLong(mainAct.KEY_DELAY_MS, mainAct.defaultDelayMs)
            val seconds = currentDelayMs / 1000

            if (currentServiceState && mainAct.selectedImageUris.isNotEmpty()) {
                status = "Automatischer Wechsel aktiv.\nNächster Wechsel in $seconds Sek."
            } else if (currentServiceState && mainAct.selectedImageUris.isEmpty()) {
                status = "Keine Bilder für aktiven Wechsel. Bitte auswählen."
                // Wenn Service aktiv sein soll, aber keine Bilder da sind, sollte der Switch auch aus sein.
                // Dies wird idealerweise durch die Logik in setServiceActive / checkExactAlarm... in MainActivity gehandhabt
                // und dann hier durch binding.switchAutoChange.isChecked = mainAct.isServiceActive() reflektiert.
                if(binding.switchAutoChange.isChecked) binding.switchAutoChange.isChecked = false;

            } else if (mainAct.selectedImageUris.isEmpty()){
                status = "Bitte Bilder auswählen."
            } else {
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
        if (_binding != null) {
            binding.textViewStatus.text = message
        }
    }

    override fun onResume() {
        super.onResume()
        // Stelle sicher, dass die UI den aktuellen Zustand widerspiegelt, wenn das Fragment wieder sichtbar wird
        Log.d("SettingsFragment", "onResume aufgerufen, rufe updateFragmentUI")
        updateFragmentUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("SettingsFragment", "onDestroyView aufgerufen")
        _binding = null // Wichtig, um Memory Leaks zu vermeiden
    }
}