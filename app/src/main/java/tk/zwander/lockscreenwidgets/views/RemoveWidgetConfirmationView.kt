package tk.zwander.lockscreenwidgets.views

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.remove_widget_confirmation_layout.view.*

/**
 * An overlay on the widget frame that appears when a user taps the "remove"
 * button for a widget. It asks the user whether they really want to remove
 * the widget, and passes the result back to the delegate.
 */
class RemoveWidgetConfirmationView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {
    var onConfirmListener: ((confirmed: Boolean) -> Unit)? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        confirm_delete.setOnClickListener {
            onConfirmListener?.invoke(true)
            onConfirmListener = null
            hide()
        }

        cancel_delete.setOnClickListener {
            onConfirmListener?.invoke(false)
            onConfirmListener = null
            hide()
        }
    }

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}