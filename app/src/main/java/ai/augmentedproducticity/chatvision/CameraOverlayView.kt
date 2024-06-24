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
    }

    fun setPoint(point: Point?) {
        this.point = point?.let { adjustPointToViewSize(it) }
        invalidate()
    }

    private fun adjustPointToViewSize(point: Point): Point {
        val scaleX = width.toFloat() / previewWidth
        val scaleY = height.toFloat() / previewHeight
        return Point((point.x * scaleX).toInt(), (point.y * scaleY).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        point?.let {
            canvas.drawCircle(it.x.toFloat(), it.y.toFloat(), 10f, paint)
        }
    }
}
