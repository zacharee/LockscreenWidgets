package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.WindowManager
import androidx.core.view.isVisible
import tk.zwander.lockscreenwidgets.drawable.BackgroundBlurDrawableCompatDelegate

class BlurManager(
    context: Context,
    private val params: WindowManager.LayoutParams,
    private val targetView: View,
    private val listenKeys: Array<String>,
    private val shouldBlur: () -> Boolean,
    private val blurAmount: () -> Int,
    private val updateWindow: () -> Unit,
) : ContextWrapper(context), OnAttachStateChangeListener {
    private val handlerRegistry = HandlerRegistry {
        handler(*listenKeys) {
            updateBlur()
        }
    }

    private var blurDrawable: BackgroundBlurDrawableCompatDelegate? = null

    override fun onViewAttachedToWindow(v: View?) {
        blurDrawable = targetView.viewRootImpl.createBackgroundBlurDrawableCompat()
        updateBlur()
    }

    override fun onViewDetachedFromWindow(v: View?) {
        blurDrawable = null
        updateBlur()
    }

    fun onCreate() {
        targetView.addOnAttachStateChangeListener(this)
        handlerRegistry.register(this)
    }

    fun onDestroy() {
        targetView.removeOnAttachStateChangeListener(this)
        handlerRegistry.unregister(this)
    }

    fun updateBlur() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasBlur = targetView.isHardwareAccelerated

            blurDrawable?.setBlurRadius(blurAmount())

            targetView.background = if (hasBlur) blurDrawable?.wrapped else null
            targetView.isVisible = hasBlur && shouldBlur()
        } else {
            val f = try {
                params::class.java.getDeclaredField("samsungFlags")
            } catch (e: Exception) {
                null
            }

            if (shouldBlur()) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND

                f?.set(params, f.get(params) as Int or 64)
                params.dimAmount = blurAmount() / 1000f
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()

                f?.set(params, f.get(params) as Int and 64.inv())
                params.dimAmount = 0.0f
            }
        }

        updateWindow()
    }

    fun setCornerRadius(radius: Float) {
        blurDrawable?.setCornerRadius(radius)
    }
}