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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 1)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val preferences = context.getSharedPreferences("recomed_prefs", Context.MODE_PRIVATE)

        when (intent.action) {
            ACTION_STOP_ALARM, "STOP_ALARM" -> {
                manager.cancel(id)
                clearActiveAlarm(preferences, id)
                return
            }
            ACTION_MARK_TAKEN, ACTION_MARK_MISSED -> {
                val medicineName = intent.getStringExtra("name") ?: "Medicamento"
                val status = if (intent.action == ACTION_MARK_TAKEN) {
                    DoseRecord.Status.TAKEN
                } else {
                    DoseRecord.Status.MISSED
                }
                DoseRecordStore.record(context, id, medicineName, LocalDate.now(), status)
                manager.cancel(id)
                clearActiveAlarm(preferences, id)
                val message = if (status == DoseRecord.Status.TAKEN) {
                    "Dosis marcada como tomada"
                } else {
                    "Dosis marcada como omitida"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val name = intent.getStringExtra("name") ?: "Medicamento"
        val dose = intent.getStringExtra("dose") ?: ""
        val soundMode = preferences.getString("notification_sound", "default") ?: "default"
        val customToneUri = preferences.getString("notification_custom_uri", null).orEmpty()
        val vibrationMode = preferences.getString("notification_vibration", "standard") ?: "standard"
        val channelId = "recomed_reminders_v5_${soundMode}_${vibrationMode}_${customToneUri.hashCode()}"

        val soundUri = when (soundMode) {
            "soft" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            "intense" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            "custom" -> customToneUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            "silent" -> null
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(channelId, "Recordatorios RecoMed", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notificaciones con opción de registrar la dosis"
            setSound(soundUri, audioAttributes)
            val vibrationPattern = when (vibrationMode) {
                "soft" -> longArrayOf(0, 120, 100, 120)
                "long" -> longArrayOf(0, 500, 180, 500)
                "off" -> null
                else -> longArrayOf(0, 250, 150, 250)
            }
            enableVibration(vibrationPattern != null)
            if (vibrationPattern != null) setVibrationPattern(vibrationPattern)
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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Hora de tu tratamiento: $name")
            .setContentText("Indica si tomaste la dosis o la omitiste.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Es momento de tomar $name${if (dose.isBlank()) "" else " ($dose)"}. " +
                        "Registra tu decisión para actualizar el seguimiento."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "Tomada",
                createDoseAction(context, id, name, ACTION_MARK_TAKEN)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Omitida",
                createDoseAction(context, id, name, ACTION_MARK_MISSED)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cerrar",
                createDoseAction(context, id, name, ACTION_STOP_ALARM)
            )
            .build()

        manager.notify(id, notification)

        preferences.edit().putInt("active_alarm_id", id).putString("active_alarm_name", name).apply()

        val medicine = MedicineStore.get(context).firstOrNull { it.id == id && it.enabled }
        if (medicine != null) AlarmScheduler.schedule(context, medicine)
    }

    private fun createDoseAction(
        context: Context,
        id: Int,
        name: String,
        action: String
    ): android.app.PendingIntent {
        val actionIntent = Intent(context, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra("id", id)
            putExtra("name", name)
        }
        return android.app.PendingIntent.getBroadcast(
            context,
            id,
            actionIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun clearActiveAlarm(preferences: android.content.SharedPreferences, id: Int) {
        if (preferences.getInt("active_alarm_id", -1) == id) {
            preferences.edit().remove("active_alarm_id").remove("active_alarm_name").apply()
        }
    }

    companion object {
        private const val ACTION_STOP_ALARM = "uy.recomed.app.STOP_ALARM"
        private const val ACTION_MARK_TAKEN = "uy.recomed.app.MARK_TAKEN"
        private const val ACTION_MARK_MISSED = "uy.recomed.app.MARK_MISSED"
    }
}
