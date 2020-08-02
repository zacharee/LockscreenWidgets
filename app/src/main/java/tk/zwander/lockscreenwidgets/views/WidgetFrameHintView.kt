package tk.zwander.lockscreenwidgets.views

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import kotlinx.android.synthetic.main.widget_frame_gesture_hint.view.*
import kotlinx.android.synthetic.main.widget_frame_hide_hint.view.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.prefManager

/**
 * The View showing the first-time hint over the widget frame, giving the user basic instructions
 * (not that anyone actually reads...)
 */
open class WidgetFrameHintView(context: Context, attrs: AttributeSet) : MaterialCardView(context, attrs) {
    open fun close() {
        isVisible = false
    }
}

class WidgetFrameGestureHintView(context: Context, attrs: AttributeSet) : WidgetFrameHintView(context, attrs) {
    var stage2 = false
        set(value) {
            field = value

            gesture_hint_text.setText(if (value) R.string.edit_gesture_hint_2 else R.string.edit_gesture_hint)
        }

    override fun onFinishInflate() {
        super.onFinishInflate()

        gesture_hint_text.movementMethod = ScrollingMovementMethod()
    }
}

class WidgetFrameHideHintView(context: Context, attrs: AttributeSet) : WidgetFrameHintView(context, attrs) {
    override fun onFinishInflate() {
        super.onFinishInflate()

        hide_hint_text.movementMethod = ScrollingMovementMethod()
    }

    override fun close() {
        super.close()
        context.prefManager.firstViewing = false
    }
}