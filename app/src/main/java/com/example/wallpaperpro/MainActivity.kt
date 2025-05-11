package com.example.wallpaperpro

import androidx.appcompat.app.AppCompatDelegate // Import hinzufügen
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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wallpaperpro.databinding.ActivityMainBinding
import com.example.wallpaperpro.databinding.FragmentSettingsBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    lateinit var imageAdapterForSettingsPreview: ImageAdapter
    var selectedImageUris: MutableList<Uri> = mutableListOf()
    lateinit var prefs: SharedPreferences

    val PREFS_NAME = "wallpaper_prefs"
    val KEY_IMAGE_URIS = "image_uris"
    val KEY_CURRENT_INDEX = "current_index"
    val KEY_DELAY_MS = "delay_ms"
    val KEY_IS_SERVICE_ACTIVE = "is_service_active"
    val defaultDelayMs: Long = 10000L

    val KEY_WALLPAPER_TARGET = "wallpaper_target" // Neuer Key

    // Konstanten für die Zielauswahl
    val TARGET_HOME = 1
    val TARGET_LOCK = 2
    val TARGET_BOTH = 3
    val DEFAULT_WALLPAPER_TARGET = TARGET_BOTH // Standardmäßig beide setzen

    val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!uris.isNullOrEmpty()) {
                handleSelectedIndividualImageUris(uris)
            } else {
                // Toast.makeText(this, "Keine Bilder ausgewählt", Toast.LENGTH_SHORT).show()
            }
        }

    val pickDirectoryLauncher =
        registerForActivityResult(OpenDocumentTree()) { directoryUri ->
            if (directoryUri != null) {
                try {
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(directoryUri, takeFlags)
                    loadImagesFromDirectory(directoryUri)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Berechtigung für Ordnerzugriff verweigert.", Toast.LENGTH_LONG).show()
                    updateSettingsFragmentStatus("Ordnerberechtigung verweigert.")
                }
            } else {
                // Toast.makeText(this, "Kein Ordner ausgewählt", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark Mode für die gesamte App erzwingen (vor super.onCreate und setContentView)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        // ImageAdapter für SettingsFragment Vorschau (listener ist null, da dort keine Aktionen ausgelöst werden)
        imageAdapterForSettingsPreview = ImageAdapter(selectedImageUris, R.layout.item_selected_image, null)

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

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .commit()
            binding.bottomNavigation.selectedItemId = R.id.nav_settings
        }
        loadInitialState()
    }

    fun handleSelectedIndividualImageUris(uris: List<Uri>) {
        Log.d("MainActivity", "handleSelectedIndividualImageUris: ${uris.size} neue URIs")
        val resolver = contentResolver
        var newImagesAddedCount = 0
        uris.forEach { uri ->
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (!selectedImageUris.contains(uri)) { // Verhindere Duplikate in der Arbeitsliste
                    selectedImageUris.add(uri)
                    newImagesAddedCount++
                } else {
                    Log.d("MainActivity", "Bild bereits vorhanden: $uri")
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                Toast.makeText(this, "Fehler bei URI-Berechtigung: ${uri.lastPathSegment}", Toast.LENGTH_LONG).show()
            }
        }

        if (newImagesAddedCount > 0) {
            updateUrisInPrefsAndUI(isFromFolder = false, operationStatus = "$newImagesAddedCount Bilder hinzugefügt.")
        } else if (uris.isNotEmpty()) {
            updateSettingsFragmentStatus("Keine neuen Bilder hinzugefügt (ggf. Duplikate oder Fehler).")
            Toast.makeText(this, "Keine neuen Bilder hinzugefügt.", Toast.LENGTH_SHORT).show()
        }
    }

    fun loadImagesFromDirectory(directoryUri: Uri) {
        Log.d("MainActivity", "loadImagesFromDirectory: $directoryUri")
        val newImageUris = mutableListOf<Uri>()
        // ... (Implementierung von loadImagesFromDirectory wie zuvor, die newImageUris füllt)
        if (!DocumentsContract.isTreeUri(directoryUri)) { /*...*/ return }
        val documentId = DocumentsContract.getTreeDocumentId(directoryUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, documentId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE)
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
        } catch (e: Exception) { e.printStackTrace(); Toast.makeText(this, "Fehler beim Lesen des Ordnerinhalts.", Toast.LENGTH_LONG).show(); updateSettingsFragmentStatus("Fehler beim Ordnerzugriff."); return }


        if (newImageUris.isNotEmpty()) {
            selectedImageUris.clear() // Ordnerauswahl ersetzt die aktuelle Liste
            selectedImageUris.addAll(newImageUris)
            updateUrisInPrefsAndUI(isFromFolder = true, operationStatus = "${newImageUris.size} Bilder aus Ordner geladen.")
        } else {
            Toast.makeText(this, "Keine passenden Bilder im Ordner gefunden.", Toast.LENGTH_SHORT).show()
            updateSettingsFragmentStatus("Keine Bilder im Ordner.")
        }
    }

    // Angepasste Signatur für operationStatus
    fun updateUrisInPrefsAndUI(isFromFolder: Boolean, operationStatus: String) {
        Log.d("MainActivity", "updateUrisInPrefsAndUI. isFromFolder: $isFromFolder. Status: $operationStatus. Total URIs: ${selectedImageUris.size}")
        imageAdapterForSettingsPreview.updateData(selectedImageUris)

        val uriStrings = selectedImageUris.map { it.toString() }.toSet()
        prefs.edit()
            .putStringSet(KEY_IMAGE_URIS, uriStrings)
            .putInt(KEY_CURRENT_INDEX, 0)
            .apply()

        if (operationStatus.isNotBlank()){
            Toast.makeText(this, operationStatus, Toast.LENGTH_SHORT).show()
        }

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is SettingsFragment) {
            currentFragment.updateFragmentUI()
        } else if (currentFragment is ImagesFragment) {
            currentFragment.updateImageList()
        }

        if (isServiceActive() && selectedImageUris.isNotEmpty()) {
            checkExactAlarmPermissionAndSchedule()
        } else if (selectedImageUris.isEmpty() && isServiceActive()) {
            setServiceActive(false, true)
        }
    }

    // NEUE METHODE zum Entfernen eines Bildes
    fun removeImageUri(uriToRemove: Uri) {
        val removed = selectedImageUris.remove(uriToRemove)
        if (removed) {
            Log.d("MainActivity", "URI entfernt: $uriToRemove. Verbleibend: ${selectedImageUris.size}")

            val uriStrings = selectedImageUris.map { it.toString() }.toSet()
            prefs.edit()
                .putStringSet(KEY_IMAGE_URIS, uriStrings)
                .putInt(KEY_CURRENT_INDEX, 0) // Index zurücksetzen
                .apply()

            imageAdapterForSettingsPreview.updateData(selectedImageUris)
            val imagesFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ImagesFragment
            imagesFragment?.updateImageList()
            val settingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
            settingsFragment?.updateFragmentUI()

            Toast.makeText(this, "Bild entfernt.", Toast.LENGTH_SHORT).show()

            if (selectedImageUris.isEmpty() && isServiceActive()) {
                Log.d("MainActivity", "Letztes Bild entfernt, Dienst war aktiv. Stoppe Dienst.")
                setServiceActive(false, true)
            } else if (isServiceActive()) { // Wenn noch Bilder da sind und Dienst aktiv war
                checkExactAlarmPermissionAndSchedule() // Alarm neu planen (aktualisiert auch Status-Text)
            }
        }
    }

    fun loadInitialState() {
        // ... (Implementierung von loadInitialState wie zuvor, sie ruft updateFragmentUI der Fragmente auf)
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

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (currentFragment is SettingsFragment) {
            currentFragment.updateFragmentUI()
        } else if (currentFragment is ImagesFragment) {
            currentFragment.updateImageList()
        }

        if (isServiceActive() && selectedImageUris.isNotEmpty()) {
            Log.d("MainActivity", "Dienst war aktiv, starte Alarmprüfung.")
            checkExactAlarmPermissionAndSchedule()
        }
    }

    fun checkExactAlarmPermissionAndSchedule() {
        // ... (Implementierung von checkExactAlarmPermissionAndSchedule wie zuvor)
        Log.d("MainActivity", "checkExactAlarmPermissionAndSchedule. Bilder: ${selectedImageUris.size}, Service Aktiv: ${isServiceActive()}")
        if (selectedImageUris.isEmpty()) {
            Log.w("MainActivity", "Keine Bilder zum Planen des Alarms ausgewählt. Service wird deaktiviert.")
            setServiceActive(false, true)
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
                try { startActivity(intent); Toast.makeText(this, "Bitte erlaube exakte Alarme.", Toast.LENGTH_LONG).show() }
                catch (e: Exception) { Toast.makeText(this, "Einstellungen konnten nicht geöffnet werden.", Toast.LENGTH_LONG).show() }
            }
        } else { scheduleWallpaperAlarm() }
    }

    fun scheduleWallpaperAlarm() {
        // ... (Implementierung von scheduleWallpaperAlarm wie zuvor)
        if (selectedImageUris.isEmpty()) { Log.w("MainActivity", "ScheduleWallpaperAlarm: keine Bilder."); setServiceActive(false, true); return }
        Log.d("MainActivity", "scheduleWallpaperAlarm für ${selectedImageUris.size} Bilder.")
        val currentDelayMs = prefs.getLong(KEY_DELAY_MS, defaultDelayMs)
        val intent = Intent(this, WallpaperReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        val triggerTime = System.currentTimeMillis() + currentDelayMs
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        val seconds = currentDelayMs / 1000
        Log.i("MainActivity", "Nächster Wechsel in $seconds Sek. geplant.")
        updateSettingsFragmentStatus("Automatischer Wechsel aktiv.\nNächster Wechsel in $seconds Sek.")
    }

    fun cancelWallpaperAlarm() {
        // ... (Implementierung von cancelWallpaperAlarm wie zuvor)
        Log.d("MainActivity", "cancelWallpaperAlarm aufgerufen")
        val intent = Intent(this, WallpaperReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
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
        // ... (Implementierung von setServiceActive wie zuvor)
        Log.d("MainActivity", "setServiceActive: $isActive, updateFragment: $updateFragmentViews")
        prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, isActive).apply()
        if (updateFragmentViews) {
            val settingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
            settingsFragment?.updateFragmentUI()
        }
        if (!isActive) {
            cancelWallpaperAlarm()
        } else {
            if (selectedImageUris.isNotEmpty()) {
                checkExactAlarmPermissionAndSchedule()
            } else {
                updateSettingsFragmentStatus("Bitte zuerst Bilder auswählen, um den Wechsel zu starten.")
                if (prefs.getBoolean(KEY_IS_SERVICE_ACTIVE, false)) {
                    prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply()
                    val sf = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
                    sf?.updateFragmentUI()
                }
            }
        }
    }

    fun configureSettingsFragmentUI(settingsBinding: FragmentSettingsBinding) {
        // ... (Implementierung von configureSettingsFragmentUI wie zuvor)
        Log.d("MainActivity", "configureSettingsFragmentUI aufgerufen")
        settingsBinding.recyclerViewSelectedImages.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        settingsBinding.recyclerViewSelectedImages.adapter = imageAdapterForSettingsPreview
        settingsBinding.recyclerViewSelectedImages.visibility = if (selectedImageUris.isEmpty()) View.GONE else View.VISIBLE

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
        settingsBinding.switchAutoChange.isChecked = isServiceActive()
        settingsBinding.buttonSelectImages.setOnClickListener { pickImagesLauncher.launch("image/*") }
        settingsBinding.buttonSelectFolder.setOnClickListener { pickDirectoryLauncher.launch(null) }
        settingsBinding.buttonApplyAndExit.setOnClickListener {
            val currentServiceActiveState = settingsBinding.switchAutoChange.isChecked
            prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, currentServiceActiveState).apply()
            if (currentServiceActiveState) {
                if (selectedImageUris.isNotEmpty()) {
                    Toast.makeText(this, "Einstellungen angewendet. Automatischer Wechsel aktiv.", Toast.LENGTH_LONG).show()
                    checkExactAlarmPermissionAndSchedule()
                } else {
                    Toast.makeText(this, "Keine Bilder ausgewählt. Automatischer Wechsel nicht gestartet.", Toast.LENGTH_LONG).show()
                    if (settingsBinding.switchAutoChange.isChecked) { settingsBinding.switchAutoChange.isChecked = false }
                    else { prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply() }
                    cancelWallpaperAlarm()
                }
            } else {
                Toast.makeText(this, "Einstellungen gespeichert. Automatischer Wechsel ist deaktiviert.", Toast.LENGTH_LONG).show()
                cancelWallpaperAlarm()
            }
            val homeIntent = Intent(Intent.ACTION_MAIN); homeIntent.addCategory(Intent.CATEGORY_HOME); homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK; startActivity(homeIntent)
        }
        updateSettingsFragmentStatus(null)
    }

    fun updateSettingsFragmentStatus(customMessage: String? = null) {
        // ... (Implementierung von updateSettingsFragmentStatus wie zuvor)
        val settingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
        settingsFragment?.let {
            if (customMessage != null) {
                it.updateStatusText(customMessage)
            } else {
                val status: String; val currentServiceState = isServiceActive(); val currentDelayMs = prefs.getLong(KEY_DELAY_MS, defaultDelayMs); val seconds = currentDelayMs / 1000
                if (currentServiceState && selectedImageUris.isNotEmpty()) { status = "Automatischer Wechsel aktiv.\nNächster Wechsel in $seconds Sek." }
                else if (currentServiceState && selectedImageUris.isEmpty()) { status = "Keine Bilder für aktiven Wechsel. Bitte auswählen." }
                else if (selectedImageUris.isEmpty()) { status = "Bitte Bilder auswählen." }
                else { status = "Automatischer Wechsel ist gestoppt." }
                it.updateStatusText(status)
            }
        }
    }

    fun startNewImageCollection() {
        Log.d("MainActivity", "startNewImageCollection aufgerufen - Auswahl wird zurückgesetzt.")

        // 1. Aktuelle Liste der ausgewählten Bilder leeren
        selectedImageUris.clear()

        // 2. SharedPreferences aktualisieren
        prefs.edit()
            .remove(KEY_IMAGE_URIS) // Entfernt den Eintrag komplett oder setze ein leeres Set
            // .putStringSet(KEY_IMAGE_URIS, emptySet()) // Alternative zum Entfernen
            .putInt(KEY_CURRENT_INDEX, 0) // Index zurücksetzen
            .apply()

        // 3. Adapter für die Vorschau im SettingsFragment aktualisieren
        if (::imageAdapterForSettingsPreview.isInitialized) {
            imageAdapterForSettingsPreview.updateData(selectedImageUris)
        }

        // 4. UI der Fragmente informieren und aktualisieren
        // (SettingsFragment wird seinen RecyclerView und Status aktualisieren, ImagesFragment seine Liste leeren)
        val currentSettingsFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? SettingsFragment
        currentSettingsFragment?.updateFragmentUI() // Allgemeine UI-Aktualisierung

        val currentImagesFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? ImagesFragment
        currentImagesFragment?.updateImageList() // Leert die Liste im ImagesFragment

        // 5. Wenn der Dienst aktiv war, stoppen, da keine Bilder mehr vorhanden sind
        if (isServiceActive()) {
            setServiceActive(false, true) // Deaktiviert den Dienst und aktualisiert den Switch im SettingsFragment
        } else {
            // Wenn der Dienst nicht aktiv war, zumindest den Status-Text im SettingsFragment aktualisieren
            updateSettingsFragmentStatus("Auswahl zurückgesetzt. Bitte neue Bilder wählen.")
        }

        Toast.makeText(this, "Auswahl zurückgesetzt. Bitte neue Bilder wählen.", Toast.LENGTH_LONG).show()
    }
}