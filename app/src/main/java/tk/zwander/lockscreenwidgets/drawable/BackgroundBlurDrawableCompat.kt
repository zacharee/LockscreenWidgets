package tk.zwander.lockscreenwidgets.drawable

import android.annotation.ColorInt
import android.annotation.TargetApi
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import org.xmlpull.v1.XmlPullParser

class BackgroundBlurDrawableCompatDelegate(val wrapped: Drawable?) {
    private val castWrapped: BackgroundBlurDrawable?
        @RequiresApi(Build.VERSION_CODES.S)
        get() = wrapped as? BackgroundBlurDrawable

    fun setColor(@ColorInt color: Int) {
        safeInvoke {
            castWrapped?.setColor(color)
        }
    }

    fun setBlurRadius(blurRadius: Int) {
        safeInvoke {
            castWrapped?.setBlurRadius(blurRadius)
        }
    }

    fun setCornerRadius(cornerRadius: Float) {
        safeInvoke {
            castWrapped?.setCornerRadius(cornerRadius)
        }
    }

    fun setCornerRadius(
        cornerRadiusTL: Float,
        cornerRadiusTR: Float,
        cornerRadiusBL: Float,
        cornerRadiusBR: Float
    ) {
        safeInvoke {
            castWrapped?.setCornerRadius(
                cornerRadiusTL, cornerRadiusTR, cornerRadiusBL, cornerRadiusBR
            )
        }
    }

    private fun safeInvoke(block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            block()
        }
    }
}