package tk.zwander.widgetdrawer.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.components.DrawerToolbar
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.logUtils
import tk.zwander.lockscreenwidgets.databinding.DrawerLayoutBinding
import tk.zwander.widgetdrawer.activities.TaskerIsShowingDrawer

class Drawer : FrameLayout {
    private val binding by lazy { DrawerLayoutBinding.bind(this) }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding.toolbarView.setContent {
            AppTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    DrawerToolbar(
                        addWidget = {
                            context.eventManager.sendEvent(Event.CloseDrawer)
                            context.eventManager.sendEvent(Event.LaunchAddDrawerWidget(true))
                        },
                        closeDrawer = {
                            context.eventManager.sendEvent(Event.CloseDrawer)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

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