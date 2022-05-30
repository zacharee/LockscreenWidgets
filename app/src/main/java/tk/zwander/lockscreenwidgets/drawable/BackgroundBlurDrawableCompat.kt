package tk.zwander.lockscreenwidgets.drawable

import android.annotation.ColorInt
import android.graphics.drawable.Drawable
import android.os.Build
import com.android.internal.graphics.drawable.BackgroundBlurDrawable

class BackgroundBlurDrawableCompat12(override val wrapped: BackgroundBlurDrawable?) : BackgroundBlurDrawableCompatDelegate(wrapped) {
    override fun setColor(color: Int) {
        wrapped?.setColor(color)
    }

    override fun setBlurRadius(blurRadius: Int) {
        wrapped?.setBlurRadius(blurRadius)
    }

    override fun setCornerRadius(cornerRadius: Float) {
        wrapped?.setCornerRadius(cornerRadius)
    }

    override fun setCornerRadius(
        cornerRadiusTL: Float,
        cornerRadiusTR: Float,
        cornerRadiusBL: Float,
        cornerRadiusBR: Float
    ) {
        wrapped?.setCornerRadius(cornerRadiusTL, cornerRadiusTR, cornerRadiusBL, cornerRadiusBR)
    }
}

class BackgroundBlurDrawableCompatPre12(wrapped: Drawable?) : BackgroundBlurDrawableCompatDelegate(wrapped)

sealed class BackgroundBlurDrawableCompatDelegate(open val wrapped: Drawable?) {
    companion object {
        fun getInstance(wrapped: Drawable?): BackgroundBlurDrawableCompatDelegate {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BackgroundBlurDrawableCompat12(wrapped as? BackgroundBlurDrawable)
            } else {
                BackgroundBlurDrawableCompatPre12(wrapped)
            }
        }
    }

    open fun setColor(@ColorInt color: Int) {}

    open fun setBlurRadius(blurRadius: Int) {}

    open fun setCornerRadius(cornerRadius: Float) {}

    open fun setCornerRadius(
        cornerRadiusTL: Float,
        cornerRadiusTR: Float,
        cornerRadiusBL: Float,
        cornerRadiusBR: Float
    ) {}
}