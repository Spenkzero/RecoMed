package uy.recomed.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
    private var scrollIndex = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartScrollIndex = 0f
    private var isDragging = false
    private var isScrubbing = false
    private var velocityTracker: VelocityTracker? = null
    private var scrollAnimator: ValueAnimator? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var onVisibleRangeChanged: ((String) -> Unit)? = null
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
    private val guideTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.border
        style = Paint.Style.FILL
    }
    private val guideThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.accent
        style = Paint.Style.FILL
    }
    private val radius = 8f * resources.displayMetrics.density

    fun updateDays(days: List<AdherenceDay>) {
        scrollAnimator?.cancel()
        this.days = days
        scrollIndex = maxScrollIndex().toFloat()
        notifyVisibleRange()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrollAnimator?.cancel()
                touchStartX = event.x
                touchStartY = event.y
                touchStartScrollIndex = scrollIndex
                isDragging = false
                isScrubbing = days.size > WEEK_DAYS &&
                    event.y >= height - GUIDE_TOUCH_HEIGHT_DP * resources.displayMetrics.density
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                if (isScrubbing) updateScrollFromGuide(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val deltaX = event.x - touchStartX
                if (isScrubbing) {
                    updateScrollFromGuide(event.x)
                    return true
                }
                if (!isDragging && abs(deltaX) > touchSlop &&
                    abs(deltaX) > abs(event.y - touchStartY)) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (isDragging) {
                    val dayWidth = chartWidth() / visibleDaysCount().coerceAtLeast(1)
                    scrollIndex = (touchStartScrollIndex - deltaX / dayWidth)
                        .coerceIn(0f, maxScrollIndex().toFloat())
                    notifyVisibleRange()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (isDragging || isScrubbing) {
                    val dayWidth = chartWidth() / visibleDaysCount().coerceAtLeast(1)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityDays = -(velocityTracker?.xVelocity ?: 0f) /
                        dayWidth * FLING_DURATION_SECONDS
                    val targetIndex = (scrollIndex + velocityDays)
                        .roundToInt().coerceIn(0, maxScrollIndex())
                    animateToIndex(targetIndex)
                } else {
                    performClick()
                }
                recycleVelocityTracker()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging || isScrubbing) animateToIndex(scrollIndex.roundToInt())
                recycleVelocityTracker()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun visibleDaysCount() =
        if (days.size <= WEEK_DAYS) days.size else PAGE_DAYS

    private fun maxScrollIndex() = (days.size - visibleDaysCount()).coerceAtLeast(0)

    private fun chartWidth(): Float {
        val horizontalPadding = HORIZONTAL_PADDING_DP * resources.displayMetrics.density
        return (width - horizontalPadding * 2).coerceAtLeast(1f)
    }

    private fun updateScrollFromGuide(x: Float) {
        val density = resources.displayMetrics.density
        val horizontalPadding = HORIZONTAL_PADDING_DP * density
        val trackWidth = chartWidth()
        val thumbWidth = (trackWidth * visibleDaysCount() / days.size)
            .coerceAtLeast(GUIDE_THUMB_MIN_DP * density)
        val travel = (trackWidth - thumbWidth).coerceAtLeast(0f)
        val fraction = ((x - horizontalPadding - thumbWidth / 2f) / travel.coerceAtLeast(1f))
            .coerceIn(0f, 1f)
        scrollIndex = fraction * maxScrollIndex()
        notifyVisibleRange()
        invalidate()
    }

    private fun animateToIndex(targetIndex: Int) {
        scrollAnimator?.cancel()
        val target = targetIndex.coerceIn(0, maxScrollIndex()).toFloat()
        if (target == scrollIndex) return
        scrollAnimator = ValueAnimator.ofFloat(scrollIndex, target).apply {
            duration = (180L + 35L * abs(target - scrollIndex).toInt()).coerceAtMost(360L)
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                scrollIndex = animator.animatedValue as Float
                notifyVisibleRange()
                invalidate()
            }
            start()
        }
    }

    private fun recycleVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun notifyVisibleRange() {
        if (days.isEmpty()) return
        val firstVisibleIndex = scrollIndex.roundToInt().coerceIn(0, maxScrollIndex())
        val visibleDays = days.drop(firstVisibleIndex).take(visibleDaysCount())
        val first = visibleDays.firstOrNull()?.date ?: return
        val last = visibleDays.last().date
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
        val range = if (first == last) {
            first.format(formatter)
        } else {
            "${first.format(formatter)} – ${last.format(formatter)}"
        }
        onVisibleRangeChanged?.invoke("Mostrando $range")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (days.isEmpty()) return

        val density = resources.displayMetrics.density
        val horizontalPadding = HORIZONTAL_PADDING_DP * density
        val topPadding = 22f * density
        val labelArea = 38f * density
        val chartHeight = height - topPadding - labelArea
        if (chartHeight <= 0f) return

        val pageSize = visibleDaysCount()
        val maxValue = days.maxOfOrNull { it.taken + it.missed }?.coerceAtLeast(1) ?: 1
        val chartWidth = chartWidth()
        val slotWidth = chartWidth / pageSize
        val barWidth = (slotWidth * 0.48f).coerceAtLeast(4f * density)
        val baseline = topPadding + chartHeight
        val cornerRadius = radius.coerceAtMost(barWidth / 2f)
        val labelBaseline = baseline + labelArea * 0.58f

        for (line in 0..3) {
            val y = topPadding + chartHeight * line / 3f
            canvas.drawLine(horizontalPadding, y, width - horizontalPadding, y, gridPaint)
        }

        canvas.save()
        canvas.clipRect(
            horizontalPadding,
            topPadding,
            width - horizontalPadding,
            baseline + labelArea
        )
        val firstDayIndex = scrollIndex.toInt()
        val lastDayIndex = (scrollIndex + pageSize).toInt().coerceAtMost(days.lastIndex)
        (firstDayIndex..lastDayIndex).forEach { index ->
            val day = days[index]
            val x = horizontalPadding + slotWidth * (index - scrollIndex + 0.5f)
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
            val label = if (days.size <= WEEK_DAYS) {
                day.date.format(DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("es")))
                    .replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }
            } else {
                day.date.dayOfMonth.toString()
            }
            canvas.drawText(label, x, labelBaseline, labelPaint)
        }
        canvas.restore()

        if (days.size > WEEK_DAYS) {
            val guideY = height - 7f * density
            val guideHeight = 4f * density
            val thumbWidth = (chartWidth * pageSize / days.size)
                .coerceAtLeast(GUIDE_THUMB_MIN_DP * density)
            val thumbTravel = (chartWidth - thumbWidth).coerceAtLeast(0f)
            val progress = if (maxScrollIndex() == 0) 0f else scrollIndex / maxScrollIndex()
            canvas.drawRoundRect(
                horizontalPadding,
                guideY,
                width - horizontalPadding,
                guideY + guideHeight,
                guideHeight / 2f,
                guideHeight / 2f,
                guideTrackPaint
            )
            val thumbLeft = horizontalPadding + thumbTravel * progress
            canvas.drawRoundRect(
                thumbLeft,
                guideY - density,
                thumbLeft + thumbWidth,
                guideY + guideHeight + density,
                guideHeight,
                guideHeight,
                guideThumbPaint
            )
        }
    }

    override fun onDetachedFromWindow() {
        scrollAnimator?.cancel()
        recycleVelocityTracker()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val WEEK_DAYS = 7
        private const val PAGE_DAYS = 5
        private const val HORIZONTAL_PADDING_DP = 18f
        private const val GUIDE_THUMB_MIN_DP = 28f
        private const val GUIDE_TOUCH_HEIGHT_DP = 26f
        private const val FLING_DURATION_SECONDS = 0.12f
    }
}
