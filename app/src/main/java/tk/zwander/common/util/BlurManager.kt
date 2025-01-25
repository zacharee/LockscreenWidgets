package tk.zwander.common.util

import android.content.Context
import android.os.Build
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.WindowManager
import tk.zwander.common.drawable.BackgroundBlurDrawableCompat

class BlurManager(
    private val windowManager: WindowManager,
    private val context: Context,
    private val params: WindowManager.LayoutParams,
    private val targetView: View,
    private val listenKeys: List<String>,
    private val shouldBlur: () -> Boolean,
    private val blurAmount: () -> Int,
    private val updateWindow: () -> Unit,
    private val cornerRadius: () -> Float = { 0f },
) : OnAttachStateChangeListener {
    private val handlerRegistry = HandlerRegistry {
        handler(listenKeys) {
            updateBlur()
        }
    }
    private val crossBlurEnabledListener = { _: Boolean ->
        updateBlur()
    }

    private var blurDrawable: BackgroundBlurDrawableCompat? = null

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
        blurDrawable = null
    }

    fun onCreate() {
        targetView.addOnAttachStateChangeListener(this)
        handlerRegistry.register(context)
    }

    fun onDestroy() {
        targetView.removeOnAttachStateChangeListener(this)
        handlerRegistry.unregister(context)
    }

    fun updateBlur(fromParamsUpdate: Boolean = false) {
        val blurAmount = blurAmount()
        val shouldBlur = shouldBlur()
        val cornerRadius = cornerRadius()

        context.logUtils.debugLog("Updating blur for $targetView. Should blur $shouldBlur, amount $blurAmount, radius $cornerRadius.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!fromParamsUpdate) {
                if (blurAmount > 0 &&
                    shouldBlur &&
                    targetView.isAttachedToWindow &&
                    windowManager.isCrossWindowBlurEnabled &&
                    targetView.rootView.viewRootImpl.isHardwareEnabled &&
                    targetView.alpha > 0
                ) {
                    if (blurDrawable == null) {
                        context.logUtils.debugLog("Creating BackgroundBlurDrawableCompat.", null)
                        blurDrawable = BackgroundBlurDrawableCompat(targetView.rootView.viewRootImpl)
                    }
                } else {
                    blurDrawable = null
                }

                blurDrawable?.setBlurRadius(blurAmount)
                blurDrawable?.setCornerRadius(cornerRadius)

                context.logUtils.debugLog("Setting blur drawable $blurDrawable on target view with current background ${targetView.background}.", null)
                targetView.background = blurDrawable
            }
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

            updateWindow()
        }
    }
}