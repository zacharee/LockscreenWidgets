package tk.zwander.common.views

import android.content.Context
import android.graphics.Canvas
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.databinding.WidgetFrameGestureHintBinding
import tk.zwander.lockscreenwidgets.databinding.WidgetFrameHideHintBinding

/**
 * The View showing the first-time hint over the widget frame, giving the user basic instructions
 * (not that anyone actually reads...)
 */
open class WidgetFrameHintView(context: Context, attrs: AttributeSet) : MaterialCardView(context, attrs) {
    open fun close() {
        isVisible = false
    }

    override fun draw(canvas: Canvas) {
        context.logUtils.debugLog("draw() ${this::class.java.name}", null)
        super.draw(canvas)
    }

    override fun drawChild(canvas: Canvas, child: View?, drawingTime: Long): Boolean {
        context.logUtils.debugLog("drawChild() ${this::class.java.name}", null)
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun dispatchDraw(canvas: Canvas) {
        context.logUtils.debugLog("dispatchDraw() ${this::class.java.name}", null)
        super.dispatchDraw(canvas)
    }

    override fun canHaveDisplayList(): Boolean {
        context.logUtils.debugLog("canHaveDisplayList() ${this::class.java.name}")
        return super.canHaveDisplayList()
    }
}

class WidgetFrameGestureHintView(context: Context, attrs: AttributeSet) : WidgetFrameHintView(context, attrs) {
    var stage2 = false
        set(value) {
            field = value

            binding.gestureHintText.setText(if (value) R.string.edit_gesture_hint_2 else R.string.edit_gesture_hint)
        }

    private val binding by lazy { WidgetFrameGestureHintBinding.bind(this) }

    override fun onFinishInflate() {
        super.onFinishInflate()

        binding.gestureHintText.movementMethod = ScrollingMovementMethod()
    }
}

class WidgetFrameHideHintView(context: Context, attrs: AttributeSet) : WidgetFrameHintView(context, attrs) {
    private val binding by lazy { WidgetFrameHideHintBinding.bind(this) }

    override fun onFinishInflate() {
        super.onFinishInflate()

        binding.hideHintText.movementMethod = ScrollingMovementMethod()
        binding.okForHideHint.setOnClickListener {
            close()
        }
    }

    override fun close() {
        super.close()
        context.prefManager.firstViewing = false
    }
}