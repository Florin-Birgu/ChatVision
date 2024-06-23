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
    private val paint = Paint().apply {
        color = ContextCompat.getColor(context, android.R.color.holo_red_light)
        style = Paint.Style.FILL
    }

    fun setPoint(point: Point?) {
        this.point = point
        invalidate() // Redraw the view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        point?.let {
            canvas?.drawCircle(it.x.toFloat(), it.y.toFloat(), 10f, paint)
        }
    }
}
