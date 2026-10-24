package uy.recomed.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private var selectedHour = 8
    private var selectedMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        showExactAlarmInfoIfNeeded()
        buildScreen()
        renderMedicines()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(18))
            setBackgroundColor(0xFFF5F8F7.toInt())
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val brand = TextView(this).apply {
            text = "RecoMed"
            textSize = 30f
            setTextColor(0xFF176B5B.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val tagline = TextView(this).apply {
            text = "Tus recordatorios, a tu ritmo."
            textSize = 16f
            setTextColor(0xFF42534F.toInt())
            setPadding(0, dp(4), 0, dp(18))
        }
        val add = Button(this).apply {
            text = "+ Agregar medicamento"
            setOnClickListener { showAddDialog() }
        }
        val heading = TextView(this).apply {
            text = "Mis recordatorios"
            textSize = 21f
            setTextColor(0xFF1E302C.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(8))
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val note = TextView(this).apply {
            text = "RecoMed ayuda a recordar horarios. No reemplaza las indicaciones médicas."
            textSize = 12f
            setTextColor(0xFF60716C.toInt())
            setPadding(0, dp(24), 0, dp(8))
        }
        content.addView(brand)
        content.addView(tagline)
        content.addView(add, LinearLayout.LayoutParams(-1, -2))
        content.addView(heading)
        content.addView(list)
        content.addView(note)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun renderMedicines() {
        list.removeAllViews()
        val medicines = MedicineStore.get(this).sortedWith(compareBy<Medicine> { it.hour }.thenBy { it.minute })
        if (medicines.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Todavía no agregaste medicamentos. Pulsa el botón para crear tu primer recordatorio."
                textSize = 15f
                setTextColor(0xFF60716C.toInt())
                setPadding(0, dp(12), 0, dp(12))
            })
            return
        }
        medicines.forEach { medicine ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(12))
                setBackgroundColor(0xFFFFFFFF.toInt())
                elevation = dp(2).toFloat()
            }
            val time = String.format(Locale.getDefault(), "%02d:%02d", medicine.hour, medicine.minute)
            card.addView(TextView(this).apply {
                text = medicine.name
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF203B35.toInt())
            })
            card.addView(TextView(this).apply {
                text = "$time  ·  ${medicine.dose.ifBlank { "Sin dosis indicada" }}"
                textSize = 14f
                setTextColor(0xFF536660.toInt())
                setPadding(0, dp(4), 0, dp(8))
            })
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val enabled = CheckBox(this).apply {
                text = if (medicine.enabled) "Activo" else "Pausado"
                isChecked = medicine.enabled
                setOnCheckedChangeListener { _, checked ->
                    val updated = medicine.copy(enabled = checked)
                    val all = MedicineStore.get(this@MainActivity).map { if (it.id == medicine.id) updated else it }
                    MedicineStore.save(this@MainActivity, all)
                    if (checked) AlarmScheduler.schedule(this@MainActivity, updated)
                    else AlarmScheduler.cancel(this@MainActivity, medicine.id)
                    renderMedicines()
                }
            }
            val delete = Button(this).apply {
                text = "Eliminar"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Eliminar recordatorio")
                        .setMessage("¿Quieres eliminar ${medicine.name}?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Eliminar") { _, _ ->
                            AlarmScheduler.cancel(this@MainActivity, medicine.id)
                            MedicineStore.save(this@MainActivity, MedicineStore.get(this@MainActivity).filterNot { it.id == medicine.id })
                            renderMedicines()
                        }.show()
                }
            }
            actions.addView(enabled, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(delete)
            card.addView(actions)
            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = dp(12)
            list.addView(card, params)
        }
    }

    private fun showAddDialog() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }
        val name = EditText(this).apply {
            hint = "Nombre del medicamento"
            singleLine = true
        }
        val dose = EditText(this).apply {
            hint = "Dosis o indicación (opcional)"
            singleLine = true
        }
        val timeButton = Button(this).apply {
            text = "Elegir hora: 08:00"
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    selectedHour = h
                    selectedMinute = m
                    text = String.format(Locale.getDefault(), "Elegir hora: %02d:%02d", h, m)
                }, selectedHour, selectedMinute, true).show()
            }
        }
        panel.addView(name)
        panel.addView(dose)
        panel.addView(timeButton)
        AlertDialog.Builder(this)
            .setTitle("Nuevo recordatorio")
            .setView(panel)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val medicineName = name.text.toString().trim()
                        if (medicineName.isBlank()) {
                            name.error = "Escribe el nombre del medicamento"
                            return@setOnClickListener
                        }
                        val all = MedicineStore.get(this)
                        val newId = (all.maxOfOrNull { it.id } ?: 100) + 1
                        val medicine = Medicine(newId, medicineName, dose.text.toString().trim(), selectedHour, selectedMinute)
                        all.add(medicine)
                        MedicineStore.save(this, all)
                        AlarmScheduler.schedule(this, medicine)
                        renderMedicines()
                        Toast.makeText(this, "Recordatorio guardado", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 501)
        }
    }

    private fun showExactAlarmInfoIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!manager.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("Permitir recordatorios puntuales")
                    .setMessage("Para mejorar la puntualidad, activa «Alarmas y recordatorios» para RecoMed en los ajustes del sistema. Si no lo haces, Android intentará usar una alarma no exacta.")
                    .setNegativeButton("Ahora no", null)
                    .setPositiveButton("Abrir ajustes") { _, _ ->
                        runCatching {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:$packageName")))
                        }
                    }.show()
            }
        }
    }
}
