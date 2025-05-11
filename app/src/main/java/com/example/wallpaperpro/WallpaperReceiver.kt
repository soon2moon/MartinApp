package com.example.wallpaperpro

import android.app.AlarmManager // Behalte diesen Import
import android.app.PendingIntent // Behalte diesen Import
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log

class WallpaperReceiver : BroadcastReceiver() {

    // Konstanten hier (oder aus einer gemeinsamen Datei/Companion Object der MainActivity holen)
    private val PREFS_NAME = "wallpaper_prefs"
    private val KEY_IMAGE_URIS = "image_uris"
    private val KEY_CURRENT_INDEX = "current_index"
    private val KEY_DELAY_MS = "delay_ms"
    private val KEY_IS_SERVICE_ACTIVE = "is_service_active"
    private val KEY_WALLPAPER_TARGET = "wallpaper_target" // Neuer Key

    private val TARGET_HOME = 1
    private val TARGET_LOCK = 2
    private val TARGET_BOTH = 3
    private val DEFAULT_WALLPAPER_TARGET = TARGET_BOTH // Synchron mit MainActivity halten

    private val defaultDelayMs: Long = 10000L

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isServiceActive = prefs.getBoolean(KEY_IS_SERVICE_ACTIVE, false)

        if (!isServiceActive) {
            Log.i("WallpaperReceiver", "Dienst nicht aktiv, kein Wechsel.")
            return
        }

        try {
            val uris = prefs.getStringSet(KEY_IMAGE_URIS, null)?.toList()
            if (uris.isNullOrEmpty()) {
                Log.w("WallpaperReceiver", "Keine URIs, stoppe Wechsel.")
                prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply()
                return
            }

            val index = prefs.getInt(KEY_CURRENT_INDEX, 0)
            val safeIndex = if (uris.isNotEmpty()) index % uris.size else 0
            val uri = Uri.parse(uris[safeIndex])

            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap != null) {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    val target = prefs.getInt(KEY_WALLPAPER_TARGET, DEFAULT_WALLPAPER_TARGET)

                    // Wallpaper setzen basierend auf der Auswahl
                    // Benötigt API Level 24 (Android N), was deine minSdk ist.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        var successSystem = true
                        var successLock = true

                        if (target == TARGET_HOME || target == TARGET_BOTH) {
                            try {
                                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                                Log.i("WallpaperReceiver", "Hintergrundbild gesetzt: ${uri.lastPathSegment}")
                            } catch (e: Exception) {
                                successSystem = false
                                Log.e("WallpaperReceiver", "Fehler beim Setzen des Hintergrundbilds", e)
                            }
                        }
                        if (target == TARGET_LOCK || target == TARGET_BOTH) {
                            try {
                                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                                Log.i("WallpaperReceiver", "Sperrbildschirm gesetzt: ${uri.lastPathSegment}")
                            } catch (e: Exception) {
                                successLock = false
                                Log.e("WallpaperReceiver", "Fehler beim Setzen des Sperrbildschirms", e)
                            }
                        }
                        if ((target == TARGET_BOTH && successSystem && successLock) ||
                            (target == TARGET_HOME && successSystem) ||
                            (target == TARGET_LOCK && successLock)) {
                            // Nur Index erhöhen und neu planen, wenn erfolgreich
                            val nextIndex = (safeIndex + 1) % uris.size
                            prefs.edit().putInt(KEY_CURRENT_INDEX, nextIndex).apply()
                        } else {
                            // Leerer else-Block oder ein Log-Statement für Debugging-Zwecke
                            // Log.d("WallpaperReceiver", "Bedingung zum Index erhöhen nicht erfüllt.")
                        }

                    } else {
                        // Fallback für API < 24 (wird bei minSdk 24 nicht erreicht)
                        // wallpaperManager.setBitmap(bitmap)
                        // Log.i("WallpaperReceiver", "Hintergrundbild (altes API) gesetzt: ${uri.lastPathSegment}")
                        // val nextIndex = (safeIndex + 1) % uris.size
                        // prefs.edit().putInt(KEY_CURRENT_INDEX, nextIndex).apply()
                    }
                } else {
                    Log.e("WallpaperReceiver", "Bitmap konnte nicht dekodiert werden: $uri")
                }
            } ?: run {
                Log.e("WallpaperReceiver", "InputStream konnte nicht geöffnet werden für URI: $uri")
            }

            // Nächsten Alarm planen (auch wenn ein Teil fehlschlug, damit es weitergeht)
            rescheduleAlarm(context, prefs)

        } catch (e: Exception) {
            Log.e("WallpaperReceiver", "Allgemeiner Fehler im WallpaperReceiver", e)
            // Eventuell Dienst deaktivieren bei schweren Fehlern
            // prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply()
        }
    }

    private fun rescheduleAlarm(context: Context, prefs: SharedPreferences) {
        // ... (deine bestehende rescheduleAlarm Methode, sie sollte currentDelayMs aus Prefs lesen)
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
                prefs.edit().putBoolean(KEY_IS_SERVICE_ACTIVE, false).apply()
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
            Log.i("WallpaperReceiver", "Nächster Alarm geplant in ${currentDelayMs / 1000} Sek. (Pre-S)")
        }
    }
}