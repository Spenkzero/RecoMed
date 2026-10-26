package uy.recomed.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 1)

        if (intent.action == "STOP_ALARM") {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(id)
            context.getSharedPreferences("recomed_prefs", Context.MODE_PRIVATE)
                .edit().remove("active_alarm_id").remove("active_alarm_name").apply()
            return
        }

        val name = intent.getStringExtra("name") ?: "Medicamento"
        val dose = intent.getStringExtra("dose") ?: ""
        val alarmTone = intent.getStringExtra("alarmTone") ?: "default"
        val customToneUri = intent.getStringExtra("customToneUri") ?: ""

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "recomed_reminders_v4"

        val soundUri = if (customToneUri.isNotBlank()) {
            runCatching { Uri.parse(customToneUri) }.getOrNull() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else {
            when (alarmTone) {
                "suave" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                "urgente" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(channelId, "Recordatorios Avanzados RecoMed", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notificaciones con opción de detener alarma"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)

        val openApp = Intent(context, MainActivity::class.java).apply {
            putExtra("active_alarm_id", id)
            putExtra("active_alarm_name", name)
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            context, id, openApp,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "STOP_ALARM"
            putExtra("id", id)
        }
        val stopPendingIntent = android.app.PendingIntent.getBroadcast(
            context, id + 10000, stopIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora de tu tratamiento: $name")
            .setContentText(if (dose.isBlank()) "Toma programada" else "Dosis: $dose")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Es momento de tomar $name${if (dose.isBlank()) "" else " ($dose)"}. ¡Mantén tu rutina al día!"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener alarma", stopPendingIntent)
            .build()

        manager.notify(id, notification)

        context.getSharedPreferences("recomed_prefs", Context.MODE_PRIVATE)
            .edit().putInt("active_alarm_id", id).putString("active_alarm_name", name).apply()

        val medicine = MedicineStore.get(context).firstOrNull { it.id == id && it.enabled }
        if (medicine != null) AlarmScheduler.schedule(context, medicine)
    }
}
