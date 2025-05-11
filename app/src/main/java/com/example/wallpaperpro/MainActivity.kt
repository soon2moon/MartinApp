package com.example.wallpaperpro

import android.view.View
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
// ArrayAdapter und AdapterView werden in configureSettingsFragmentUI verwendet
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager // HIER IST DER NEUE IMPORT
import com.example.wallpaperpro.databinding.ActivityMainBinding
import com.example.wallpaperpro.databinding.FragmentSettingsBinding // Wird in configureSettingsFragmentUI benötigt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding // Binding für activity_main.xml (mit BottomNav und FragmentContainer)

    // Kernlogik und Daten bleiben hier:
    lateinit var imageAdapterForSettingsPreview: ImageAdapter // Für die kleine Vorschau im SettingsFragment
    var selectedImageUris: MutableList<Uri> = mutableListOf() // Gemeinsame Datenquelle
    lateinit var prefs: SharedPreferences

    // Konstanten für SharedPreferences
    val PREFS_NAME = "wallpaper_prefs"
    val KEY_IMAGE_URIS = "image_uris"
    val KEY_CURRENT_INDEX = "current_index"
    val KEY_DELAY_MS = "delay_ms"
    val KEY_IS_SERVICE_ACTIVE = "is_service_active"
    val defaultDelayMs: Long = 10000L // Standard: 10 Sekunden


    // Launcher für einzelne Bilder
    val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!uris.isNullOrEmpty()) {
                handleSelectedIndividualImageUris(uris)
            } else {
                // Optional: Toast oder Log, dass keine Bilder ausgewählt wurden
                // Toast.makeText(this, "Keine Bilder ausgewählt", Toast.LENGTH_SHORT).show()
            }
        }

    // Launcher für Ordnerauswahl
    val pickDirectoryLauncher =
        registerForActivityResult(OpenDocumentTree()) { directoryUri ->
            if (directoryUri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION // Für alle Fälle, falls mal Schreibzugriff nötig wäre
                    contentResolver.takePersistableUriPermission(directoryUri, takeFlags)
                    loadImagesFromDirectory(directoryUri)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Berechtigung für Ordnerzugriff verweigert.", Toast.LENGTH_LONG).show()
                    updateSettingsFragmentStatus("Ordnerberechtigung verweigert.")
                }
            } else {
                // Optional: Toast oder Log, dass kein Ordner ausgewählt wurde
                // Toast.makeText(this, "Kein Ordner ausgewählt", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        imageAdapterForSettingsPreview = ImageAdapter(selectedImageUris, R.layout.item_selected_image) // Wird mit Daten aus loadInitialState gefüllt

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.nav_settings -> selectedFragment = SettingsFragment()
                R.id.nav_images -> selectedFragment = ImagesFragment()
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit()
            }
            true
        }

        if (savedInstanceState == null) { // Nur beim ersten Erstellen das SettingsFragment laden
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .commit()
            binding.bottomNavigation.selectedItemId = R.id.nav_settings // Setzt den Tab als aktiv
        }

        loadInitialState() // Lädt Daten und informiert das initial geladene Fragment
    }

    fun handleSelectedIndividualImageUris(uris: List<Uri>) {
        Log.d("MainActivity", "handleSelectedIndividualImageUris: ${uris.size} URIs")
        val resolver = contentResolver
        val successfullyPersistedUris = mutableListOf<Uri>()
        uris.forEach { uri ->
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                successfullyPersistedUris.add(uri)
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(this, "Fehler bei URI-Berechtigung: ${uri.lastPathSegment}", Toast.LENGTH_LONG).show()
            }
        }

        if (successfullyPersistedUris.isNotEmpty()) {
            selectedImageUris.clear()
            selectedImageUris.addAll(successfullyPersistedUris)
            updateUrisInPrefsAndUI(isFromFolder = false, count = successfullyPersistedUris.size)
        } else if (uris.isNotEmpty()) {
            updateSettingsFragmentStatus("Konnte keine Berechtigungen für Bilder erhalten.")
        }
    }

    fun loadImagesFromDirectory(directoryUri: Uri) {
        Log.d("MainActivity", "loadImagesFromDirectory: $directoryUri")
        val newImageUris = mutableListOf<Uri>()
        if (!DocumentsContract.isTreeUri(directoryUri)) {
            Log.e("MainActivity", "Provided URI is not a tree URI: $directoryUri")
            Toast.makeText(this, "Ungültiger Ordner-URI.", Toast.LENGTH_SHORT).show()
            updateSettingsFragmentStatus("Ungültiger Ordner ausgewählt.")
            return
        }
        val documentId = DocumentsContract.getTreeDocumentId(directoryUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val imageMimeTypes = arrayOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp")

        try {
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(mimeTypeColumn)
                    if (imageMimeTypes.contains(mimeType)) {
                        val docId = cursor.getString(idColumn)
                        val imageUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId)
                        newImageUris.add(imageUri)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Fehler beim Lesen des Ordnerinhalts.", Toast.LENGTH_LONG).show()
            updateSettingsFragmentStatus("Fehler beim Ordnerzugriff.")
            return
        }

        if (newImageUris.isNotEmpty()) {
            selectedImageUris.clear()
            selectedImageUris.addAll(newImageUris)
            updateUrisInPrefsAndUI(isFromFolder = true, count = newImageUris.size)
        } else {
            Toast.makeText(this, "Keine passenden Bilder im Ordner gefunden.", Toast.LENGTH_SHORT).show()
            updateSettingsFragmentStatus("Keine Bilder im Ordner.")
        }
    }

    fun updateUrisInPrefsAndUI(isFromFolder: Boolean, count: Int) {
        Log.d("MainActivity", "updateUrisInPrefsAndUI. isFromFolder: $isFromFolder, count: $count. selectedImageUris size: ${selectedImageUris.size}")
        imageAdapterForSettingsPreview.updateData(selectedImageUris)

        val uriStrings = selectedImageUris.map { it.toString() }.toSet()
        prefs.edit()
            .putStringSet(KEY_IMAGE_URIS, uriStrings)
            .putInt(KEY_CURRENT_INDEX, 0) // Index immer zurücksetzen bei neuer Auswahl
            .apply()

        val sourceText = if (isFromFolder) "aus Ordner" else "einzeln"
        val toastMessage: String
        if (count > 0) {
            toastMessage = "$count Bilder $sourceText gespeichert."
        } else {
            toastMessage = if (selectedImageUris.isEmpty()) "Keine Bilder ausgewählt." else "Auswahl aktualisiert."
        }
        Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()

        // Informiere das aktuell angezeigte Fragment
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is SettingsFragment) {
            currentFragment.updateFragmentUI()
        } else if (currentFragment is ImagesFragment) {
            currentFragment.updateImageList()
        }

        if (isServiceActive() && selectedImageUris.isNotEmpty()) {
            checkExactAlarmPermissionAndSchedule()
        } else if (selectedImageUris.isEmpty() && isServiceActive()) {
            setServiceActive(false, true) // Deaktiviere Service und aktualisiere Switch im Fragment
        }
    }

    fun loadInitialState() {
        Log.d("MainActivity", "loadInitialState aufgerufen")
        val savedUriStrings = prefs.getStringSet(KEY_IMAGE_URIS, null)
        selectedImageUris.clear()
        if (!savedUriStrings.isNullOrEmpty()) {
            selectedImageUris.addAll(savedUriStrings.map { Uri.parse(it) })
            Log.d("MainActivity", "${selectedImageUris.size} URIs aus SharedPreferences geladen")
        } else {
            Log.d("MainActivity", "Keine Bild-URIs in SharedPreferences gefunden")
        }
        imageAdapterForSettingsPreview.updateData(selectedImageUris)

        // Das initial geladene Fragment (SettingsFragment) wird seine eigene UI in onViewCreated/onResume aktualisieren,
        // indem es auf isServiceActive() etc. zugreift.
        // Wir können hier aber das ImagesFragment informieren, falls es direkt geladen würde (nicht der Fall).
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is SettingsFragment) { // Sicherstellen, dass das Fragment bereits da ist
            currentFragment.updateFragmentUI()
        } else if (currentFragment is ImagesFragment) {
            currentFragment.updateImageList()
        }


        // Wenn der Dienst aktiv war und Bilder vorhanden sind, Alarm ggf. neu starten.
        // Dies wird aber auch durch den Switch im SettingsFragment beim Laden seines Zustands getriggert.
        if (isServiceActive() && selectedImageUris.isNotEmpty()) {
            Log.d("MainActivity", "Dienst war aktiv, starte Alarmprüfung.")
            checkExactAlarmPermissionAndSchedule()
        }
    }

    fun checkExactAlarmPermissionAndSchedule() {
        Log.d("MainActivity", "checkExactAlarmPermissionAndSchedule. Bilder: ${selectedImageUris.size}, Service Aktiv: ${isServiceActive()}")
        if (selectedImageUris.isEmpty()) {
            Log.w("MainActivity", "Keine Bilder zum Planen des Alarms ausgewählt. Service wird deaktiviert.")
            setServiceActive(false, true) // Deaktiviere Service und informiere Fragment
            updateSettingsFragmentStatus("Keine Bilder ausgewählt. Wechsel gestoppt.")
            return
        }

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                scheduleWallpaperAlarm()
            } else {
                updateSettingsFragmentStatus("Berechtigung für exakte Alarme erforderlich.")
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                try {
                    startActivity(intent)
                    Toast.makeText(this, "Bitte erlaube exakte Alarme.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Einstellungen für exakte Alarme konnten nicht geöffnet werden.", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            scheduleWallpaperAlarm()
        }
    }

    fun scheduleWallpaperAlarm() {
        if (selectedImageUris.isEmpty()) {
            Log.w("MainActivity", "ScheduleWallpaperAlarm aufgerufen, aber keine Bilder ausgewählt. Breche ab.")
            setServiceActive(false, true) // Sicherstellen, dass der Dienst deaktiviert ist
            return
        }
        Log.d("MainActivity", "scheduleWallpaperAlarm aufgerufen für ${selectedImageUris.size} Bilder.")

        val currentDelayMs = prefs.getLong(KEY_DELAY_MS, defaultDelayMs)
        val intent = Intent(this, WallpaperReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent) // Bestehenden Alarm mit gleichem PendingIntent zuerst aufheben

        val triggerTime = System.currentTimeMillis() + currentDelayMs
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)

        val seconds = currentDelayMs / 1000
        Log.i("MainActivity", "Nächster Wallpaper-Wechsel in $seconds Sek. geplant.")
        updateSettingsFragmentStatus("Automatischer Wechsel aktiv.\nNächster Wechsel in $seconds Sek.")
    }

    fun cancelWallpaperAlarm() {
        Log.d("MainActivity", "cancelWallpaperAlarm aufgerufen")
        val intent = Intent(this, WallpaperReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Toast.makeText(this, "Automatischer Wechsel gestoppt.", Toast.LENGTH_SHORT).show()
            updateSettingsFragmentStatus("Automatischer Wechsel gestoppt.")
        } else {
            updateSettingsFragmentStatus("Automatischer Wechsel war nicht aktiv.")
        }
    }

    fun isServiceActive(): Boolean {
        return prefs.getBoolean(KEY_IS_SERVICE_ACTIVE, false)
    }

    fun setServiceActive(isActive: Boolean, updateFragmentViews: Boolean = false) {
        Log.d("MainActivity", "setServiceActive: $isActive, updateFragment: $updateFragmentViews")
        prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, isActive).apply()

        if (updateFragmentViews) {
            val settingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
            settingsFragment?.updateFragmentUI()
        }

        if (!isActive) {
            cancelWallpaperAlarm()
        } else {
            // Nur Alarm planen, wenn auch Bilder da sind.
            // checkExactAlarmPermissionAndSchedule prüft das selbst.
            // Dieser Aufruf wird oft durch den Switch-Listener im SettingsFragment kommen.
            if (selectedImageUris.isNotEmpty()) {
                checkExactAlarmPermissionAndSchedule()
            } else {
                // Wenn keine Bilder da sind, aber der Dienst aktiviert werden soll,
                // dann wird der Switch im SettingsFragment das handhaben und den Status-Text setzen.
                // Hier könnten wir den Status auch direkt setzen, aber Fragment-UI-Updates sind besser im Fragment.
                updateSettingsFragmentStatus("Bitte zuerst Bilder auswählen, um den Wechsel zu starten.")
                // Sicherstellen, dass der Dienst nicht wirklich als "aktiv" in Prefs bleibt, wenn keine Bilder da sind.
                // Der Switch Listener im Fragment sollte das bereits tun.
                if (prefs.getBoolean(KEY_IS_SERVICE_ACTIVE, false)) { // Doppelte Prüfung
                    prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply()
                    val settingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
                    settingsFragment?.updateFragmentUI() // Nochmal UI updaten, da Prefs geändert
                }
            }
        }
    }

    // Diese Methode wird vom SettingsFragment in onViewCreated aufgerufen
    fun configureSettingsFragmentUI(settingsBinding: FragmentSettingsBinding) {
        Log.d("MainActivity", "configureSettingsFragmentUI aufgerufen")

        // 1. Preview RecyclerView im SettingsFragment konfigurieren
        settingsBinding.recyclerViewSelectedImages.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        settingsBinding.recyclerViewSelectedImages.adapter = imageAdapterForSettingsPreview
        settingsBinding.recyclerViewSelectedImages.visibility =
            if (selectedImageUris.isEmpty()) View.GONE else View.VISIBLE

        // 2. Spinner im SettingsFragment konfigurieren
        val delayLabels = resources.getStringArray(R.array.delay_options_labels)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, delayLabels)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        settingsBinding.spinnerDelay.adapter = spinnerAdapter

        val delayValuesMs = resources.getStringArray(R.array.delay_options_values_ms).map { it.toLong() }
        val currentDelay = prefs.getLong(KEY_DELAY_MS, defaultDelayMs)
        val currentDelayIndex = delayValuesMs.indexOf(currentDelay).takeIf { it != -1 } ?: 0
        settingsBinding.spinnerDelay.setSelection(currentDelayIndex)

        settingsBinding.spinnerDelay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedDelayMs = delayValuesMs[position]
                prefs.edit().putLong(KEY_DELAY_MS, selectedDelayMs).apply()
                if (settingsBinding.switchAutoChange.isChecked && selectedImageUris.isNotEmpty()) {
                    checkExactAlarmPermissionAndSchedule()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3. Switch im SettingsFragment (Zustand setzen, Listener ist im Fragment selbst)
        settingsBinding.switchAutoChange.isChecked = isServiceActive()
        // Der OnCheckedChangeListener für den Switch wird im SettingsFragment.onViewCreated gesetzt.

        // 4. Buttons im SettingsFragment
        settingsBinding.buttonSelectImages.setOnClickListener { pickImagesLauncher.launch("image/*") }
        settingsBinding.buttonSelectFolder.setOnClickListener { pickDirectoryLauncher.launch(null) }
        settingsBinding.buttonApplyAndExit.setOnClickListener {
            val currentServiceActiveState = settingsBinding.switchAutoChange.isChecked
            // Stelle sicher, dass der letzte Stand des Switches in Prefs gespeichert wird
            prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, currentServiceActiveState).apply()

            if (currentServiceActiveState) {
                if (selectedImageUris.isNotEmpty()) {
                    Toast.makeText(this, "Einstellungen angewendet. Automatischer Wechsel aktiv.", Toast.LENGTH_LONG).show()
                    checkExactAlarmPermissionAndSchedule()
                } else {
                    Toast.makeText(this, "Keine Bilder ausgewählt. Automatischer Wechsel nicht gestartet.", Toast.LENGTH_LONG).show()
                    if (settingsBinding.switchAutoChange.isChecked) { // Redundante Prüfung, aber sicher
                        settingsBinding.switchAutoChange.isChecked = false // Löst Listener im Fragment aus
                    } else { // Falls Listener nicht auslöst, direkt in Prefs schreiben
                        prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply()
                    }
                    cancelWallpaperAlarm()
                }
            } else {
                Toast.makeText(this, "Einstellungen gespeichert. Automatischer Wechsel ist deaktiviert.", Toast.LENGTH_LONG).show()
                cancelWallpaperAlarm()
            }

            // App in den Hintergrund schicken
            val homeIntent = Intent(Intent.ACTION_MAIN)
            homeIntent.addCategory(Intent.CATEGORY_HOME)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(homeIntent)
        }

        // 5. Initialen Status-Text im SettingsFragment setzen
        updateSettingsFragmentStatus(null) // Ruft die zentrale Methode zum Setzen des Status auf
    }

    // Hilfsmethode, um den Status-Text im SettingsFragment zu aktualisieren
    fun updateSettingsFragmentStatus(customMessage: String? = null) {
        val settingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
        settingsFragment?.let {
            if (customMessage != null) {
                it.updateStatusText(customMessage) // Benötigt updateStatusText im SettingsFragment
            } else {
                // Standard-Statuslogik basierend auf isServiceActive und selectedImageUris
                val status: String
                val currentServiceState = isServiceActive()
                val currentDelayMs = prefs.getLong(KEY_DELAY_MS, defaultDelayMs)
                val seconds = currentDelayMs / 1000

                if (currentServiceState && selectedImageUris.isNotEmpty()) {
                    status = "Automatischer Wechsel aktiv.\nNächster Wechsel in $seconds Sek."
                } else if (currentServiceState && selectedImageUris.isEmpty()) {
                    status = "Keine Bilder für aktiven Wechsel. Bitte auswählen."
                } else if (selectedImageUris.isEmpty()) {
                    status = "Bitte Bilder auswählen."
                } else {
                    status = "Automatischer Wechsel ist gestoppt."
                }
                it.updateStatusText(status) // Benötigt updateStatusText im SettingsFragment
            }
        }
    }
}