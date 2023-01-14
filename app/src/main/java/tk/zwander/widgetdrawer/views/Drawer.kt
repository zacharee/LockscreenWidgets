package tk.zwander.widgetdrawer.views

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager

class Drawer : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        context.eventManager.sendEvent(Event.DrawerAttachmentState(true))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        context.eventManager.sendEvent(Event.DrawerAttachmentState(false))
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
            context.eventManager.sendEvent(Event.DrawerBackButtonClick)
            return true
        }

        return super.dispatchKeyEvent(event)
    }
}