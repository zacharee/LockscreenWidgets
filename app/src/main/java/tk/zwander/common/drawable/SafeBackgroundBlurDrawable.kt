package tk.zwander.common.drawable

import android.graphics.Canvas
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import androidx.annotation.RequiresApi
import com.android.internal.graphics.drawable.BackgroundBlurDrawable

@RequiresApi(Build.VERSION_CODES.S)
class SafeBackgroundBlurDrawable(private val wrapped: BackgroundBlurDrawable) : DrawableWrapper(wrapped) {
    override fun draw(canvas: Canvas) {
        if (canvas.isHardwareAccelerated) {
            wrapped.draw(canvas)
        }
    }

    fun setColor(color: Int) {
        wrapped.setColor(color)
    }

    fun setBlurRadius(blurRadius: Int) {
        wrapped.setBlurRadius(blurRadius)
    }

    fun setCornerRadius(cornerRadius: Float) {
        wrapped.setCornerRadius(cornerRadius)
    }

    fun setCornerRadius(
        cornerRadiusTL: Float,
        cornerRadiusTR: Float,
        cornerRadiusBL: Float,
        cornerRadiusBR: Float,
    ) {
        wrapped.setCornerRadius(
            cornerRadiusTL,
            cornerRadiusTR,
            cornerRadiusBL,
            cornerRadiusBR,
        )
    }
}