package com.nigdroid.journal

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.google.android.material.bottomnavigation.BottomNavigationView

class CurvedBottomNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr) {

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#000000") // Darker semi-transparent black (70% opacity)
        style = Paint.Style.FILL
    }

    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60FFFFFF") // Stronger white overlay for more blur effect
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL) // Increased blur radius
    }

    private var navigationBarWidth = 0
    private var navigationBarHeight = 0
    private val cornerRadius = 80f // Reduced for smaller nav bar
    private val fabMargin = 10 // Space between FAB and curve
    private val widthReductionFactor = 0.85f // Reduce width to 85% of screen

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        navigationBarWidth = width
        navigationBarHeight = height
    }

    override fun onDraw(canvas: Canvas) {
        path.reset()

        val centerX = navigationBarWidth / 2f
        val fabRadius = 42f // Half of FAB size (84dp / 2)
        val curveRadius = fabRadius + fabMargin

        // Calculate curve points
        val curveStartX = centerX - curveRadius - 60
        val curveEndX = centerX + curveRadius + 60

        // Start from top-left corner
        path.moveTo(0f, cornerRadius)

        // Top-left rounded corner
        path.quadTo(0f, 0f, cornerRadius, 0f)

        // Top line to left curve start
        path.lineTo(curveStartX, 0f)

        // Left curve going up
        path.quadTo(
            centerX - curveRadius - 20, 0f,
            centerX - curveRadius, -curveRadius + 15
        )

        // Arc around the FAB (semi-circle cutout)
        path.arcTo(
            RectF(
                centerX - curveRadius,
                -curveRadius - 5,
                centerX + curveRadius,
                curveRadius + 25
            ),
            180f,
            180f,
            false
        )

        // Right curve going down
        path.quadTo(
            centerX + curveRadius + 20, 0f,
            curveEndX, 0f
        )

        // Top line to top-right corner
        path.lineTo(navigationBarWidth - cornerRadius, 0f)

        // Top-right rounded corner
        path.quadTo(
            navigationBarWidth.toFloat(), 0f,
            navigationBarWidth.toFloat(), cornerRadius
        )

        // Right side
        path.lineTo(navigationBarWidth.toFloat(), navigationBarHeight - cornerRadius)

        // Bottom-right rounded corner
        path.quadTo(
            navigationBarWidth.toFloat(), navigationBarHeight.toFloat(),
            navigationBarWidth - cornerRadius, navigationBarHeight.toFloat()
        )

        // Bottom line
        path.lineTo(cornerRadius, navigationBarHeight.toFloat())

        // Bottom-left rounded corner
        path.quadTo(
            0f, navigationBarHeight.toFloat(),
            0f, navigationBarHeight - cornerRadius
        )

        // Close path
        path.close()

        // Draw blur effect first
        canvas.drawPath(path, blurPaint)
        // Draw main background on top
        canvas.drawPath(path, paint)

        super.onDraw(canvas)
    }
}