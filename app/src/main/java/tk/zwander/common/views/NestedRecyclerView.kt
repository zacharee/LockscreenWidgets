package tk.zwander.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

//https://stackoverflow.com/a/68318211/5496177
open class NestedRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollingItemTouchRecyclerView(context, attrs, defStyleAttr) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Nothing special if no child scrolling target.
        if (nestedScrollTarget == null || selectedItem) return super.dispatchTouchEvent(ev)

        // Inhibit the execution of our onInterceptTouchEvent for now...
        requestDisallowInterceptTouchEvent(true)
        // ... but do all other processing.
        var handled = super.dispatchTouchEvent(ev)

        // If the first dispatch yielded an unhandled event or the descendant view is unable to
        // scroll in the direction the user is scrolling, we dispatch once more but without skipping
        // our onInterceptTouchEvent. Note that RecyclerView automatically cancels active touches of
        // all its descendants once it starts scrolling so we don't have to do that.
        requestDisallowInterceptTouchEvent(false)
        if (!handled || nestedScrollTargetWasUnableToScroll) {
            handled = super.dispatchTouchEvent(ev)
        }

        return handled
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
    ) {
        if (target === nestedScrollTarget && dyUnconsumed != 0) {
            // The descendant could not fully consume the scroll. We remember that in order
            // to allow the RecyclerView to take over scrolling.
            nestedScrollTargetWasUnableToScroll = true
            // Let the parent start to consume scroll events.
            target.parent?.requestDisallowInterceptTouchEvent(false)
        }
    }
}