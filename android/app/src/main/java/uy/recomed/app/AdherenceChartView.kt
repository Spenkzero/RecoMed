package uy.recomed.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class AdherenceDay(
    val date: LocalDate,
    val taken: Int,
    val missed: Int
)

internal class AdherenceChartView(
    context: Context,
    palette: RecoMedPalette,
    private val textSize: Float
) : View(context) {
    private var days = emptyList<AdherenceDay>()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.secondaryText
        textSize = textSize * resources.displayMetrics.density * resources.configuration.fontScale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.border
        strokeWidth = resources.displayMetrics.density
    }
    private val takenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.accent
        style = Paint.Style.FILL
    }
    private val missedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.danger
        style = Paint.Style.FILL
    }
    private val radius = 8f * resources.displayMetrics.density

    fun updateDays(days: List<AdherenceDay>) {
        this.days = days
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return

        val density = resources.displayMetrics.density
        val horizontalPadding = 18f * density
        val topPadding = 22f * density
        val labelArea = 38f * density
        val chartHeight = height - topPadding - labelArea
        if (chartHeight <= 0f) return

        val maxValue = days.maxOfOrNull { it.taken + it.missed }?.coerceAtLeast(1) ?: 1
        val chartWidth = width - horizontalPadding * 2
        val slotWidth = chartWidth / days.size
        val barWidth = (slotWidth * 0.48f).coerceAtLeast(4f * density)
        val baseline = topPadding + chartHeight
        val cornerRadius = radius.coerceAtMost(barWidth / 2f)

        for (line in 0..3) {
            val y = topPadding + chartHeight * line / 3f
            canvas.drawLine(horizontalPadding, y, width - horizontalPadding, y, gridPaint)
        }

        days.forEachIndexed { index, day ->
            val x = horizontalPadding + slotWidth * (index + 0.5f)
            val total = day.taken + day.missed
            if (total > 0) {
                val totalHeight = chartHeight * total / maxValue
                val takenHeight = chartHeight * day.taken / maxValue
                val missedHeight = totalHeight - takenHeight
                if (missedHeight > 0f) {
                    canvas.drawRoundRect(
                        x - barWidth / 2f,
                        baseline - totalHeight,
                        x + barWidth / 2f,
                        baseline - takenHeight,
                        cornerRadius,
                        cornerRadius,
                        missedPaint
                    )
                }
                if (takenHeight > 0f) {
                    canvas.drawRoundRect(
                        x - barWidth / 2f,
                        baseline - takenHeight,
                        x + barWidth / 2f,
                        baseline,
                        cornerRadius,
                        cornerRadius,
                        takenPaint
                    )
                }
            }
            val label = if (days.size <= 7) {
                day.date.format(DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("es")))
                    .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }
            } else {
                day.date.dayOfMonth.toString()
            }
            canvas.drawText(label, x, baseline + labelArea * 0.68f, labelPaint)
        }
    }
}
