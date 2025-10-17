package com.nigdroid.journal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.google.android.material.bottomnavigation.BottomNavigationView

class AnimatedBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr) {

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D5B7E")
        style = Paint.Style.FILL
    }

    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FFFFFF")
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#20FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var navigationBarWidth = 0
    private var navigationBarHeight = 0
    private val cornerRadius = 80f

    // Curve points
    private val firstCurveStartPoint = Point()
    private val firstCurveEndPoint = Point()
    private val firstCurveControlPoint1 = Point()
    private val firstCurveControlPoint2 = Point()

    private val secondCurveStartPoint = Point()
    private val secondCurveEndPoint = Point()
    private val secondCurveControlPoint1 = Point()
    private val secondCurveControlPoint2 = Point()

    // Animation properties
    private var pulseRadius = 0f
    private var pulseAlpha = 0
    var isMenuOpen = false
        private set

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        elevation = 0f
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        // Adjust icon positions to be closer to center
        if (childCount > 0) {
            val menuView = getChildAt(0) as? ViewGroup
            menuView?.let {
                val totalWidth = width
                val centerX = totalWidth / 2
                val itemCount = it.childCount

                // Reduce spacing between items to bring them closer to center
                val itemSpacing = totalWidth * 0.27f // Reduced from default spacing
                val totalItemsWidth = itemSpacing * (itemCount - 1)
                val startX = centerX - totalItemsWidth / 2

                for (i in 0 until itemCount) {
                    val child = it.getChildAt(i)
                    val itemX = (startX + i * itemSpacing).toInt()
                    val itemWidth = child.width

                    child.layout(
                        itemX - itemWidth / 2,
                        child.top,
                        itemX + itemWidth / 2,
                        child.bottom
                    )
                }
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        navigationBarWidth = width
        navigationBarHeight = height

        // Calculate curve points
        val curveStartX = (width * 0.33f).toInt()
        val curveEndX = (width * 0.67f).toInt()
        val centerX = width / 2
        val curveDepth = 70

        // First curve (left side going up)
        firstCurveStartPoint.set(curveStartX, 0)
        firstCurveEndPoint.set(centerX, curveDepth)
        firstCurveControlPoint1.set(curveStartX + 40, 5)
        firstCurveControlPoint2.set(centerX - 80, curveDepth)

        // Second curve (right side going down)
        secondCurveStartPoint.set(centerX, curveDepth)
        secondCurveEndPoint.set(curveEndX, 0)
        secondCurveControlPoint1.set(centerX + 80, curveDepth)
        secondCurveControlPoint2.set(curveEndX - 40, 5)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
            (56 * resources.displayMetrics.density).toInt(),
            MeasureSpec.EXACTLY
        ))
    }

    fun animateFabPulse() {
        val pulseAnimator = ValueAnimator.ofFloat(0f, 80f, 0f).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                pulseRadius = animation.animatedValue as Float
                pulseAlpha = ((1f - animation.animatedFraction) * 100).toInt()
                invalidate()
            }
        }
        pulseAnimator.start()
    }

    fun setMenuOpenState(open: Boolean, animate: Boolean = true) {
        isMenuOpen = open
        if (animate) {
            animateFabPulse()
        }
    }

    override fun onDraw(canvas: Canvas) {
        path.reset()

        val centerX = navigationBarWidth / 2f

        // Draw pulse effect at the center of the curve
        if (pulseRadius > 0) {
            glowPaint.shader = RadialGradient(
                centerX,
                firstCurveEndPoint.y.toFloat(),
                pulseRadius,
                Color.argb(pulseAlpha, 168, 85, 247),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(centerX, firstCurveEndPoint.y.toFloat(), pulseRadius, glowPaint)
        }

        // Start from bottom left corner
        path.moveTo(cornerRadius, navigationBarHeight.toFloat())

        // Bottom-left corner curve
        path.quadTo(0f, navigationBarHeight.toFloat(), 0f, (navigationBarHeight - cornerRadius))

        // Left side straight line
        path.lineTo(0f, cornerRadius)

        // Top-left corner
        path.quadTo(0f, 0f, cornerRadius, 0f)

        // Straight line to curve start
        path.lineTo(firstCurveStartPoint.x.toFloat(), firstCurveStartPoint.y.toFloat())

        // First elliptical curve (going up and inward)
        path.cubicTo(
            firstCurveControlPoint1.x.toFloat(), firstCurveControlPoint1.y.toFloat(),
            firstCurveControlPoint2.x.toFloat(), firstCurveControlPoint2.y.toFloat(),
            firstCurveEndPoint.x.toFloat(), firstCurveEndPoint.y.toFloat()
        )

        // Second elliptical curve (going down and outward)
        path.cubicTo(
            secondCurveControlPoint1.x.toFloat(), secondCurveControlPoint1.y.toFloat(),
            secondCurveControlPoint2.x.toFloat(), secondCurveControlPoint2.y.toFloat(),
            secondCurveEndPoint.x.toFloat(), secondCurveEndPoint.y.toFloat()
        )

        // Straight line to right corner
        path.lineTo((navigationBarWidth - cornerRadius), 0f)

        // Top-right corner
        path.quadTo(navigationBarWidth.toFloat(), 0f, navigationBarWidth.toFloat(), cornerRadius)

        // Right side straight line
        path.lineTo(navigationBarWidth.toFloat(), (navigationBarHeight - cornerRadius))

        // Bottom-right corner curve
        path.quadTo(
            navigationBarWidth.toFloat(), navigationBarHeight.toFloat(),
            (navigationBarWidth - cornerRadius), navigationBarHeight.toFloat()
        )

        // Bottom line back to start
        path.lineTo(cornerRadius, navigationBarHeight.toFloat())

        // Close path
        path.close()

        // Draw glassmorphism background with curve
        canvas.drawPath(path, paint)
        canvas.drawPath(path, glassPaint)
        canvas.drawPath(path, strokePaint)

        super.onDraw(canvas)
    }
}