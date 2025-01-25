package tk.zwander.common.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.Display
import android.view.SurfaceControl
import android.view.View
import android.view.ViewRootImpl.SurfaceChangedCallback
import android.widget.FrameLayout
import kotlinx.atomicfu.atomic
import tk.zwander.common.util.logUtils

class SafeBlurView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {
    private val canDrawOnSurface = atomic(false)

    private val surfaceChangedCallback = object : SurfaceChangedCallback {
        override fun surfaceCreated(t: SurfaceControl.Transaction?) {
            context.logUtils.debugLog("Surface created", null)
            canDrawOnSurface.value = true
        }

        override fun surfaceReplaced(t: SurfaceControl.Transaction?) {
            context.logUtils.debugLog("Surface replaced", null)
            canDrawOnSurface.value = true
        }

        override fun surfaceDestroyed() {
            context.logUtils.debugLog("Surface destroyed", null)
            canDrawOnSurface.value = false
        }
    }

    override fun onAttachedToWindow() {
        context.logUtils.debugLog("Blur view attached", null)

        super.onAttachedToWindow()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewRootImpl.addSurfaceChangedCallback(surfaceChangedCallback)
        }
    }

    override fun onDetachedFromWindow() {
        context.logUtils.debugLog("Blur view detached", null)

        super.onDetachedFromWindow()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            viewRootImpl.removeSurfaceChangedCallback(surfaceChangedCallback)
        }
    }

    override fun setBackground(background: Drawable?) {
        context.logUtils.debugLog("Setting background $background on blur view. Should skip draw? ${shouldSkipDrawing()}", null)

        super.setBackground(background)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (shouldSkipDrawing()) {
            context.logUtils.debugLog("Skipping draw in dispatchDraw().", null)
            return
        }

        context.logUtils.debugLog("Blur dispatchDraw()", null)

        super.dispatchDraw(canvas)
    }

    override fun draw(canvas: Canvas) {
        if (shouldSkipDrawing()) {
            context.logUtils.debugLog("Skipping draw in draw().", null)
            return
        }

        context.logUtils.debugLog("Blur draw()", null)

        super.draw(canvas)
    }

    override fun drawChild(canvas: Canvas, child: View?, drawingTime: Long): Boolean {
        return if (shouldSkipDrawing()) {
            context.logUtils.debugLog("Skipping draw in drawChild().", null)
            false
        } else {
            context.logUtils.debugLog("Blur drawChild()", null)
            super.drawChild(canvas, child, drawingTime)
        }
    }

    private fun shouldSkipDrawing(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.logUtils.debugLog(
                "Checking if can draw blur\n" +
                        "viewRootImpl = ${viewRootImpl}\n" +
                        "displayState = ${display?.state}\n" +
                        "surfaceValid = ${viewRootImpl?.mSurface?.isValid}\n" +
                        "canDrawOnSurface = ${canDrawOnSurface.value}"
            )

            if (
                viewRootImpl == null ||
                display?.state == Display.STATE_OFF ||
                viewRootImpl?.mSurface?.isValid == false ||
                !canDrawOnSurface.value
            ) {
                return true
            }
        }

        return false
    }
}
