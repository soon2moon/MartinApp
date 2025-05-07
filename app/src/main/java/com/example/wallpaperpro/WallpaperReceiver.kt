package com.example.wallpaperpro

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log

class WallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val prefs = context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            val uris = prefs.getStringSet("image_uris", null)?.toList() ?: return
            val index = prefs.getInt("current_index", 0)
            val uri = Uri.parse(uris[index % uris.size])

            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input)
                WallpaperManager.getInstance(context).setBitmap(bitmap)

                val nextIndex = (index + 1) % uris.size
                prefs.edit().putInt("current_index", nextIndex).apply()

                Log.i("WallpaperReceiver", "Wallpaper gewechselt: Index $nextIndex")
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val newIntent = Intent(context, WallpaperReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextTrigger = System.currentTimeMillis() + 30_000L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTrigger,
                        pendingIntent
                    )
                } else {
                    Log.w("WallpaperReceiver", "App darf keine exakten Alarme setzen")
                }
            } else {
                // Pre-Android 12
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTrigger,
                    pendingIntent
                )
            }

        } catch (e: Exception) {
            Log.e("WallpaperReceiver", "Fehler beim Setzen des Wallpapers", e)
        }
    }
}
