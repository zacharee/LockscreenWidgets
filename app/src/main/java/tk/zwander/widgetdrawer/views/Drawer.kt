package tk.zwander.widgetdrawer.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.logUtils
import tk.zwander.widgetdrawer.activities.TaskerIsShowingDrawer

class Drawer : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        TaskerIsShowingDrawer::class.java.requestQuery(context)
        context.eventManager.sendEvent(Event.DrawerAttachmentState(true))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        TaskerIsShowingDrawer::class.java.requestQuery(context)
        context.eventManager.sendEvent(Event.DrawerAttachmentState(false))
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
            context.eventManager.sendEvent(Event.DrawerBackButtonClick)
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        context.logUtils.debugLog("onDraw() Drawer", null)
        super.onDraw(canvas)
    }

    override fun draw(canvas: Canvas) {
        context.logUtils.debugLog("draw() Drawer", null)
        super.draw(canvas)
    }

    override fun drawChild(canvas: Canvas, child: View?, drawingTime: Long): Boolean {
        context.logUtils.debugLog("drawChild() Drawer", null)
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun canHaveDisplayList(): Boolean {
        context.logUtils.debugLog("canHaveDisplayList() ${this::class.java.name}")
        return super.canHaveDisplayList()
    }
}