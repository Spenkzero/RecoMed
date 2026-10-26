package uy.recomed.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
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
    private var firstVisibleIndex = 0
    private var touchStartX = 0f
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
        firstVisibleIndex = (days.size - VISIBLE_DAYS).coerceAtLeast(0)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                return true
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - touchStartX
                if (kotlin.math.abs(deltaX) > 48f * resources.displayMetrics.density) {
                    val maxFirstIndex = (days.size - VISIBLE_DAYS).coerceAtLeast(0)
                    firstVisibleIndex = if (deltaX < 0) {
                        (firstVisibleIndex + VISIBLE_DAYS).coerceAtMost(maxFirstIndex)
                    } else {
                        (firstVisibleIndex - VISIBLE_DAYS).coerceAtLeast(0)
                    }
                    invalidate()
                }
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return
        val visibleDays = days.drop(firstVisibleIndex).take(VISIBLE_DAYS)
        if (visibleDays.isEmpty()) return

        val density = resources.displayMetrics.density
        val horizontalPadding = 18f * density
        val topPadding = 22f * density
        val labelArea = 38f * density
        val chartHeight = height - topPadding - labelArea
        if (chartHeight <= 0f) return

        val maxValue = visibleDays.maxOfOrNull { it.taken + it.missed }?.coerceAtLeast(1) ?: 1
        val chartWidth = width - horizontalPadding * 2
        val slotWidth = chartWidth / visibleDays.size
        val barWidth = (slotWidth * 0.48f).coerceAtLeast(4f * density)
        val baseline = topPadding + chartHeight
        val cornerRadius = radius.coerceAtMost(barWidth / 2f)

        for (line in 0..3) {
            val y = topPadding + chartHeight * line / 3f
            canvas.drawLine(horizontalPadding, y, width - horizontalPadding, y, gridPaint)
        }

        visibleDays.forEachIndexed { index, day ->
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
            val label = if (days.size <= VISIBLE_DAYS) {
                day.date.format(DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("es")))
                    .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }
            } else {
                "${day.date.dayOfMonth}/${day.date.monthValue}"
            }
            canvas.drawText(label, x, baseline + labelArea * 0.68f, labelPaint)
        }
    }

    companion object {
        private const val VISIBLE_DAYS = 7
    }
}
