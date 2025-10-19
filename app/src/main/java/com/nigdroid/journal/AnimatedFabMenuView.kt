package com.nigdroid.journal

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.sin

class AnimatedFabMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val menuOptions = listOf(
        MenuOption("Journal", GradientColors(0xFFFBBE94.toInt(), 0xFFD57F57.toInt(), 0xFFF95007.toInt())),
        MenuOption("Todo", GradientColors(0xFFACE6CD.toInt(), 0xFF6ECB9C.toInt(), 0xFF01F86A.toInt())),
        MenuOption("Audio", GradientColors(0xFFF8BCD1.toInt(), 0xFFDB6D8F.toInt(), 0xFFFC1359.toInt())),
        MenuOption("Text", GradientColors(0xFF92B7F0.toInt(), 0xFF4E7EF3.toInt(), 0xFF0034FF.toInt()))
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6302E2E")
        style = Paint.Style.FILL
    }

    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val optionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(15f, 0f, 8f, Color.argb(60, 0, 0, 0))
    }

    private val closePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1F2937")
        style = Paint.Style.FILL
        setShadowLayer(20f, 0f, 10f, Color.argb(80, 0, 0, 0))
    }

    private val closeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(12f, 0f, 6f, Color.argb(50, 0, 0, 0))
    }

    private var isOpen = false
    private var animationProgress = 0f
    private var blurProgress = 0f
    private var closeButtonAlpha = 0f

    private val optionRadius = 80f
    private val closeButtonRadius = 80f
    private val arcRadius = 370f

    private val optionPositions = mutableListOf<PointF>()
    private val optionScales = mutableListOf<Float>()
    private val optionAlphas = mutableListOf<Float>()

    var onOptionSelectedListener: ((Int) -> Unit)? = null
    var onMenuStateChangeListener: ((Boolean) -> Unit)? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        for (i in menuOptions.indices) {
            optionPositions.add(PointF())
            optionScales.add(0f)
            optionAlphas.add(0f)
        }
    }

    fun toggle() {
        if (isOpen) {
            close()
        } else {
            open()
        }
    }

    private fun open() {
        isOpen = true
        visibility = VISIBLE
        bringToFront()
        parent?.requestLayout()
        onMenuStateChangeListener?.invoke(true)

        val animators = mutableListOf<Animator>()

        val bgAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
        }
        animators.add(bgAnimator)

        val blurAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                blurProgress = it.animatedValue as Float
                invalidate()
            }
        }
        animators.add(blurAnimator)

        for (i in menuOptions.indices) {
            val scaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 550
                startDelay = 200L + (i * 70L)
                interpolator = OvershootInterpolator(1.5f)
                addUpdateListener {
                    optionScales[i] = it.animatedValue as Float
                    invalidate()
                }
            }
            animators.add(scaleAnimator)

            val alphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 400
                startDelay = 200L + (i * 70L)
                addUpdateListener {
                    optionAlphas[i] = it.animatedValue as Float
                    invalidate()
                }
            }
            animators.add(alphaAnimator)
        }

        val closeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            startDelay = 550
            addUpdateListener {
                closeButtonAlpha = it.animatedValue as Float
                invalidate()
            }
        }
        animators.add(closeAnimator)

        AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    fun close() {
        val animators = mutableListOf<Animator>()

        onMenuStateChangeListener?.invoke(false)

        for (i in menuOptions.indices) {
            val scaleAnimator = ValueAnimator.ofFloat(optionScales[i], 0f).apply {
                duration = 300
                startDelay = (menuOptions.size - i - 1) * 40L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    optionScales[i] = it.animatedValue as Float
                    invalidate()
                }
            }
            animators.add(scaleAnimator)

            val alphaAnimator = ValueAnimator.ofFloat(optionAlphas[i], 0f).apply {
                duration = 250
                startDelay = (menuOptions.size - i - 1) * 40L
                addUpdateListener {
                    optionAlphas[i] = it.animatedValue as Float
                    invalidate()
                }
            }
            animators.add(alphaAnimator)
        }

        val closeAnimator = ValueAnimator.ofFloat(closeButtonAlpha, 0f).apply {
            duration = 250
            addUpdateListener {
                closeButtonAlpha = it.animatedValue as Float
                invalidate()
            }
        }
        animators.add(closeAnimator)

        val blurAnimator = ValueAnimator.ofFloat(blurProgress, 0f).apply {
            duration = 350
            startDelay = 100
            addUpdateListener {
                blurProgress = it.animatedValue as Float
                invalidate()
            }
        }
        animators.add(blurAnimator)

        val bgAnimator = ValueAnimator.ofFloat(animationProgress, 0f).apply {
            duration = 350
            startDelay = 200
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isOpen = false
                    visibility = GONE
                }
            })
        }
        animators.add(bgAnimator)

        AnimatorSet().apply {
            playTogether(animators)
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isOpen && animationProgress == 0f) return

        val fabCenterX = width / 2f
        val fabCenterY = height - 280f

        if (blurProgress > 0) {
            val gradientShader = LinearGradient(
                0f, height.toFloat(),
                0f, 0f,
                intArrayOf(
                    Color.argb((blurProgress * 230).toInt(), 0, 0, 0),
                    Color.argb((blurProgress * 180).toInt(), 0, 0, 0),
                    Color.argb((blurProgress * 100).toInt(), 0, 0, 0)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            blurPaint.shader = gradientShader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), blurPaint)
        }

        backgroundPaint.alpha = (animationProgress * 150).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val startAngle = 180f
        val sweepAngle = 180f
        val angleStep = sweepAngle / (menuOptions.size + 1)

        for (i in menuOptions.indices) {
            if (optionScales[i] > 0) {
                val angle = Math.toRadians((startAngle + angleStep * (i + 1)).toDouble())
                val distance = arcRadius * blurProgress
                val x = fabCenterX + (cos(angle) * distance).toFloat()
                val y = fabCenterY + (sin(angle) * distance).toFloat()

                optionPositions[i].set(x, y)

                val currentScale = optionScales[i]
                val currentAlpha = optionAlphas[i]

                // Create diagonal gradient (135 degrees)
                val cardSize = optionRadius * currentScale
                val colors = menuOptions[i].gradientColors
                val gradient = LinearGradient(
                    x - cardSize, y - cardSize,  // Top-left
                    x + cardSize, y + cardSize,  // Bottom-right (135° diagonal)
                    intArrayOf(colors.startColor, colors.centerColor, colors.endColor),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                cardPaint.shader = gradient
                cardPaint.alpha = (currentAlpha * 255).toInt()

                val rect = RectF(
                    x - cardSize, y - cardSize,
                    x + cardSize, y + cardSize
                )
                canvas.drawRoundRect(rect, 65f, 65f, cardPaint)

                // Glass overlay for depth
                optionPaint.color = Color.WHITE
                optionPaint.alpha = (currentAlpha * 30).toInt()
                canvas.drawRoundRect(rect, 65f, 65f, optionPaint)

                drawIcon(canvas, i, x, y, currentScale * currentAlpha)
            }
        }

        if (closeButtonAlpha > 0) {
            closePaint.alpha = (closeButtonAlpha * 255).toInt()
            canvas.drawCircle(fabCenterX, fabCenterY, closeButtonRadius, closePaint)

            optionPaint.color = Color.WHITE
            optionPaint.alpha = (closeButtonAlpha * 20).toInt()
            canvas.drawCircle(fabCenterX, fabCenterY, closeButtonRadius, optionPaint)

            closeStrokePaint.alpha = (closeButtonAlpha * 80).toInt()
            canvas.drawCircle(fabCenterX, fabCenterY, closeButtonRadius - 1.5f, closeStrokePaint)

            iconPaint.alpha = (closeButtonAlpha * 255).toInt()
            iconPaint.strokeWidth = 7f
            val crossSize = 18f
            canvas.drawLine(
                fabCenterX - crossSize, fabCenterY - crossSize,
                fabCenterX + crossSize, fabCenterY + crossSize,
                iconPaint
            )
            canvas.drawLine(
                fabCenterX + crossSize, fabCenterY - crossSize,
                fabCenterX - crossSize, fabCenterY + crossSize,
                iconPaint
            )
            iconPaint.strokeWidth = 6f
        }
    }

    private fun drawIcon(canvas: Canvas, index: Int, x: Float, y: Float, scale: Float) {
        iconPaint.alpha = (scale * 255).toInt()
        val size = 45f * scale

        when (index) {
            0 -> { // Journal - Book icon with spine
                // Main rectangle
                val rect = RectF(x - size * 0.83f, y - size * 0.75f, x + size * 0.83f, y + size * 0.75f)
                canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, iconPaint)
                // Vertical spine line
                canvas.drawLine(x - size * 0.5f, y - size * 0.75f, x - size * 0.5f, y + size * 0.75f, iconPaint)
                // Horizontal lines
                canvas.drawLine(x, y - size * 0.33f, x + size * 0.5f, y - size * 0.33f, iconPaint)
                canvas.drawLine(x, y, x + size * 0.5f, y, iconPaint)
                canvas.drawLine(x, y + size * 0.33f, x + size * 0.33f, y + size * 0.33f, iconPaint)
            }
            1 -> { // Todo - List with bullets
                // Three horizontal lines
                canvas.drawLine(x - size * 0.125f, y - size * 0.5f, x + size * 0.83f, y - size * 0.5f, iconPaint)
                canvas.drawLine(x - size * 0.125f, y, x + size * 0.83f, y, iconPaint)
                canvas.drawLine(x - size * 0.125f, y + size * 0.5f, x + size * 0.83f, y + size * 0.5f, iconPaint)

                // Save paint settings
                val originalStyle = iconPaint.style
                iconPaint.style = Paint.Style.FILL

                // Three bullet dots
                canvas.drawCircle(x - size * 0.58f, y - size * 0.5f, size * 0.125f, iconPaint)
                canvas.drawCircle(x - size * 0.58f, y, size * 0.125f, iconPaint)
                canvas.drawCircle(x - size * 0.58f, y + size * 0.5f, size * 0.125f, iconPaint)

                // Restore paint
                iconPaint.style = originalStyle
            }
            2 -> { // Audio - Microphone (filled style)
                val originalStyle = iconPaint.style
                iconPaint.style = Paint.Style.FILL

                // Microphone capsule
                val micRect = RectF(x - size * 0.25f, y - size * 0.58f, x + size * 0.25f, y + size * 0.08f)
                canvas.drawRoundRect(micRect, size * 0.25f, size * 0.25f, iconPaint)

                // Stand base and neck
                val standPath = Path().apply {
                    // Arc around microphone
                    val arcRect = RectF(x - size * 0.45f, y - size * 0.33f, x + size * 0.45f, y + size * 0.42f)
                    arcTo(arcRect, 0f, 180f, false)
                    // Vertical line down
                    lineTo(x, y + size * 0.73f)
                    lineTo(x, y + size * 0.73f)
                }
                iconPaint.style = Paint.Style.STROKE
                canvas.drawPath(standPath, iconPaint)

                // Bottom horizontal line
                canvas.drawLine(x - size * 0.17f, y + size * 0.73f, x + size * 0.17f, y + size * 0.73f, iconPaint)

                iconPaint.style = originalStyle
            }
            3 -> { // Text - Note/Document icon
                // Main rectangle
                val rect = RectF(x - size * 0.67f, y - size * 0.67f, x + size * 0.67f, y + size * 0.67f)
                canvas.drawRoundRect(rect, size * 0.17f, size * 0.17f, iconPaint)
                // Two horizontal lines
                canvas.drawLine(x - size * 0.42f, y - size * 0.17f, x + size * 0.42f, y - size * 0.17f, iconPaint)
                canvas.drawLine(x - size * 0.42f, y + size * 0.17f, x + size * 0.25f, y + size * 0.17f, iconPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && isOpen) {
            val x = event.x
            val y = event.y

            val fabCenterX = width / 2f
            val fabCenterY = height - 280f

            val distToClose = distance(x, y, fabCenterX, fabCenterY)
            if (distToClose < closeButtonRadius * 1.3f) {
                close()
                return true
            }

            for (i in optionPositions.indices) {
                val pos = optionPositions[i]
                val dist = distance(x, y, pos.x, pos.y)
                if (dist < optionRadius * optionScales[i] * 1.2f) {
                    onOptionSelectedListener?.invoke(i)
                    close()
                    return true
                }
            }

            close()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    data class GradientColors(
        val startColor: Int,
        val centerColor: Int,
        val endColor: Int
    )

    data class MenuOption(val name: String, val gradientColors: GradientColors)
}