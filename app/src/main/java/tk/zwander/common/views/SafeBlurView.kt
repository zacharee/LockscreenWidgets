package tk.zwander.common.views

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.util.AttributeSet
import android.view.Display
import android.view.SurfaceControl
import android.view.ViewRootImpl.SurfaceChangedCallback
import android.widget.FrameLayout
import kotlinx.atomicfu.atomic

class SafeBlurView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {
    private val canDrawOnSurface = atomic(false)

    private val surfaceChangedCallback = object : SurfaceChangedCallback {
        override fun surfaceCreated(t: SurfaceControl.Transaction?) {
            canDrawOnSurface.value = true
        }

        override fun surfaceReplaced(t: SurfaceControl.Transaction?) {
            canDrawOnSurface.value = true
        }

        override fun surfaceDestroyed() {
            canDrawOnSurface.value = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewRootImpl.addSurfaceChangedCallback(surfaceChangedCallback)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewRootImpl.removeSurfaceChangedCallback(surfaceChangedCallback)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (
                viewRootImpl == null ||
                display?.state == Display.STATE_OFF ||
                !viewRootImpl.mSurface.isValid ||
                !canDrawOnSurface.value
            ) {
                return
            }
        }

        super.onDraw(canvas)
    }
}
