package tk.zwander.widgetdrawer.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import tk.zwander.lockscreenwidgets.util.Event
import tk.zwander.lockscreenwidgets.util.eventManager
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.views.NestedRecyclerView

class DrawerRecycler : NestedRecyclerView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            return if (context.prefManager.closeOnEmptyTap) {
                context.eventManager.sendEvent(Event.CloseDrawer)
                true
            } else false
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent?): Boolean {
        return super.onTouchEvent(e) or gestureDetector.onTouchEvent(e)
    }
}