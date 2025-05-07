package com.example.wallpaperpro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!uris.isNullOrEmpty()) {
                val resolver = contentResolver
                uris.forEach { uri ->
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val uriStrings = uris.map { it.toString() }.toSet()
                val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
                prefs.edit()
                    .putStringSet("image_uris", uriStrings)
                    .putInt("current_index", 0)
                    .apply()

                Toast.makeText(this, "Bilder gespeichert (${uris.size})", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Keine Bilder ausgewählt", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<TextView>(R.id.textView).setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        checkExactAlarmPermissionAndSchedule()
    }

    private fun checkExactAlarmPermissionAndSchedule() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                scheduleWallpaperAlarm()
            } else {
                // Nutzer zu den Einstellungen weiterleiten
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
                Toast.makeText(
                    this,
                    "Bitte erlaube exakte Alarme, um Wallpaper automatisch zu wechseln.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            // Vor Android 12: kein Problem
            scheduleWallpaperAlarm()
        }
    }

    private fun scheduleWallpaperAlarm() {
        val intent = Intent(this, WallpaperReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + 30_000L // alle 30 Sekunden

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTime,
            pendingIntent
        )
        Toast.makeText(this, "Alarm in 30 Sekunden geplant", Toast.LENGTH_SHORT).show()
    }
}
