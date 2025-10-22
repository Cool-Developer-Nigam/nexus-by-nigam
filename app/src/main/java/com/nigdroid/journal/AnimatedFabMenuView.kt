package com.nigdroid.journal

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.min
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

    // Use dp to px conversion for consistent sizing
    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        )
    }

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

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1.4f) // 30% smaller stroke width
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

    // Fixed sizes in DP for consistency
    private val optionRadius = dpToPx(28f) // Fixed 28dp radius (30% smaller than 40dp)
    private var arcRadius = 0f
    private var fabCenterY = 0f

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // Calculate responsive values with proper constraints
        val screenWidth = w.toFloat()
        val screenHeight = h.toFloat()

        // Use minimum dimension to ensure consistent spacing on all devices
        val minDimension = min(screenWidth, screenHeight)

        // Arc radius: 30% of smaller screen dimension, capped between 126dp and 168dp (30% smaller)
        val minArcRadius = dpToPx(126f) // 30% smaller than 180dp
        val maxArcRadius = dpToPx(168f) // 30% smaller than 240dp
        val calculatedArcRadius = minDimension * 0.21f // 30% smaller (0.30 * 0.7 = 0.21)
        arcRadius = calculatedArcRadius.coerceIn(minArcRadius, maxArcRadius)

        // FAB center Y: Fixed distance from bottom in DP
        fabCenterY = screenHeight - dpToPx(80f)
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

                // Use fixed option radius (no scaling with screen)
                val cardSize = optionRadius * currentScale
                val cornerRadius = dpToPx(22f) // Fixed corner radius (30% smaller)

                val colors = menuOptions[i].gradientColors
                val gradient = LinearGradient(
                    x - cardSize, y - cardSize,
                    x + cardSize, y + cardSize,
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
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardPaint)

                // Glass overlay for depth
                optionPaint.color = Color.WHITE
                optionPaint.alpha = (currentAlpha * 30).toInt()
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, optionPaint)

                drawIcon(canvas, i, x, y, currentScale * currentAlpha)
            }
        }
    }

    private fun drawIcon(canvas: Canvas, index: Int, x: Float, y: Float, scale: Float) {
        iconPaint.alpha = (scale * 255).toInt()
        val size = dpToPx(15.4f) * scale // Fixed icon size in DP (30% smaller)

        when (index) {
            0 -> { // Journal - Book icon with spine
                val rect = RectF(x - size * 0.83f, y - size * 0.75f, x + size * 0.83f, y + size * 0.75f)
                canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, iconPaint)
                canvas.drawLine(x - size * 0.5f, y - size * 0.75f, x - size * 0.5f, y + size * 0.75f, iconPaint)
                canvas.drawLine(x, y - size * 0.33f, x + size * 0.5f, y - size * 0.33f, iconPaint)
                canvas.drawLine(x, y, x + size * 0.5f, y, iconPaint)
                canvas.drawLine(x, y + size * 0.33f, x + size * 0.33f, y + size * 0.33f, iconPaint)
            }
            1 -> { // Todo - List with bullets
                canvas.drawLine(x - size * 0.125f, y - size * 0.5f, x + size * 0.83f, y - size * 0.5f, iconPaint)
                canvas.drawLine(x - size * 0.125f, y, x + size * 0.83f, y, iconPaint)
                canvas.drawLine(x - size * 0.125f, y + size * 0.5f, x + size * 0.83f, y + size * 0.5f, iconPaint)

                val originalStyle = iconPaint.style
                iconPaint.style = Paint.Style.FILL

                canvas.drawCircle(x - size * 0.58f, y - size * 0.5f, size * 0.125f, iconPaint)
                canvas.drawCircle(x - size * 0.58f, y, size * 0.125f, iconPaint)
                canvas.drawCircle(x - size * 0.58f, y + size * 0.5f, size * 0.125f, iconPaint)

                iconPaint.style = originalStyle
            }
            2 -> { // Audio - Microphone
                val originalStyle = iconPaint.style
                iconPaint.style = Paint.Style.FILL

                val micRect = RectF(x - size * 0.25f, y - size * 0.58f, x + size * 0.25f, y + size * 0.08f)
                canvas.drawRoundRect(micRect, size * 0.25f, size * 0.25f, iconPaint)

                val standPath = Path().apply {
                    val arcRect = RectF(x - size * 0.45f, y - size * 0.33f, x + size * 0.45f, y + size * 0.42f)
                    arcTo(arcRect, 0f, 180f, false)
                    lineTo(x, y + size * 0.73f)
                }
                iconPaint.style = Paint.Style.STROKE
                canvas.drawPath(standPath, iconPaint)
                canvas.drawLine(x - size * 0.17f, y + size * 0.73f, x + size * 0.17f, y + size * 0.73f, iconPaint)

                iconPaint.style = originalStyle
            }
            3 -> { // Text - Document icon
                val rect = RectF(x - size * 0.67f, y - size * 0.67f, x + size * 0.67f, y + size * 0.67f)
                canvas.drawRoundRect(rect, size * 0.17f, size * 0.17f, iconPaint)
                canvas.drawLine(x - size * 0.42f, y - size * 0.17f, x + size * 0.42f, y - size * 0.17f, iconPaint)
                canvas.drawLine(x - size * 0.42f, y + size * 0.17f, x + size * 0.25f, y + size * 0.17f, iconPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && isOpen) {
            val x = event.x
            val y = event.y

            // Check if clicked on any menu option
            for (i in optionPositions.indices) {
                val pos = optionPositions[i]
                val dist = distance(x, y, pos.x, pos.y)
                if (dist < optionRadius * optionScales[i] * 1.2f) {
                    onOptionSelectedListener?.invoke(i)
                    close()
                    return true
                }
            }

            // Clicked on background/shadow - close menu
            close()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Make the view fill the entire screen
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
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