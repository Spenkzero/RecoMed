package uy.recomed.app

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import java.time.LocalDate
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val STATE_SELECTED_TAB = "selected_tab"
        private const val KEY_SELECTED_TAB = "selected_tab"
    }

    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var pageContainer: FrameLayout
    private lateinit var bottomNavigation: LinearLayout
    private val pages = mutableListOf<ScrollView>()
    private val navigationItems = mutableListOf<TextView>()
    private var selectedHour = 8
    private var selectedMinute = 0
    private var selectedIcon = "Tableta"
    private var selectedColor = 0xFF176B5B.toInt()
    private var selectedRoutine = "Diario"

    private var lastTheme = ""
    private var lastFont = ""
    private var currentFilter = "Todos"
    private lateinit var filterButton: Button
    private var selectedTab = 0
    private var trackingPeriodDays = 7
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeStartedOnChart = false
    private lateinit var trackingPeriodButtons: List<Button>
    private var settingsAudioStatusView: TextView? = null

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
    private val settingsAudioPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    getSharedPreferences("recomed_prefs", MODE_PRIVATE).edit()
                        .putString("notification_sound", "custom")
                        .putString("notification_custom_uri", uri.toString())
                        .apply()
                    settingsAudioStatusView?.text = "Tono personalizado seleccionado"
                    recreate()
                } catch (error: SecurityException) {
                    android.util.Log.e("RecoMed", "No se pudo guardar el permiso del tono seleccionado", error)
                    Toast.makeText(this, "No se pudo acceder al archivo de audio", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedTab = savedInstanceState?.getInt(STATE_SELECTED_TAB)
            ?: getSharedPreferences("recomed_prefs", MODE_PRIVATE).getInt(KEY_SELECTED_TAB, 0)

        val prefs = getSharedPreferences("recomed_prefs", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme", "Sistema (Automático)") ?: "Sistema (Automático)"
        lastTheme = savedTheme
        lastFont = prefs.getString("font_size", "Normal") ?: "Normal"

        when (savedTheme) {
            "Modo Claro" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "Modo Oscuro" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }

        buildScreen()
        renderMedicines()
        if (selectedTab == 1) renderTracking()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                swipeStartY = event.y
                swipeStartedOnChart = if (::trackingChart.isInitialized) {
                    val location = IntArray(2)
                    trackingChart.getLocationOnScreen(location)
                    event.rawX >= location[0] &&
                        event.rawX <= location[0] + trackingChart.width &&
                        event.rawY >= location[1] &&
                        event.rawY <= location[1] + trackingChart.height
                } else {
                    false
                }
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - swipeStartX
                val deltaY = event.y - swipeStartY
                if (!swipeStartedOnChart &&
                    kotlin.math.abs(deltaX) > dp(72) &&
                    kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 1.3f) {
                    selectTab(
                        (selectedTab + if (deltaX < 0) 1 else -1).coerceIn(0, 2),
                        animateTransition = true
                    )
                }
            }
        }
        return super.dispatchTouchEvent(event)
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
        if (selectedTab == 1) renderTracking()
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

    private fun buildBottomNavigation(palette: RecoMedPalette, baseTextSize: Float): LinearLayout {
        navigationItems.clear()
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(palette.surface)
            elevation = dp(8).toFloat()
            listOf("Tratamientos", "Seguimiento", "Ajustes").forEachIndexed { index, label ->
                val item = TextView(this@MainActivity).apply {
                    text = label
                    textSize = (baseTextSize - 4f).coerceAtLeast(12f)
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(dp(4), dp(8), dp(4), dp(8))
                    minHeight = dp(56)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { selectTab(index) }
                }
                navigationItems.add(item)
                addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                })
            }
        }
    }

    private fun updateBottomNavigation(palette: RecoMedPalette) {
        navigationItems.forEachIndexed { index, item ->
            val selected = index == selectedTab
            item.background = createRoundedBackground(
                if (selected) palette.accentSoft else Color.TRANSPARENT,
                14
            )
            item.setTextColor(if (selected) palette.accent else palette.secondaryText)
            item.contentDescription = when (index) {
                0 -> "Pestaña Tratamientos"
                1 -> "Pestaña Seguimiento"
                else -> "Pestaña Ajustes"
            }
            item.isSelected = selected
        }
    }

    private fun selectTab(index: Int, animateTransition: Boolean = false) {
        if (index !in pages.indices || index == selectedTab) return
        val previousTab = selectedTab
        selectedTab = index
        pages.forEachIndexed { pageIndex, page ->
            page.animate().cancel()
            if (pageIndex != selectedTab) {
                page.visibility = View.GONE
                page.translationX = 0f
                page.alpha = 1f
            }
        }
        val selectedPage = pages[selectedTab]
        selectedPage.visibility = View.VISIBLE
        if (animateTransition) {
            selectedPage.translationX = if (selectedTab > previousTab) dp(24).toFloat() else -dp(24).toFloat()
            selectedPage.alpha = 0.85f
            selectedPage.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        getSharedPreferences("recomed_prefs", MODE_PRIVATE)
            .edit().putInt(KEY_SELECTED_TAB, selectedTab).apply()
        updateBottomNavigation(RecoMedPalette.forMode(isDarkMode()))
        if (selectedTab == 1) renderTracking()
    }

    private fun isDarkMode() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun buildTrackingPage(
        palette: RecoMedPalette,
        baseTextSize: Float,
        expandedText: Boolean
    ): ScrollView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        content.addView(TextView(this).apply {
            text = "Seguimiento"
            textSize = baseTextSize + 7f
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Revisa las dosis que registraste como tomadas u omitidas."
            textSize = baseTextSize
            setTextColor(palette.secondaryText)
            setPadding(0, dp(6), 0, dp(18))
        })

        val periodSelector = LinearLayout(this).apply {
            orientation = if (expandedText) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        trackingPeriodButtons = listOf(7 to "Últimos 7 días", 30 to "Últimos 30 días").map { (days, label) ->
            val button = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = baseTextSize - 2f
                setOnClickListener {
                    trackingPeriodDays = days
                    renderTracking()
                }
            }
            val params = if (expandedText) {
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            } else {
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (days == 7) rightMargin = dp(6) else leftMargin = dp(6)
                }
            }
            periodSelector.addView(button, params)
            button
        }
        content.addView(periodSelector)

        val summaryCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = createCardBackground(palette.surface, palette.border)
        }
        content.addView(summaryCard, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })
        val summary = TextView(this).apply {
            textSize = baseTextSize + 1f
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        summaryCard.addView(summary)
        val chartRange = TextView(this).apply {
            textSize = baseTextSize - 2f
            setTextColor(palette.secondaryText)
            setPadding(0, dp(10), 0, dp(2))
        }
        summaryCard.addView(chartRange)
        val chart = AdherenceChartView(this, palette, baseTextSize - 3f).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(210))
            onVisibleRangeChanged = { range -> chartRange.text = range }
        }
        summaryCard.addView(chart)

        val legend = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        addLegendItem(legend, "Tomadas", palette.accent, baseTextSize - 3f, palette)
        addLegendItem(legend, "Omitidas", palette.danger, baseTextSize - 3f, palette)
        summaryCard.addView(legend)
        content.addView(TextView(this).apply {
            text = "En la vista de 30 días, desliza el gráfico o arrastra la barra guía para recorrer las fechas."
            textSize = baseTextSize - 3f
            setTextColor(palette.secondaryText)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })

        val note = TextView(this).apply {
            text = "El gráfico cuenta solo las dosis que registraste. RecoMed no marca dosis omitidas automáticamente; si no registras una dosis, no se incluye en el resumen."
            textSize = baseTextSize - 2f
            setTextColor(palette.secondaryText)
            setPadding(0, dp(18), 0, dp(8))
        }
        content.addView(note)
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }.also {
            trackingSummary = summary
            trackingChart = chart
        }
    }

    private fun addLegendItem(
        container: LinearLayout,
        label: String,
        color: Int,
        textSize: Float,
        palette: RecoMedPalette
    ) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        item.addView(View(this).apply {
            background = createRoundedBackground(color, 4)
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                rightMargin = dp(6)
            }
        })
        item.addView(TextView(this).apply {
            text = label
            this.textSize = textSize
            setTextColor(palette.secondaryText)
        })
        container.addView(item)
    }

    private lateinit var trackingSummary: TextView
    private lateinit var trackingChart: AdherenceChartView

    private fun renderTracking() {
        if (!::trackingSummary.isInitialized || !::trackingChart.isInitialized) return
        val today = LocalDate.now()
        val firstDay = today.minusDays((trackingPeriodDays - 1).toLong())
        val records = DoseRecordStore.get(this).filter {
            val date = runCatching { LocalDate.parse(it.date) }.getOrNull()
            date != null && date >= firstDay && date <= today
        }
        val taken = records.count { it.status == DoseRecord.Status.TAKEN }
        val missed = records.count { it.status == DoseRecord.Status.MISSED }
        val total = taken + missed
        val percentage = if (total == 0) 0 else (taken * 100 / total)
        val palette = RecoMedPalette.forMode(isDarkMode())
        trackingPeriodButtons.forEachIndexed { index, button ->
            val selected = (index == 0 && trackingPeriodDays == 7) || (index == 1 && trackingPeriodDays == 30)
            button.background = createRoundedBackground(
                if (selected) palette.accent else palette.accentSoft,
                12
            )
            button.setTextColor(
                if (selected && !isDarkMode()) Color.WHITE
                else if (selected) 0xFF10221D.toInt()
                else palette.primaryText
            )
        }
        trackingSummary.text = if (total == 0) {
            "Todavía no hay dosis registradas en este período."
        } else {
            "$percentage% tomadas  ·  $taken tomadas  ·  $missed omitidas"
        }
        val byDate = records.groupBy { LocalDate.parse(it.date) }
        trackingChart.updateDays((0 until trackingPeriodDays).map { offset ->
            val date = firstDay.plusDays(offset.toLong())
            val dailyRecords = byDate[date].orEmpty()
            AdherenceDay(
                date = date,
                taken = dailyRecords.count { it.status == DoseRecord.Status.TAKEN },
                missed = dailyRecords.count { it.status == DoseRecord.Status.MISSED }
            )
        })
        trackingChart.contentDescription =
            "Gráfico de los últimos $trackingPeriodDays días: $taken dosis tomadas y $missed omitidas. " +
                "Desliza horizontalmente o arrastra la barra guía para recorrer los días."
    }

    private fun buildSettingsPage(
        palette: RecoMedPalette,
        baseTextSize: Float,
        expandedText: Boolean
    ): ScrollView {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        content.addView(TextView(this).apply {
            text = "Ajustes"
            textSize = baseTextSize + 7f
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        content.addView(TextView(this).apply {
            text = "Personaliza la apariencia y la lectura de RecoMed."
            textSize = baseTextSize
            setTextColor(palette.secondaryText)
            setPadding(0, dp(6), 0, dp(20))
        })

        fun addSettingGroup(title: String, options: List<Pair<String, String>>, selected: String, action: (String) -> Unit) {
            content.addView(TextView(this).apply {
                text = title
                textSize = baseTextSize + 1f
                setTextColor(palette.primaryText)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(18), 0, dp(8))
            })
            val verticalOptions = expandedText || options.size > 3
            val row = LinearLayout(this).apply {
                orientation = if (verticalOptions) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            }
            options.forEachIndexed { index, (value, label) ->
                val button = Button(this).apply {
                    text = label
                    isAllCaps = false
                    textSize = baseTextSize - 2f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    background = createRoundedBackground(
                        if (value == selected) palette.accent else palette.accentSoft,
                        12
                    )
                    setTextColor(
                        if (value == selected && !isDarkMode()) Color.WHITE
                        else if (value == selected) 0xFF10221D.toInt()
                        else palette.primaryText
                    )
                    addClickAnimation()
                    setOnClickListener { action(value) }
                }
                val params = if (verticalOptions) {
                    LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                } else {
                    LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index > 0) leftMargin = dp(5)
                        if (index < options.lastIndex) rightMargin = dp(5)
                    }
                }
                row.addView(button, params)
            }
            content.addView(row)
        }

        val preferences = getSharedPreferences("recomed_prefs", MODE_PRIVATE)
        val currentTheme = preferences.getString("theme", "Sistema (Automático)") ?: "Sistema (Automático)"
        addSettingGroup(
            "Tema",
            listOf(
                "Sistema (Automático)" to "Sistema",
                "Modo Claro" to "Claro",
                "Modo Oscuro" to "Oscuro"
            ),
            currentTheme
        ) { theme ->
            preferences.edit().putString("theme", theme).apply()
            when (theme) {
                "Modo Claro" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "Modo Oscuro" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        val currentFont = preferences.getString("font_size", "Normal") ?: "Normal"
        addSettingGroup(
            "Tamaño del texto",
            listOf("Normal" to "Normal", "Grande" to "Grande", "Extra Grande" to "Extra grande"),
            currentFont
        ) { font ->
            preferences.edit().putString("font_size", font).apply()
            recreate()
        }

        val currentSound = preferences.getString("notification_sound", "default") ?: "default"
        addSettingGroup(
            "Sonido de los recordatorios",
            listOf(
                "default" to "Alarma",
                "soft" to "Suave",
                "intense" to "Intenso",
                "custom" to "Personalizado",
                "silent" to "Sin sonido"
            ),
            currentSound
        ) { sound ->
            if (sound == "custom") {
                settingsAudioPickerLauncher.launch(arrayOf("audio/*"))
            } else {
                preferences.edit().putString("notification_sound", sound).apply()
                recreate()
            }
        }
        val customUri = preferences.getString("notification_custom_uri", null)
        settingsAudioStatusView = TextView(this).apply {
            text = if (customUri.isNullOrBlank()) {
                "No hay un archivo de audio personalizado seleccionado."
            } else {
                "Tono personalizado seleccionado."
            }
            textSize = baseTextSize - 2f
            setTextColor(palette.secondaryText)
            setPadding(0, dp(6), 0, dp(4))
        }
        content.addView(settingsAudioStatusView)
        val chooseAudioButton = Button(this).apply {
            text = "Elegir archivo de audio"
            isAllCaps = false
            textSize = baseTextSize - 2f
            setTextColor(palette.primaryText)
            background = createRoundedBackground(palette.accentSoft, 12)
            addClickAnimation()
            setOnClickListener { settingsAudioPickerLauncher.launch(arrayOf("audio/*")) }
        }
        content.addView(chooseAudioButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(6)
        })

        content.addView(TextView(this).apply {
            text = "Permisos de recordatorio"
            textSize = baseTextSize + 1f
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = "RecoMed solicita permisos solo cuando son necesarios. Puedes gestionar aquí las notificaciones y la precisión de las alarmas."
            textSize = baseTextSize - 2f
            setTextColor(palette.secondaryText)
        })
        val notificationSettingsButton = Button(this).apply {
            text = "Gestionar notificaciones"
            isAllCaps = false
            textSize = baseTextSize - 2f
            setTextColor(palette.primaryText)
            background = createRoundedBackground(palette.accentSoft, 12)
            addClickAnimation()
            setOnClickListener {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    packageName
                )
                try {
                    startActivity(intent)
                } catch (error: android.content.ActivityNotFoundException) {
                    android.util.Log.e("RecoMed", "No se pudo abrir la configuración de notificaciones", error)
                    Toast.makeText(this@MainActivity, "No se pudieron abrir los ajustes de notificaciones", Toast.LENGTH_LONG).show()
                }
            }
        }
        content.addView(notificationSettingsButton, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                content.addView(TextView(this).apply {
                    text = "Alarmas precisas activadas."
                    textSize = baseTextSize - 2f
                    setTextColor(palette.accent)
                    setPadding(0, dp(8), 0, 0)
                })
            } else {
                val exactAlarmButton = Button(this).apply {
                    text = "Configurar alarmas precisas"
                    isAllCaps = false
                    textSize = baseTextSize - 2f
                    setTextColor(palette.primaryText)
                    background = createRoundedBackground(palette.accentSoft, 12)
                    addClickAnimation()
                    setOnClickListener {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                        try {
                            startActivity(intent)
                        } catch (error: android.content.ActivityNotFoundException) {
                            android.util.Log.e("RecoMed", "No se pudo abrir la configuración de alarmas precisas", error)
                            Toast.makeText(this@MainActivity, "No se pudieron abrir los ajustes de alarmas", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                content.addView(exactAlarmButton, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(6)
                })
            }
        }
        addSettingGroup(
            "Vibración",
            listOf(
                "standard" to "Estándar",
                "soft" to "Suave",
                "long" to "Larga",
                "off" to "Desactivada"
            ),
            preferences.getString("notification_vibration", "standard") ?: "standard"
        ) { vibration ->
            preferences.edit().putString("notification_vibration", vibration).apply()
            recreate()
        }
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
    }

    private fun buildScreen() {
        val prefs = getSharedPreferences("recomed_prefs", MODE_PRIVATE)
        val fontScaleSetting = prefs.getString("font_size", "Normal") ?: "Normal"
        val baseTextSize = appBaseTextSize(fontScaleSetting)
        val expandedText = fontScaleSetting == "Extra Grande"

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
                orientation = if (expandedText) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = createRoundedBackground(palette.danger, 18)
                gravity = if (expandedText) Gravity.START else Gravity.CENTER_VERTICAL
                elevation = dp(2).toFloat()
            }
            val alarmText = TextView(this).apply {
                text = "Alarma activa: $activeAlarmName"
                textSize = baseTextSize
                setTextColor(onBrandColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    if (expandedText) -1 else 0,
                    -2,
                    if (expandedText) 0f else 1f
                ).apply {
                    if (expandedText) bottomMargin = dp(10)
                }
            }
            val stopAlarmButton = Button(this).apply {
                text = "Detener alarma"
                isAllCaps = false
                textSize = baseTextSize - 2f
                setTextColor(palette.danger)
                background = createRoundedBackground(palette.surface, 12)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                maxLines = 2
                if (expandedText) layoutParams = LinearLayout.LayoutParams(-1, -2)
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
            setImageResource(R.drawable.recomed_logo_transparent)
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

        topRow.addView(logoImageView)
        topRow.addView(brandTitleLayout)
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
            setOnClickListener { showRoutineFilterDialog(palette, baseTextSize) }
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

        if (expandedText) {
            actionsLayout.orientation = LinearLayout.VERTICAL
            actionsLayout.addView(filterButton, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
            actionsLayout.addView(addButton, LinearLayout.LayoutParams(-1, -2))
        } else {
            actionsLayout.addView(filterButton, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(8) })
            actionsLayout.addView(addButton, LinearLayout.LayoutParams(0, -2, 1.2f))
        }
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

        pages.clear()
        pages.add(scroll)
        pages.add(buildTrackingPage(palette, baseTextSize, expandedText))
        pages.add(buildSettingsPage(palette, baseTextSize, expandedText))
        pageContainer = FrameLayout(this)
        pages.forEachIndexed { index, page ->
            page.visibility = if (index == selectedTab) View.VISIBLE else View.GONE
            pageContainer.addView(page, FrameLayout.LayoutParams(-1, -1))
        }
        bottomNavigation = buildBottomNavigation(palette, baseTextSize)
        root.addView(pageContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(bottomNavigation, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        updateBottomNavigation(palette)
    }

    private fun renderMedicines() {
        list.removeAllViews()
        val allMedicines = MedicineStore.get(this)
        val medicines = allMedicines
            .filter { currentFilter == "Todos" || it.routine.equals(currentFilter, ignoreCase = true) }
            .sortedWith(compareBy<Medicine> { it.hour }.thenBy { it.minute })

        val fontScaleSetting = getSharedPreferences("recomed_prefs", MODE_PRIVATE).getString("font_size", "Normal") ?: "Normal"
        val baseTextSize = appBaseTextSize(fontScaleSetting)
        val expandedText = fontScaleSetting == "Extra Grande"

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
                orientation = if (expandedText) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = if (expandedText) Gravity.START else Gravity.CENTER_VERTICAL
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
                setPadding(if (expandedText) 0 else dp(12), if (expandedText) dp(8) else 0, 0, 0)
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
            topRow.addView(
                titleLayout,
                if (expandedText) LinearLayout.LayoutParams(-1, -2)
                else LinearLayout.LayoutParams(0, -2, 1f)
            )
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

            val today = LocalDate.now()
            val todayRecord = DoseRecordStore.find(this, medicine.id, today)
            val statusText = TextView(this).apply {
                text = when (todayRecord?.status) {
                    DoseRecord.Status.TAKEN -> "Hoy: marcada como tomada"
                    DoseRecord.Status.MISSED -> "Hoy: marcada como omitida"
                    null -> "¿Qué pasó con la dosis de hoy?"
                }
                textSize = baseTextSize - 1f
                setTextColor(
                    when (todayRecord?.status) {
                        DoseRecord.Status.TAKEN -> palette.accent
                        DoseRecord.Status.MISSED -> palette.danger
                        null -> descColor
                    }
                )
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(4), 0, dp(8))
            }
            card.addView(statusText)

            val doseActions = LinearLayout(this).apply {
                orientation = if (expandedText) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            }
            fun doseAction(label: String, status: DoseRecord.Status, color: Int, softColor: Int): Button =
                Button(this).apply {
                    text = label
                    isAllCaps = false
                    textSize = baseTextSize - 2f
                    maxLines = 2
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(if (todayRecord?.status == status) Color.WHITE else color)
                    background = createRoundedBackground(
                        if (todayRecord?.status == status) color else softColor,
                        12
                    )
                    setOnClickListener {
                        DoseRecordStore.record(this@MainActivity, medicine.id, medicine.name, today, status)
                        renderMedicines()
                        renderTracking()
                        val message = if (status == DoseRecord.Status.TAKEN) {
                            "Dosis registrada como tomada"
                        } else {
                            "Dosis registrada como omitida"
                        }
                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            val takenButton = doseAction("Tomada", DoseRecord.Status.TAKEN, palette.accent, palette.accentSoft)
            val missedButton = doseAction("Omitida", DoseRecord.Status.MISSED, palette.danger, palette.dangerSoft)
            if (expandedText) {
                doseActions.addView(takenButton, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(6)
                })
                doseActions.addView(missedButton, LinearLayout.LayoutParams(-1, -2))
            } else {
                doseActions.addView(takenButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    rightMargin = dp(5)
                })
                doseActions.addView(missedButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    leftMargin = dp(5)
                })
            }
            card.addView(doseActions)

            val divider = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(0, dp(4), 0, dp(8)) }
                setBackgroundColor(palette.border)
            }
            card.addView(divider)

            val actions = LinearLayout(this).apply {
                orientation = if (expandedText) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                gravity = if (expandedText) Gravity.START else Gravity.CENTER_VERTICAL
            }

            val edit = Button(this).apply {
                text = "Editar tratamiento"
                isAllCaps = false
                textSize = baseTextSize - 2f
                setTextColor(palette.accent)
                background = createRoundedBackground(palette.accentSoft, 12)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                addClickAnimation()
                setOnClickListener { showMedicineDialog(medicine) }
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

            if (expandedText) {
                actions.addView(edit, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                })
                actions.addView(enabled, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(8)
                })
                actions.addView(delete, LinearLayout.LayoutParams(-1, -2))
            } else {
                actions.addView(enabled, LinearLayout.LayoutParams(0, -2, 1f))
                actions.addView(edit, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    leftMargin = dp(4)
                    rightMargin = dp(4)
                })
                actions.addView(delete, LinearLayout.LayoutParams(0, -2, 1f))
            }
            card.addView(actions)

            val params = LinearLayout.LayoutParams(-1, -2)
            params.bottomMargin = dp(14)
            list.addView(card, params)
        }
    }

    private fun showRoutineFilterDialog(palette: RecoMedPalette, baseTextSize: Float) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(8))
            background = createCardBackground(palette.surface, palette.border, 20)
        }
        content.addView(TextView(this).apply {
            text = "Elegir tratamientos"
            textSize = baseTextSize + 3f
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })
        content.addView(TextView(this).apply {
            text = "Filtra la lista por rutina."
            textSize = baseTextSize - 1f
            setTextColor(palette.secondaryText)
            setPadding(0, 0, 0, dp(14))
        })

        val dialog = AlertDialog.Builder(this)
            .setView(content)
            .setNegativeButton("Cerrar", null)
            .create()

        filterOptions.forEach { option ->
            val selected = option == currentFilter
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = createCardBackground(
                    if (selected) palette.accentSoft else palette.surface,
                    if (selected) palette.accent else palette.border,
                    14
                )
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    currentFilter = option
                    filterButton.text = "Filtro: $currentFilter"
                    renderMedicines()
                    Toast.makeText(this@MainActivity, "Mostrando: $currentFilter", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            row.addView(TextView(this).apply {
                text = if (selected) "Seleccionado" else ""
                textSize = baseTextSize
                setTextColor(if (selected) palette.accent else palette.secondaryText)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(if (selected) dp(94) else dp(12), -2).apply {
                rightMargin = dp(10)
            })
            row.addView(TextView(this).apply {
                text = if (option == "Todos") "Todos los tratamientos" else option
                textSize = baseTextSize - 1f
                setTextColor(palette.primaryText)
                setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(7)
            })
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showAddDialog() = showMedicineDialog(null)

    private fun showMedicineDialog(existingMedicine: Medicine?) {
        selectedHour = existingMedicine?.hour ?: 8
        selectedMinute = existingMedicine?.minute ?: 0
        selectedIcon = existingMedicine?.icon ?: "Tableta"
        selectedColor = existingMedicine?.color ?: colorOptions.first().second
        selectedRoutine = existingMedicine?.routine ?: "Diario"
        val fontScaleSetting = getSharedPreferences("recomed_prefs", MODE_PRIVATE)
            .getString("font_size", "Normal") ?: "Normal"
        val baseTextSize = appBaseTextSize(fontScaleSetting)
        val expandedText = fontScaleSetting == "Extra Grande"
        val palette = RecoMedPalette.forMode(isDarkMode())
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(12))
        }

        val name = EditText(this).apply {
            hint = "Nombre del medicamento o tratamiento"
            isSingleLine = false
            maxLines = 2
            textSize = baseTextSize
            setText(existingMedicine?.name.orEmpty())
            setTextColor(palette.primaryText)
            setHintTextColor(palette.secondaryText)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = createCardBackground(palette.surface, palette.border, 14)
        }
        val dose = EditText(this).apply {
            hint = "Dosis (ej. 1 tableta, 5ml, 1 cápsula)"
            isSingleLine = false
            maxLines = 2
            textSize = baseTextSize
            setText(existingMedicine?.dose.orEmpty())
            setTextColor(palette.primaryText)
            setHintTextColor(palette.secondaryText)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = createCardBackground(palette.surface, palette.border, 14)
        }

        val timeButton = Button(this).apply {
            text = String.format(Locale.getDefault(), "Horario de toma: %02d:%02d", selectedHour, selectedMinute)
            isAllCaps = false
            textSize = baseTextSize
            maxLines = 2
            setTextColor(palette.primaryText)
            background = createRoundedBackground(palette.accentSoft, 14)
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, h, m ->
                    selectedHour = h
                    selectedMinute = m
                    text = String.format(Locale.getDefault(), "Horario de toma: %02d:%02d", h, m)
                }, selectedHour, selectedMinute, true).show()
            }
        }

        val iconLabel = TextView(this).apply {
            text = "Formato farmacéutico:"
            textSize = baseTextSize
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        }
        val iconLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val iconButtons = mutableListOf<Button>()
        icons.forEach { ic ->
            val btn = Button(this).apply {
                text = ic
                isAllCaps = false
                textSize = baseTextSize - 2f
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 2
                setTextColor(if (ic == selectedIcon) palette.accent else palette.primaryText)
                background = createCardBackground(
                    if (ic == selectedIcon) palette.accentSoft else palette.surface,
                    if (ic == selectedIcon) palette.accent else palette.border,
                    12
                )
                iconButtons.add(this)
                addClickAnimation()
                setOnClickListener {
                    selectedIcon = ic
                    iconButtons.forEach { button ->
                        val selected = button.text.toString() == selectedIcon
                        button.setTextColor(if (selected) palette.accent else palette.primaryText)
                        button.background = createCardBackground(
                            if (selected) palette.accentSoft else palette.surface,
                            if (selected) palette.accent else palette.border,
                            12
                        )
                    }
                }
            }
            iconLayout.addView(btn, LinearLayout.LayoutParams(-2, -2).apply {
                setMargins(0, 0, dp(7), 0)
            })
        }
        val iconScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(iconLayout)
        }

        val routineLabel = TextView(this).apply {
            text = "Rutina clínica:"
            textSize = baseTextSize
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        }
        val routineButton = Button(this).apply {
            text = selectedRoutine
            isAllCaps = false
            textSize = baseTextSize
            maxLines = 2
            setTextColor(palette.primaryText)
            background = createRoundedBackground(palette.accentSoft, 14)
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Seleccionar rutina")
                    .setItems(routines.toTypedArray()) { _, which ->
                        selectedRoutine = routines[which]
                        text = selectedRoutine
                    }.show()
            }
        }

        val colorLabel = TextView(this).apply {
            text = "Color identificativo:"
            textSize = baseTextSize
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(6))
        }

        val colorLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val colorSwatches = mutableListOf<Pair<Int, View>>()
        colorOptions.forEach { pair ->
            val colorVal = pair.second
            val colorSquare = View(this).apply {
                background = createCardBackground(
                    colorVal,
                    if (colorVal == selectedColor) palette.accent else palette.surface,
                    8
                )
                alpha = if (colorVal == selectedColor) 1f else 0.55f
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    setMargins(0, 0, dp(8), 0)
                }
                colorSwatches.add(colorVal to this)
                addClickAnimation()
                setOnClickListener {
                    selectedColor = colorVal
                    colorSwatches.forEach { (swatchColor, swatch) ->
                        swatch.alpha = if (swatchColor == selectedColor) 1f else 0.55f
                        swatch.background = createCardBackground(
                            swatchColor,
                            if (swatchColor == selectedColor) palette.accent else palette.surface,
                            8
                        )
                    }
                }
            }
            colorLayout.addView(colorSquare)
        }

        fun addFieldLabel(label: String) {
            panel.addView(TextView(this).apply {
                text = label
                textSize = baseTextSize - 1f
                setTextColor(palette.secondaryText)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(12), 0, dp(6))
            })
        }
        addFieldLabel("Nombre del tratamiento")
        panel.addView(name, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        addFieldLabel("Dosis o indicación (opcional)")
        panel.addView(dose, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        addFieldLabel("Horario")
        panel.addView(timeButton, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        panel.addView(iconLabel)
        panel.addView(iconScroll)
        panel.addView(routineLabel)
        panel.addView(routineButton, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        panel.addView(colorLabel)
        panel.addView(colorLayout)

        val panelScroll = ScrollView(this).apply {
            isFillViewport = false
            addView(panel)
            layoutParams = LinearLayout.LayoutParams(
                -1,
                minOf(dp(560), (resources.displayMetrics.heightPixels * 0.68f).toInt())
            )
        }

        AlertDialog.Builder(this)
            .setTitle(if (existingMedicine == null) "Añadir tratamiento" else "Editar tratamiento")
            .setView(panelScroll)
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
                        val medicine = existingMedicine?.copy(
                            name = medicineName,
                            dose = dose.text.toString().trim(),
                            hour = selectedHour,
                            minute = selectedMinute,
                            icon = selectedIcon,
                            color = selectedColor,
                            routine = selectedRoutine
                        ) ?: Medicine(
                            id = (all.maxOfOrNull { it.id } ?: 100) + 1,
                            name = medicineName,
                            dose = dose.text.toString().trim(),
                            hour = selectedHour,
                            minute = selectedMinute,
                            icon = selectedIcon,
                            color = selectedColor,
                            routine = selectedRoutine
                        )
                        val existingIndex = all.indexOfFirst { it.id == medicine.id }
                        if (existingIndex >= 0) all[existingIndex] = medicine else all.add(medicine)
                        MedicineStore.save(this, all)
                        AlarmScheduler.cancel(this, medicine.id)
                        if (medicine.enabled) AlarmScheduler.schedule(this, medicine)
                        requestNotificationPermission()
                        renderMedicines()
                        val message = if (existingMedicine == null) {
                            "Tratamiento programado"
                        } else {
                            "Cambios del tratamiento guardados"
                        }
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
                dialog.show()
            }
    }

    private fun requestNotificationPermission() {
        val preferences = getSharedPreferences("recomed_prefs", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("notification_permission_requested", false)) {
            preferences.edit().putBoolean("notification_permission_requested", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 501)
        }
    }
}
