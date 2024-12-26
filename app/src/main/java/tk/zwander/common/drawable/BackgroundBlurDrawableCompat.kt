package tk.zwander.common.drawable

import android.annotation.ColorInt
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ViewRootImpl
import androidx.annotation.RequiresApi

sealed class BackgroundBlurDrawableCompatDelegate(open val drawable: Drawable) {
    abstract fun setColor(@ColorInt color: Int)
    abstract fun setBlurRadius(blurRadius: Int)
    abstract fun setCornerRadius(cornerRadius: Float)
    abstract fun setCornerRadius(
        cornerRadiusTL: Float,
        cornerRadiusTR: Float,
        cornerRadiusBL: Float,
        cornerRadiusBR: Float,
    )

    @RequiresApi(Build.VERSION_CODES.S)
    class BackgroundBlurDrawableCompatApi31(override val drawable: SafeBackgroundBlurDrawable) : BackgroundBlurDrawableCompatDelegate(drawable) {
        override fun setColor(color: Int) {
            drawable.setColor(color)
        }

        override fun setBlurRadius(blurRadius: Int) {
            drawable.setBlurRadius(blurRadius)
        }

        override fun setCornerRadius(cornerRadius: Float) {
            drawable.setCornerRadius(cornerRadius)
        }

        override fun setCornerRadius(
            cornerRadiusTL: Float,
            cornerRadiusTR: Float,
            cornerRadiusBL: Float,
            cornerRadiusBR: Float,
        ) {
            drawable.setCornerRadius(
                cornerRadiusTL,
                cornerRadiusTR,
                cornerRadiusBL,
                cornerRadiusBR,
            )
        }
    }

    companion object {
        operator fun invoke(viewRootImpl: ViewRootImpl): BackgroundBlurDrawableCompatDelegate? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BackgroundBlurDrawableCompatApi31(SafeBackgroundBlurDrawable(viewRootImpl.createBackgroundBlurDrawable()))
            } else {
                null
            }
        }
    }
}