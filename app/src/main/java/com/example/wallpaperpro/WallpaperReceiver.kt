package com.example.wallpaperpro

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences // Import
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log

class WallpaperReceiver : BroadcastReceiver() {

    private val PREFS_NAME = "wallpaper_prefs"
    private val KEY_IMAGE_URIS = "image_uris"
    private val KEY_CURRENT_INDEX = "current_index"
    private val KEY_DELAY_MS = "delay_ms"
    private val KEY_IS_SERVICE_ACTIVE = "is_service_active" // Um zu prüfen, ob wir überhaupt was tun sollen
    private val defaultDelayMs: Long = 10000L // Standard, falls nichts gespeichert

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Prüfen, ob der Dienst überhaupt aktiv sein soll
        val isServiceActive = prefs.getBoolean(KEY_IS_SERVICE_ACTIVE, false)
        if (!isServiceActive) {
            Log.i("WallpaperReceiver", "Service ist nicht aktiv, kein Wallpaper-Wechsel.")
            // Optional: Alle Alarme hier sicherheitshalber canceln
            // cancelAllAlarms(context) // Implementierung notwendig
            return
        }

        try {
            val uris = prefs.getStringSet(KEY_IMAGE_URIS, null)?.toList()
            if (uris.isNullOrEmpty()) {
                Log.w("WallpaperReceiver", "Keine URIs gefunden, stoppe Wechsel.")
                // Optional: Dienst deaktivieren und Alarme stoppen
                prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply()
                // cancelAllAlarms(context)
                return
            }

            val index = prefs.getInt(KEY_CURRENT_INDEX, 0)
            if (index >= uris.size) { // Sicherheitshalber, falls Index mal out of bounds ist
                prefs.edit().putInt(KEY_CURRENT_INDEX, 0).apply()
                Log.w("WallpaperReceiver", "Index out of bounds, resette auf 0.")
                // Erneut planen für den korrigierten Index
                rescheduleAlarm(context, prefs)
                return
            }
            val uri = Uri.parse(uris[index % uris.size]) // Modulo für Sicherheit

            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    WallpaperManager.getInstance(context).setBitmap(bitmap)
                    Log.i("WallpaperReceiver", "Wallpaper gewechselt: ${uri.lastPathSegment}")

                    val nextIndex = (index + 1) % uris.size
                    prefs.edit().putInt(KEY_CURRENT_INDEX, nextIndex).apply()
                } else {
                    Log.e("WallpaperReceiver", "Konnte Bitmap nicht dekodieren von URI: $uri")
                    // Eventuell dieses URI überspringen oder Fehlerbehandlung
                }
            } ?: run {
                Log.e("WallpaperReceiver", "Konnte InputStream nicht öffnen für URI: $uri")
                // Fehlerbehandlung: Eventuell URI als fehlerhaft markieren und überspringen
            }

            // Nächsten Alarm planen (nur wenn der Dienst noch aktiv sein soll)
            if (prefs.getBoolean(KEY_IS_SERVICE_ACTIVE, false) && uris.isNotEmpty()) {
                rescheduleAlarm(context, prefs)
            }

        } catch (e: Exception) {
            Log.e("WallpaperReceiver", "Fehler beim Setzen des Wallpapers", e)
            // Bei schweren Fehlern den Dienst vielleicht deaktivieren
            // prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply()
        }
    }

    private fun rescheduleAlarm(context: Context, prefs: SharedPreferences) {
        val currentDelayMs = prefs.getLong(KEY_DELAY_MS, defaultDelayMs)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val newIntent = Intent(context, WallpaperReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, newIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextTrigger = System.currentTimeMillis() + currentDelayMs

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
                Log.i("WallpaperReceiver", "Nächster Alarm geplant in ${currentDelayMs / 1000} Sek.")
            } else {
                Log.w("WallpaperReceiver", "App darf keine exakten Alarme setzen. Stoppe Dienst.")
                prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply() // Dienst deaktivieren
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
            Log.i("WallpaperReceiver", "Nächster Alarm geplant in ${currentDelayMs / 1000} Sek. (Pre-S)")
        }
    }
}