package tk.zwander.common.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.NestedScrollingParent3
import tk.zwander.common.util.ISnappyLayoutManager
import kotlin.math.absoluteValue

//Based on https://stackoverflow.com/a/26445064/5496177
class SnappyRecyclerView(context: Context, attrs: AttributeSet? = null) :
    ScrollingItemTouchRecyclerView(context, attrs), NestedScrollingParent3 {

    private var latestVX = 0
    private var latestVY = 0

    override fun fling(velocityX: Int, velocityY: Int): Boolean {
        val slop = ViewConfiguration.get(context).scaledPagingTouchSlop

        latestVX = velocityX
        latestVY = velocityY

        val shouldSnap = layoutManager?.canScrollVertically() == true && velocityY.absoluteValue > slop ||
                layoutManager?.canScrollHorizontally() == true && velocityX.absoluteValue > slop

        if (shouldSnap) {
            val layoutManager = layoutManager
            if (layoutManager is ISnappyLayoutManager && layoutManager.canSnap()) {
                smoothScrollToPosition(layoutManager.getPositionForVelocity(velocityX, velocityY))

                latestVX = 0
                latestVY = 0
                return true
            }
        }

        return super.fling(velocityX, velocityY)
    }

    private var dispatchDownX = 0f
    private var dispatchDownY = 0f

    private var dispatchPrevX = 0f
    private var dispatchPrevY = 0f

    private var isVerticalSwipe = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Nothing special if no child scrolling target.
        if (nestedScrollTarget == null || selectedItem) return super.dispatchTouchEvent(ev)

        var handled = false

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                dispatchDownX = ev.rawX
                dispatchDownY = ev.rawY

                dispatchPrevX = dispatchDownX
                dispatchPrevY = dispatchDownY
            }

            MotionEvent.ACTION_MOVE -> {
                val vx = (dispatchPrevX - ev.rawX).absoluteValue
                val vy = (dispatchPrevY - ev.rawY).absoluteValue

                if (isVerticalSwipe || (vy > vx && vy > ViewConfiguration.get(context).scaledTouchSlop)) {
                    isVerticalSwipe = true

                    requestDisallowInterceptTouchEvent(true)
//                    nestedScrollingListener?.invoke(true)
                    handled = super.dispatchTouchEvent(ev)
                }

                dispatchPrevX = ev.rawX
                dispatchPrevY = ev.rawY
            }

            MotionEvent.ACTION_UP -> {
                isVerticalSwipe = false
            }
        }

        requestDisallowInterceptTouchEvent(false)
        if (!handled || nestedScrollTargetWasUnableToScroll) {
            handled = super.dispatchTouchEvent(ev)
        }

        return handled
    }

    private var origX = 0f
    private var origY = 0f

    private var prevX = 0f
    private var prevY = 0f

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val canScroll = when {
            layoutManager?.canScrollHorizontally() == true -> computeHorizontalScrollRange() > width
            layoutManager?.canScrollVertically() == true -> computeVerticalScrollRange() > height
            else -> false
        }
        overScrollMode = if (canScroll) OVER_SCROLL_ALWAYS else OVER_SCROLL_NEVER
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        // We want the parent to handle all touch events--there's a lot going on there,
        // and there is no reason to overwrite that functionality--bad things will happen.
        val ret = super.onTouchEvent(e)
        val lm = layoutManager

        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                origX = e.rawX
                origY = e.rawY

                prevX = origX
                prevY = origY
            }
            MotionEvent.ACTION_MOVE -> {
                val vx = e.rawX - prevX
                val vy = e.rawY - prevY

//                val slopTestX = e.rawX - origX
//                val slopTestY = e.rawY - origY

                latestVX = vx.toInt()
                latestVY = vy.toInt()

                prevX = e.rawX
                prevY = e.rawY
            }
        }

        if (lm is ISnappyLayoutManager
            && (e.action == MotionEvent.ACTION_UP ||
                    e.action == MotionEvent.ACTION_CANCEL)
            && scrollState == SCROLL_STATE_IDLE
            && (latestVX.absoluteValue > touchSlop &&
                    (latestVY.absoluteValue < touchSlop || latestVX.absoluteValue > latestVY.absoluteValue))
        ) {
            // The layout manager is a SnappyLayoutManager, which means that the
            // children should be snapped to a grid at the end of a drag or
            // fling. The motion event is either a user lifting their finger or
            // the cancellation of a motion events, so this is the time to take
            // over the scrolling to perform our own functionality.
            // Finally, the scroll state is idle--meaning that the resultant
            // velocity after the user's gesture was below the threshold, and
            // no fling was performed, so the view may be in an unaligned state
            // and will not be flung to a proper state.
            if (lm.canSnap()) {
                smoothScrollToPosition(lm.getFixScrollPos(latestVX, latestVY))
            }

            latestVX = 0
            latestVY = 0
        }

        return ret
    }
}