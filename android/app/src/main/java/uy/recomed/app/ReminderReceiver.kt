package uy.recomed.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 1)
        val name = intent.getStringExtra("name") ?: "Medicamento"
        val dose = intent.getStringExtra("dose") ?: ""
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "recomed_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Recordatorios de medicamentos", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val openApp = Intent(context, MainActivity::class.java)
        val contentIntent = android.app.PendingIntent.getActivity(
            context, id, openApp,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Es hora de tu recordatorio")
            .setContentText(if (dose.isBlank()) name else "$name · $dose")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Recordatorio configurado: $name${if (dose.isBlank()) "" else " — $dose"}. Sigue las indicaciones de tu profesional de salud."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(id, notification)

        // Programar la próxima ocurrencia diaria.
        val medicine = MedicineStore.get(context).firstOrNull { it.id == id && it.enabled }
        if (medicine != null) AlarmScheduler.schedule(context, medicine)
    }
}
