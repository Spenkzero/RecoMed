package uy.recomed.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {
    private var selectedTheme = "Sistema (Automático)"
    private var selectedFont = "Normal"

    private lateinit var themeSystemBtn: Button
    private lateinit var themeLightBtn: Button
    private lateinit var themeDarkBtn: Button

    private lateinit var fontNormalBtn: Button
    private lateinit var fontLargeBtn: Button
    private lateinit var fontXLargeBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.title = "Configuración"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("recomed_prefs", Context.MODE_PRIVATE)
        selectedTheme = prefs.getString("theme", "Sistema (Automático)") ?: "Sistema (Automático)"
        selectedFont = prefs.getString("font_size", "Normal") ?: "Normal"

        val fontScaleSetting = prefs.getString("font_size", "Normal") ?: "Normal"
        val baseTextSize = when (fontScaleSetting) {
            "Grande" -> 19f
            "Extra Grande" -> 22f
            else -> 16f
        }

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = RecoMedPalette.forMode(isDarkMode)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(palette.background)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val title = TextView(this).apply {
            text = "Apariencia y Accesibilidad"
            textSize = baseTextSize + 4f
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }

        // --- SECCIÓN TEMA ---
        val themeLabel = TextView(this).apply {
            text = "Modo de Tema:"
            textSize = baseTextSize
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 10, 0, 10)
        }

        val themeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        themeSystemBtn = Button(this).apply {
            text = "Sistema"
            isAllCaps = false
            textSize = baseTextSize - 1f
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener {
                updateThemeSelection("Sistema (Automático)")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
        themeLightBtn = Button(this).apply {
            text = "Claro"
            isAllCaps = false
            textSize = baseTextSize - 1f
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener {
                updateThemeSelection("Modo Claro")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
        themeDarkBtn = Button(this).apply {
            text = "Oscuro"
            isAllCaps = false
            textSize = baseTextSize - 1f
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener {
                updateThemeSelection("Modo Oscuro")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        val btnParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        }
        themeLayout.addView(themeSystemBtn, btnParams)
        themeLayout.addView(themeLightBtn, btnParams)
        themeLayout.addView(themeDarkBtn, btnParams)

        // --- SECCIÓN TAMAÑO DE FUENTE ---
        val fontLabel = TextView(this).apply {
            text = "Tamaño de Fuente:"
            textSize = baseTextSize
            setTextColor(palette.primaryText)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(30), 0, dp(10))
        }

        val fontLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        fontNormalBtn = Button(this).apply {
            text = "Normal"
            isAllCaps = false
            textSize = baseTextSize - 1f
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener { updateFontSelection("Normal") }
        }
        fontLargeBtn = Button(this).apply {
            text = "Grande"
            isAllCaps = false
            textSize = baseTextSize - 1f
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener { updateFontSelection("Grande") }
        }
        fontXLargeBtn = Button(this).apply {
            text = "Extra Grande"
            isAllCaps = false
            textSize = baseTextSize - 1f
            setTypeface(null, android.graphics.Typeface.BOLD)
            addClickAnimation()
            setOnClickListener { updateFontSelection("Extra Grande") }
        }

        fontLayout.addView(fontNormalBtn, btnParams)
        fontLayout.addView(fontLargeBtn, btnParams)
        fontLayout.addView(fontXLargeBtn, btnParams)

        // --- BOTÓN GUARDAR ---
        val saveButton = Button(this).apply {
            text = "Guardar y Salir"
            isAllCaps = false
            textSize = baseTextSize
            background = createRoundedBackground(palette.accent, 14)
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(14))
            addClickAnimation()
            setOnClickListener {
                prefs.edit()
                    .putString("theme", selectedTheme)
                    .putString("font_size", selectedFont)
                    .apply()

                Toast.makeText(this@SettingsActivity, "¡Preferencias guardadas!", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }

        content.addView(title)
        content.addView(themeLabel)
        content.addView(themeLayout)
        content.addView(fontLabel)
        content.addView(fontLayout)
        content.addView(android.widget.Space(this).apply { layoutParams = LinearLayout.LayoutParams(0, 50) })
        content.addView(saveButton, LinearLayout.LayoutParams(-1, -2))

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        setContentView(root)

        updateThemeSelection(selectedTheme, saveToMemory = false)
        updateFontSelection(selectedFont, saveToMemory = false)
    }

    private fun View.addClickAnimation() {
        setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).alpha(0.85f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(90).start()
                    v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(90).start()
                }
            }
            true
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createRoundedBackground(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
        }
    }

    private fun updateThemeSelection(theme: String, saveToMemory: Boolean = true) {
        if (saveToMemory) {
            selectedTheme = theme
            getSharedPreferences("recomed_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("theme", theme)
                .apply()
        }
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = RecoMedPalette.forMode(isDarkMode)
        val inactiveColor = palette.accentSoft
        val activeTextColor = if (isDarkMode) 0xFF10221D.toInt() else 0xFFFFFFFF.toInt()

        themeSystemBtn.background = createRoundedBackground(if (theme == "Sistema (Automático)") palette.accent else inactiveColor, 12)
        themeSystemBtn.setTextColor(if (theme == "Sistema (Automático)") activeTextColor else palette.primaryText)

        themeLightBtn.background = createRoundedBackground(if (theme == "Modo Claro") palette.accent else inactiveColor, 12)
        themeLightBtn.setTextColor(if (theme == "Modo Claro") activeTextColor else palette.primaryText)

        themeDarkBtn.background = createRoundedBackground(if (theme == "Modo Oscuro") palette.accent else inactiveColor, 12)
        themeDarkBtn.setTextColor(if (theme == "Modo Oscuro") activeTextColor else palette.primaryText)
    }

    private fun updateFontSelection(font: String, saveToMemory: Boolean = true) {
        if (saveToMemory) {
            selectedFont = font
            getSharedPreferences("recomed_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("font_size", font)
                .apply()
        }
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val palette = RecoMedPalette.forMode(isDarkMode)
        val inactiveColor = palette.accentSoft
        val activeTextColor = if (isDarkMode) 0xFF10221D.toInt() else 0xFFFFFFFF.toInt()

        fontNormalBtn.background = createRoundedBackground(if (font == "Normal") palette.accent else inactiveColor, 12)
        fontNormalBtn.setTextColor(if (font == "Normal") activeTextColor else palette.primaryText)

        fontLargeBtn.background = createRoundedBackground(if (font == "Grande") palette.accent else inactiveColor, 12)
        fontLargeBtn.setTextColor(if (font == "Grande") activeTextColor else palette.primaryText)

        fontXLargeBtn.background = createRoundedBackground(if (font == "Extra Grande") palette.accent else inactiveColor, 12)
        fontXLargeBtn.setTextColor(if (font == "Extra Grande") activeTextColor else palette.primaryText)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
