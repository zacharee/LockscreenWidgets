package tk.zwander.common.util

import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewRootImpl
import android.view.WindowManager
import tk.zwander.common.drawable.BackgroundBlurDrawableCompatDelegate

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
    private val crossBlurEnabledListener = { _: Boolean ->
        updateBlurDrawable()
    }

    private var blurDrawable: BackgroundBlurDrawableCompatDelegate? = null

    override fun onViewAttachedToWindow(v: View?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowManager.addCrossWindowBlurEnabledListener(crossBlurEnabledListener)
        }
        updateBlurDrawable()
    }

    override fun onViewDetachedFromWindow(v: View?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowManager.removeCrossWindowBlurEnabledListener(crossBlurEnabledListener)
        }
        blurDrawable = null
        updateBlur()
    }

    private fun updateBlurDrawable() {
        blurDrawable = if (windowManager.isCrossWindowBlurEnabledCompat && shouldBlur()) {
            targetView.rootView.viewRootImpl?.createBackgroundBlurDrawableCompat()
        } else {
            null
        }
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
            if (windowManager.isCrossWindowBlurEnabled) {
                val hasBlur = targetView.isHardwareAccelerated && shouldBlur()

                blurDrawable?.setBlurRadius(blurAmount())

                targetView.background = if (hasBlur) blurDrawable?.wrapped else null
            } else {
                targetView.background = null
            }
        } else {
            val f = try {
                params::class.java.getDeclaredField("samsungFlags")
            } catch (e: Exception) {
                return
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

    private fun ViewRootImpl.createBackgroundBlurDrawableCompat(): BackgroundBlurDrawableCompatDelegate {
        return BackgroundBlurDrawableCompatDelegate.createInstance(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                createBackgroundBlurDrawable() as Drawable
            } else {
                null
            }
        )
    }
}