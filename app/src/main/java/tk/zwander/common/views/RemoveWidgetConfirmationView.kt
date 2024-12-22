package tk.zwander.common.views

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import tk.zwander.common.data.WidgetData
import tk.zwander.lockscreenwidgets.databinding.RemoveWidgetConfirmationLayoutBinding
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager

/**
 * An overlay on the widget frame that appears when a user taps the "remove"
 * button for a widget. It asks the user whether they really want to remove
 * the widget, and passes the result back to the delegate.
 */
class RemoveWidgetConfirmationView(context: Context, attrs: AttributeSet) : MaterialCardView(context, attrs) {
    private val binding by lazy { RemoveWidgetConfirmationLayoutBinding.bind(this) }

    private var item: WidgetData? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        binding.confirmDelete.setOnClickListener {
            context.eventManager.sendEvent(Event.RemoveWidgetConfirmed(true, item))
            hide()
        }

        binding.cancelDelete.setOnClickListener {
            context.eventManager.sendEvent(Event.RemoveWidgetConfirmed(false, item))
            hide()
        }
    }

    fun show(item: WidgetData) {
        isVisible = true
        this.item = item
    }

    fun hide() {
        isVisible = false
        this.item = null
    }
}