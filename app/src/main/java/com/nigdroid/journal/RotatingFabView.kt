package com.nigdroid.journal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.min

class RotatingFabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 40
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private var rotation = 0f
    private var glowIntensity = 0f
    private var scale = 1f
    var isOpen = false
        private set

    // Responsive size - base size in DP
    private val baseSizeDp = 56f // Standard FAB size
    private var fabSize = 0f
    private var iconStrokeWidth = 0f
    private var iconSize = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        calculateSizes()
    }

    private fun calculateSizes() {
        // Convert base size to pixels
        val baseSizePx = dpToPx(baseSizeDp)

        // Get screen dimensions
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val minDimension = min(screenWidth, screenHeight)

        // Calculate responsive FAB size (between 56dp and 72dp based on screen size)
        val minSize = dpToPx(56f)
        val maxSize = dpToPx(72f)
        val calculatedSize = minDimension * 0.15f // 15% of smaller dimension
        fabSize = calculatedSize.coerceIn(minSize, maxSize)

        // Icon stroke width scales with FAB size
        iconStrokeWidth = fabSize * 0.04f // 4% of FAB size
        iconPaint.strokeWidth = iconStrokeWidth

        // Icon size is proportional to FAB size
        iconSize = fabSize * 0.2f // 30% of FAB size
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateSizes()
    }

    fun toggle(open: Boolean) {
        isOpen = open

        // Rotation animation
        val targetRotation = if (open) 45f else 0f
        ValueAnimator.ofFloat(rotation, targetRotation).apply {
            duration = 400
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animation ->
                rotation = animation.animatedValue as Float
                invalidate()
            }
        }.start()

        // Glow pulse animation
        ValueAnimator.ofFloat(0f, 1f, 0.3f).apply {
            duration = 600
            addUpdateListener { animation ->
                glowIntensity = animation.animatedValue as Float
                invalidate()
            }
        }.start()

        // Scale animation
        val targetScale = if (open) 1.1f else 1f
        ValueAnimator.ofFloat(scale, targetScale).apply {
            duration = 300
            addUpdateListener { animation ->
                scale = animation.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = fabSize / 2f

        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(scale, scale)

        // Draw glow effect
        if (glowIntensity > 0) {
            val glowRadius = radius * (1 + glowIntensity * 0.5f)
            glowPaint.shader = RadialGradient(
                0f, 0f,
                glowRadius,
                Color.argb((glowIntensity * 100).toInt(), 168, 85, 247),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(0f, 0f, glowRadius, glowPaint)
        }

        // Draw circular background with gradient
        val gradient = LinearGradient(
            -radius, -radius,
            radius, radius,
            intArrayOf(
                Color.parseColor("#A855F7"),
                Color.parseColor("#7C3AED")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        circlePaint.shader = gradient
        canvas.drawCircle(0f, 0f, radius, circlePaint)

        // Draw glass overlay for glassmorphism
        canvas.drawCircle(0f, 0f, radius, glassPaint)

        // Rotate icon
        canvas.rotate(rotation)

        // Draw + icon with responsive size
        canvas.drawLine(-iconSize, 0f, iconSize, 0f, iconPaint)
        canvas.drawLine(0f, -iconSize, 0f, iconSize, iconPaint)

        canvas.restore()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = fabSize.toInt()
        setMeasuredDimension(desiredSize, desiredSize)
    }
}