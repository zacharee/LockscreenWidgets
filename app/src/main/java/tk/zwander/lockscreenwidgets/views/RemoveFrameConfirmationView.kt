package tk.zwander.lockscreenwidgets.views

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.lockscreenwidgets.databinding.RemoveFrameConfirmationLayoutBinding

class RemoveFrameConfirmationView(context: Context, attrs: AttributeSet) : MaterialCardView(context, attrs) {
    private val binding by lazy { RemoveFrameConfirmationLayoutBinding.bind(this) }

    private var id: Int? = null

    override fun onFinishInflate() {
        super.onFinishInflate()

        binding.confirmDelete.setOnClickListener {
            context.eventManager.sendEvent(Event.RemoveFrameConfirmed(true, id))
            hide()
        }

        binding.cancelDelete.setOnClickListener {
            context.eventManager.sendEvent(Event.RemoveFrameConfirmed(false, id))
            hide()
        }
    }

    fun show(id: Int) {
        isVisible = true
        this.id = id
    }

    fun hide() {
        isVisible = false
        this.id = null
    }
}