package tk.zwander.lockscreenwidgets.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.logUtils
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