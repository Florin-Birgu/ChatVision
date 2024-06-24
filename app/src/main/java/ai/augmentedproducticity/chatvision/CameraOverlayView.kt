package ai.augmentedproducticity.chatvision

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class CameraOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var point: Point? = null
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0
    private val paint = Paint().apply {
        color = ContextCompat.getColor(context, android.R.color.holo_red_light)
        style = Paint.Style.FILL
    }

    fun setPreviewSize(width: Int, height: Int) {
        previewWidth = width
        previewHeight = height
        invalidate()
    }

    fun setPoint(point: Point?) {
        this.point = point
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        point?.let {
            val adjustedPoint = adjustPointToViewSize(it)
            canvas.drawCircle(adjustedPoint.x.toFloat(), adjustedPoint.y.toFloat(), 20f, paint)
        }
    }

    private fun adjustPointToViewSize(point: Point): Point {
        val scaleX = width.toFloat() / previewWidth
        val scaleY = height.toFloat() / previewHeight
        val scale = minOf(scaleX, scaleY)

        val newX = (point.x * scale + (width - previewWidth * scale) / 2f).toInt()
        val newY = (point.y * scale + (height - previewHeight * scale) / 2f).toInt()

        return Point(newX, newY)
    }
}