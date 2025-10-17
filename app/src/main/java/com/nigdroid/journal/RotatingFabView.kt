package com.nigdroid.journal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat

class RotatingFabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }

    private var rotation = 0f
    private var glowIntensity = 0f
    private var scale = 1f
    var isOpen = false
        private set

    private val size = 200f // 84dp * 2.38 for diamond

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
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

        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(45f) // Base diamond rotation
        canvas.scale(scale, scale)

        // Draw glow effect
        if (glowIntensity > 0) {
            val glowRadius = (size / 2) * (1 + glowIntensity * 0.5f)
            glowPaint.shader = RadialGradient(
                0f, 0f,
                glowRadius,
                Color.argb((glowIntensity * 100).toInt(), 168, 85, 247),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(0f, 0f, glowRadius, glowPaint)
        }

        // Draw diamond background with gradient
        val gradient = LinearGradient(
            -size / 2, -size / 2,
            size / 2, size / 2,
            intArrayOf(
                Color.parseColor("#A855F7"),
                Color.parseColor("#7C3AED")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        diamondPaint.shader = gradient

        val path = Path().apply {
            moveTo(0f, -size / 2)
            lineTo(size / 2, 0f)
            lineTo(0f, size / 2)
            lineTo(-size / 2, 0f)
            close()
        }
        canvas.drawPath(path, diamondPaint)

        // Draw glass overlay for glassmorphism
        canvas.drawPath(path, glassPaint)

        // Rotate icon
        canvas.rotate(rotation - 45f)

        // Draw + icon
        val iconSize = 30f
        canvas.drawLine(-iconSize, 0f, iconSize, 0f, iconPaint)
        canvas.drawLine(0f, -iconSize, 0f, iconSize, iconPaint)

        canvas.restore()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredSize = size.toInt()
        setMeasuredDimension(desiredSize, desiredSize)
    }
}