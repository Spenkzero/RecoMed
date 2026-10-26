package uy.recomed.app

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private var selectedHour = 8
    private var selectedMinute = 0
    private var selectedIcon = "Tableta"
    private var selectedColor = 0xFF176B5B.toInt()
    private var selectedRoutine = "Diario"
    private var selectedTone = "default"
    private var customAudioUri: String = ""
    private lateinit var customToneStatusView: TextView

    private var lastTheme = ""
    private var lastFont = ""
    private var currentFilter = "Todos"
    private lateinit var filterButton: Button

    private val filterOptions = listOf("Todos", "Diario", "Mañana", "Tarde", "Noche", "Tratamiento 7 días")

    private val icons = listOf("Tableta", "Jarabe", "Inyección", "Gotas", "Inhalador", "Cápsula", "General")
    private val colorOptions = listOf(
        Pair("Verde", 0xFF176B5B.toInt()),
        Pair("Azul", 0xFF1A5276.toInt()),
        Pair("Morado", 0xFF6C3483.toInt()),
        Pair("Ámbar", 0xFFB7950B.toInt()),
        Pair("Rojo", 0xFFC0392B.toInt())
    )
    private val routines = listOf("Diario", "Mañana", "Tarde", "Noche", "Tratamiento 7 días")
    private val tones = listOf(
        Pair("Alarma predeterminada", "default"),
        Pair("Notificación suave", "suave"),
        Pair("Alarma de alta prioridad", "urgente")
    )

    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            customAudioUri = uri.toString()
            customToneStatusView.text = "✓ Audio multimedia vinculado correctamente"
            Toast.makeText(this, "Audio multimedia seleccionado", Toast.LENGTH_SHORT).show()
        }
    }

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("recomed_prefs", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme", "Sistema (Automático)") ?: "Sistema (Automático)"
        lastTheme = savedTheme
        lastFont = prefs.getString("font_size", "Normal") ?: "Normal"

        when (savedTheme) {
            "Modo Claro" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Modo Oscuro" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        requestNotificationPermission()
        showExactAlarmInfoIfNeeded()
        buildScreen()
        renderMedicines()
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("recomed_prefs", MODE_PRIVATE)
        val theme = prefs.getString("theme", "Sistema (Automático)") ?: "Sistema (Automático)"
        val font = prefs.getString("font_size", "Normal") ?: "Normal"

        if (lastTheme != theme || lastFont != font) {
            lastTheme = theme
            lastFont = font
            recreate()
            return
        }
        buildScreen()
        renderMedicines()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun View.addClickAnimation() {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.85f).setDuration(80).start()
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(80).start()
                    v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(90).start()
                }
            }
            true
        }
    }

    private fun createRoundedBackground(color: Int, radiusDp: Int = 12): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }
    }

    private fun createCardBackground(color: Int, borderColor: Int, radiusDp: Int = 18): GradientDrawable {
        return createRoundedBackground(color, radiusDp).apply {
            setStroke(dp(1), borderColor)
        }
    }

    private fun buildScreen() {
        val prefs = getSharedPreferences("recomed_prefs", MODE_PRIVATE)
        val fontScaleSetting = prefs.getString("font_size", "Normal") ?: "Normal"
        val baseTextSize = when (fontScaleSetting) {
            "Grande" -> 19f
            "Extra Grande" -> 22f
            else -> 16f
        }

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = RecoMedPalette.forMode(isDarkMode)
        val bgColor = palette.background
        val cardBgColor = palette.surface
        val textColorPrimary = palette.primaryText
        val textColorSecondary = palette.secondaryText
        val brandColor = palette.accent
        val onBrandColor = if (isDarkMode) 0xFF10221D.toInt() else 0xFFFFFFFF.toInt()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            setBackgroundColor(bgColor)
        }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        // --- ACTIVE ALARM BANNER (STOP ALARM BUTTON) ---
        val activeAlarmId = prefs.getInt("active_alarm_id", -1)
        val activeAlarmName = prefs.getString("active_alarm_name", null)
        if (activeAlarmId != -1 && activeAlarmName != null) {
            val alarmBanner = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = createRoundedBackground(palette.danger, 18)
                gravity = Gravity.CENTER_VERTICAL
                elevation = dp(2).toFloat()
            }
            val alarmText = TextView(this).apply {
                text = "🔔 Alarma Activa: $activeAlarmName"
                textSize = baseTextSize
                setTextColor(onBrandColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val stopAlarmButton = Button(this).apply {
                text = "🛑 Detener Alarma"
                textSize = baseTextSize - 2f
                setTextColor(palette.danger)
                background = createRoundedBackground(palette.surface, 12)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                addClickAnimation()
                setOnClickListener {
                    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(activeAlarmId)
                    prefs.edit().remove("active_alarm_id").remove("active_alarm_name").apply()
                    Toast.makeText(this@MainActivity, "Alarma detenida", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            }
            alarmBanner.addView(alarmText)
            alarmBanner.addView(stopAlarmButton)
            content.addView(alarmBanner)
            content.addView(android.widget.Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, dp(12)) })
        }

        // --- COMPACT HEADER CARD WITH SMALL LOGO & SLOGAN ---
        val heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = createCardBackground(cardBgColor, palette.border, 22)
            elevation = dp(2).toFloat()
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logoImageView = ImageView(this).apply {
            setImageResource(R.drawable.recomed_logo)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(12)
            }
        }

        val brandTitleLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val brandNameLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val recoText = TextView(this).apply {
            text = "RECO"
            textSize = baseTextSize + 4f
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.1f
        }

        val medText = TextView(this).apply {
            text = "MED"
            textSize = baseTextSize + 4f
            setTextColor(if (isDarkMode) 0xFF2DD4BF.toInt() else 0xFF176B5B.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.1f
        }

        brandNameLayout.addView(recoText)
        brandNameLayout.addView(medText)

        val taglineText = TextView(this).apply {
            text = "RECORDAR TAMBIÉN ES CUIDARSE"
            textSize = baseTextSize - 5f
            setTextColor(textColorSecondary)
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.1f
        }

        brandTitleLayout.addView(brandNameLayout)
        brandTitleLayout.addView(taglineText)

        val settingsButton = Button(this).apply {
            text = "Ajustes"
            isAllCaps = false
            textSize = baseTextSize - 2f
            setTextColor(onBrandColor)
            background = createRoundedBackground(brandColor, 10)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            addClickAnimation()
            setOnClickListener {
                settingsLauncher.launch(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        topRow.addView(logoImageView)
        topRow.addView(brandTitleLayout)
        topRow.addView(settingsButton)
        heroCard.addView(topRow)

        // --- ACTION BUTTONS (FILTER & ADD) ---
        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }

        filterButton = Button(this).apply {
            text = "Filtro: $currentFilter"
            isAllCaps = false
            textSize = baseTextSize - 1f
            setTextColor(textColorPrimary)
            background = createRoundedBackground(palette.accentSoft, 14)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addClickAnimation()
            setOnClickListener {
                val currentIndex = filterOptions.indexOf(currentFilter)
                val nextIndex = (currentIndex + 1) % filterOptions.size
                currentFilter = filterOptions[nextIndex]
                text = "Filtro: $currentFilter"
                renderMedicines()
                Toast.makeText(this@MainActivity, "Mostrando: $currentFilter", Toast.LENGTH_SHORT).show()
            }
        }

        val addButton = Button(this).apply {
            text = "+ Nuevo Tratamiento"
            isAllCaps = false
            textSize = baseTextSize - 1f
            setTextColor(onBrandColor)
            background = createRoundedBackground(brandColor, 14)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addClickAnimation()
            setOnClickListener { showAddDialog() }
        }

        actionsLayout.addView(filterButton, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
        actionsLayout.addView(addButton, LinearLayout.LayoutParams(0, -2, 1.2f))
        heroCard.addView(actionsLayout)

        // --- SECTION HEADING ---
        val heading = TextView(this).apply {
            text = "Mis tratamientos"
            textSize = baseTextSize + 3f
            setTextColor(textColorPrimary)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(12))
        }

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val note = TextView(this).apply {
            text = "RecoMed asiste en tus horarios clínicos. Consulta siempre con tu médico tratante."
            textSize = baseTextSize - 4f
            setTextColor(textColorSecondary)
            setTypeface(null, android.graphics.Typeface.ITALIC)
            setPadding(0, dp(24), 0, dp(8))
            gravity = Gravity.CENTER
        }

        content.addView(heroCard)
        content.addView(heading)
        content.addView(list)
        content.addView(note)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun renderMedicines() {
        list.removeAllViews()
        val allMedicines = MedicineStore.get(this)
        val medicines = allMedicines
            .filter { currentFilter == "Todos" || it.routine.equals(currentFilter, ignoreCase = true) }
            .sortedWith(compareBy<Medicine> { it.hour }.thenBy { it.minute })

        val fontScaleSetting = getSharedPreferences("recomed_prefs", MODE_PRIVATE).getString("font_size", "Normal") ?: "Normal"
        val baseTextSize = when (fontScaleSetting) {
            "Grande" -> 19f
            "Extra Grande" -> 22f
            else -> 16f
        }

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = RecoMedPalette.forMode(isDarkMode)
        val cardBgColor = palette.surface
        val titleColor = palette.primaryText
        val descColor = palette.secondaryText
        val emptyTextColor = palette.secondaryText

        if (medicines.isEmpty()) {
            val emptyCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(28), dp(24), dp(28))
                background = createCardBackground(cardBgColor, palette.border)
                gravity = Gravity.CENTER
                elevation = dp(2).toFloat()
            }
            emptyCard.addView(TextView(this).apply {
                text = if (allMedicines.isEmpty()) {
                    "No hay tratamientos registrados. Pulse en '+ Nuevo Tratamiento' para programar su primer recordatorio clínico."
                } else {
                    "No hay medicamentos en la rutina \"$currentFilter\"."
                }
                textSize = baseTextSize
                setTextColor(emptyTextColor)
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            list.addView(emptyCard)
            return
        }

        medicines.forEach { medicine ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(14))
                background = createCardBackground(cardBgColor, palette.border)
                elevation = dp(2).toFloat()
                addClickAnimation()
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val iconBadge = TextView(this).apply {
                text = " ${medicine.icon} "
                textSize = baseTextSize
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                background = createRoundedBackground(medicine.color, 8)
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }

            val titleLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
            }

            val titleView = TextView(this).apply {
                text = medicine.name
                textSize = baseTextSize + 1f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(titleColor)
            }

            val toneDesc = if (medicine.customToneUri.isNotBlank()) "Audio personalizado" else "Tono: ${medicine.alarmTone}"
            val routineTag = TextView(this).apply {
                text = "Rutina: ${medicine.routine}  ·  $toneDesc"
                textSize = baseTextSize - 3f
                setTextColor(descColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            titleLayout.addView(titleView)
            titleLayout.addView(routineTag)

            topRow.addView(iconBadge)
            topRow.addView(titleLayout, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(topRow)

            val time = String.format(Locale.getDefault(), "%02d:%02d", medicine.hour, medicine.minute)
            val timeView = TextView(this).apply {
                text = "Horario programado: $time  ·  Dosis: ${medicine.dose.ifBlank { "Indicación general" }}"
                textSize = baseTextSize
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(titleColor)
                setPadding(0, dp(12), 0, dp(10))
            }
            card.addView(timeView)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(0, dp(4), 0, dp(8)) }
                setBackgroundColor(palette.border)
            }
            card.addView(divider)

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val enabled = CheckBox(this).apply {
                text = if (medicine.enabled) "Estado: Activo" else "Estado: Pausado"
                textSize = baseTextSize - 2f
                setTextColor(descColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    buttonTintList = android.content.res.ColorStateList.valueOf(palette.accent)
                }
                isChecked = medicine.enabled
                setOnCheckedChangeListener { _, checked ->
                    text = if (checked) "Estado: Activo" else "Estado: Pausado"
                    val updated = medicine.copy(enabled = checked)
                    val all = MedicineStore.get(this@MainActivity).map { if (it.id == medicine.id) updated else it }
                    MedicineStore.save(this@MainActivity, all)
                    if (checked) AlarmScheduler.schedule(this@MainActivity, updated)
                    else AlarmScheduler.cancel(this@MainActivity, medicine.id)
                }
            }

            val delete = Button(this).apply {
                text = "Eliminar"
                isAllCaps = false
                textSize = baseTextSize - 2f
                setTextColor(palette.danger)
                background = createRoundedBackground(palette.dangerSoft, 12)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                addClickAnimation()
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Eliminar tratamiento")
                        .setMessage("¿Desea eliminar permanentemente el registro de ${medicine.name}?")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Eliminar") { _, _ ->
                            AlarmScheduler.cancel(this@MainActivity, medicine.id)
                            MedicineStore.save(this@MainActivity, MedicineStore.get(this@MainActivity).filterNot { it.id == medicine.id })
                            renderMedicines()
                            Toast.makeText(this@MainActivity, "Tratamiento eliminado", Toast.LENGTH_SHORT).show()
                        }.show()
                }
            }

            actions.addView(enabled, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(delete)
            card.addView(actions)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = dp(14)
            list.addView(card, params)
        }
    }

    private fun showAddDialog() {
        customAudioUri = ""
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(12), dp(22), dp(12))
        }

        val name = EditText(this).apply {
            hint = "Nombre del medicamento o tratamiento"
            isSingleLine = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val dose = EditText(this).apply {
            hint = "Dosis (ej. 1 tableta, 5ml, 1 cápsula)"
            isSingleLine = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        val timeButton = Button(this).apply {
            text = String.format(Locale.getDefault(), "🕒 Horario de toma: %02d:%02d", selectedHour, selectedMinute)
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    selectedHour = h
                    selectedMinute = m
                    text = String.format(Locale.getDefault(), "🕒 Horario de toma: %02d:%02d", h, m)
                }, selectedHour, selectedMinute, true).show()
            }
        }

        val iconLabel = TextView(this).apply {
            text = "Formato farmacéutico:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        }
        val iconLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        icons.forEach { ic ->
            val btn = Button(this).apply {
                text = ic.take(3)
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                addClickAnimation()
                setOnClickListener {
                    selectedIcon = ic
                    Toast.makeText(this@MainActivity, "Formato: $ic", Toast.LENGTH_SHORT).show()
                }
            }
            iconLayout.addView(btn, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(2, 0, 2, 0) })
        }

        val routineLabel = TextView(this).apply {
            text = "Rutina clínica:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        }
        val routineButton = Button(this).apply {
            text = "Rutina: $selectedRoutine"
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Seleccionar rutina")
                    .setItems(routines.toTypedArray()) { _, which ->
                        selectedRoutine = routines[which]
                        text = "Rutina: $selectedRoutine"
                    }.show()
            }
        }

        val toneButton = Button(this).apply {
            text = "Tono de alarma estándar"
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener {
                val toneNames = tones.map { it.first }.toTypedArray()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Seleccionar tono")
                    .setItems(toneNames) { _, which ->
                        selectedTone = tones[which].second
                        text = "Tono: ${toneNames[which]}"
                    }.show()
            }
        }

        customToneStatusView = TextView(this).apply {
            text = "Ningún archivo de audio multimedia vinculado"
            textSize = 12f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(6), 0, dp(6))
        }

        val customAudioButton = Button(this).apply {
            text = "📁 Vincular audio personalizado"
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener {
                audioPickerLauncher.launch("audio/*")
            }
        }

        // --- COLOR SELECTOR WITH COLOR SQUARES (NO COLOR NAMES) ---
        val colorLabel = TextView(this).apply {
            text = "Color identificativo:"
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        }

        val colorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        colorOptions.forEach { pair ->
            val colorVal = pair.second
            val colorSquare = View(this).apply {
                background = createRoundedBackground(colorVal, 6)
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    setMargins(0, 0, dp(8), 0)
                }
                addClickAnimation()
                setOnClickListener {
                    selectedColor = colorVal
                    Toast.makeText(this@MainActivity, "Color seleccionado", Toast.LENGTH_SHORT).show()
                }
            }
            colorLayout.addView(colorSquare)
        }

        panel.addView(name)
        panel.addView(dose)
        panel.addView(timeButton)
        panel.addView(iconLabel)
        panel.addView(iconLayout)
        panel.addView(routineLabel)
        panel.addView(routineButton)
        panel.addView(toneButton)
        panel.addView(customToneStatusView)
        panel.addView(customAudioButton)
        panel.addView(colorLabel)
        panel.addView(colorLayout)

        AlertDialog.Builder(this)
            .setTitle("Programar Nuevo Tratamiento")
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
                        val medicine = Medicine(
                            id = newId,
                            name = medicineName,
                            dose = dose.text.toString().trim(),
                            hour = selectedHour,
                            minute = selectedMinute,
                            icon = selectedIcon,
                            color = selectedColor,
                            routine = selectedRoutine,
                            alarmTone = selectedTone,
                            customToneUri = customAudioUri
                        )
                        all.add(medicine)
                        MedicineStore.save(this, all)
                        AlarmScheduler.schedule(this, medicine)
                        renderMedicines()
                        Toast.makeText(this, "¡Tratamiento programado con éxito!", Toast.LENGTH_SHORT).show()
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
                    .setMessage("Para máxima precisión en sus tomas, active «Alarmas y recordatorios» para RecoMed en los ajustes del sistema.")
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
