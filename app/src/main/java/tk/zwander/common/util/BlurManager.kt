package tk.zwander.common.util

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.WindowManager
import tk.zwander.common.drawable.BackgroundBlurDrawableCompatDelegate

class BlurManager(
    context: Context,
    private val params: WindowManager.LayoutParams,
    private val targetView: View,
    private val listenKeys: Array<String>,
    private val shouldBlur: () -> Boolean,
    private val blurAmount: () -> Int,
    private val cornerRadius: (() -> Float)? = null,
    private val updateWindow: () -> Unit,
) : ContextWrapper(context), OnAttachStateChangeListener {
    private val handlerRegistry = HandlerRegistry {
        handler(*listenKeys) {
            updateBlur()
        }
    }
    private val crossBlurEnabledListener = { _: Boolean ->
        updateBlur()
    }

    private var blurWrapper: BackgroundBlurDrawableCompatDelegate? = null

    override fun onViewAttachedToWindow(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowManager.addCrossWindowBlurEnabledListener(crossBlurEnabledListener)
        }
        updateBlur()
    }

    override fun onViewDetachedFromWindow(v: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowManager.removeCrossWindowBlurEnabledListener(crossBlurEnabledListener)
        }
        targetView.background = null
        blurWrapper = null
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
        val blurAmount = blurAmount()
        val shouldBlur = shouldBlur()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (blurAmount > 0
                && shouldBlur
                && targetView.isAttachedToWindow
                && windowManager.isCrossWindowBlurEnabled
                && targetView.rootView.viewRootImpl.isHardwareEnabled
            ) {
                if (blurWrapper == null) {
                    blurWrapper = BackgroundBlurDrawableCompatDelegate(targetView.rootView.viewRootImpl)
                }
            } else {
                blurWrapper = null
            }

            blurWrapper?.setBlurRadius(blurAmount)
            cornerRadius?.invoke()?.let { cr ->
                blurWrapper?.setCornerRadius(cr)
            }

            targetView.background = blurWrapper?.drawable
        } else {
            val f = try {
                params::class.java.getDeclaredField("samsungFlags")
            } catch (e: Exception) {
                return
            }

            if (shouldBlur) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND

                f?.set(params, f.get(params) as Int or 64)
                params.dimAmount = blurAmount / 1000f
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()

                f?.set(params, f.get(params) as Int and 64.inv())
                params.dimAmount = 0.0f
            }
        }

        updateWindow()
    }
}