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
        color = Color.parseColor("#D64E7E")
        style = Paint.Style.FILL
    }

    private val firstCurveStartPoint = Point()
    private val firstCurveEndPoint = Point()
    private val firstCurveControlPoint1 = Point()
    private val firstCurveControlPoint2 = Point()

    private val secondCurveStartPoint = Point()
    private val secondCurveEndPoint = Point()
    private val secondCurveControlPoint1 = Point()
    private val secondCurveControlPoint2 = Point()

    private var navigationBarWidth = 0
    private var navigationBarHeight = 0
    private val cornerRadius = 80f // Increased corner radius

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        navigationBarWidth = width
        navigationBarHeight = height

        // Calculate curve points
        val curveStartX = (width * 0.25f).toInt() // Start after home icon
        val curveEndX = (width * 0.75f).toInt() // End before chatbot icon
        val centerX = width / 2
        val curveDepth = 145 // Increased depth for deeper curve

        // First curve (left side going up)
        firstCurveStartPoint.set(curveStartX, 0)
        firstCurveEndPoint.set(centerX, curveDepth)
        firstCurveControlPoint1.set(curveStartX + 50, 5) // Slight dip
        firstCurveControlPoint2.set(centerX - 100, curveDepth)

        // Second curve (right side going down)
        secondCurveStartPoint.set(centerX, curveDepth)
        secondCurveEndPoint.set(curveEndX, 0)
        secondCurveControlPoint1.set(centerX + 100, curveDepth)
        secondCurveControlPoint2.set(curveEndX - 50, 5) // Slight dip
    }

    override fun onDraw(canvas: Canvas) {
        path.reset()

        // Start from bottom left corner (inside the curve)
        path.moveTo(cornerRadius, navigationBarHeight.toFloat())

        // Bottom-left corner curve
        path.quadTo(0f, navigationBarHeight.toFloat(), 0f, (navigationBarHeight - cornerRadius))

        // Left side straight line
        path.lineTo(0f, cornerRadius)

        // Top-left corner
        path.quadTo(0f, 0f, cornerRadius, 0f)

        // Straight line to curve start
        path.lineTo(firstCurveStartPoint.x.toFloat(), firstCurveStartPoint.y.toFloat())

        // First elliptical curve (going up and inward) - DEEPER
        path.cubicTo(
            firstCurveControlPoint1.x.toFloat(), firstCurveControlPoint1.y.toFloat(),
            firstCurveControlPoint2.x.toFloat(), firstCurveControlPoint2.y.toFloat(),
            firstCurveEndPoint.x.toFloat(), firstCurveEndPoint.y.toFloat()
        )

        // Second elliptical curve (going down and outward) - DEEPER
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
        path.quadTo(navigationBarWidth.toFloat(), navigationBarHeight.toFloat(), (navigationBarWidth - cornerRadius), navigationBarHeight.toFloat())

        // Bottom line back to start
        path.lineTo(cornerRadius, navigationBarHeight.toFloat())

        // Close path
        path.close()

        canvas.drawPath(path, paint)

        super.onDraw(canvas)
    }
}