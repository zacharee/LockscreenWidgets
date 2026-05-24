package tk.zwander.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import tk.zwander.common.util.ISnappyLayoutManager
import kotlin.math.absoluteValue

//Based on https://stackoverflow.com/a/26445064/5496177
class SnappyRecyclerView(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollingItemTouchRecyclerView(context, attrs) {
    private var latestVX = 0
    private var latestVY = 0

    private var origX = 0f
    private var origY = 0f

    private var prevX = 0f
    private var prevY = 0f

    private var dispatchDownX = 0f
    private var dispatchDownY = 0f

    private var dispatchPrevX = 0f
    private var dispatchPrevY = 0f

    private var isNestedSwipe = false

    override fun fling(velocityX: Int, velocityY: Int): Boolean {
        val slop = ViewConfiguration.get(context).scaledPagingTouchSlop

        latestVX = velocityX
        latestVY = velocityY

        val shouldSnap =
            layoutManager?.canScrollVertically() == true && velocityY.absoluteValue > slop ||
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

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val lm = layoutManager

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                origX = ev.rawX
                origY = ev.rawY

                prevX = origX
                prevY = origY
            }
            MotionEvent.ACTION_MOVE -> {
                val vx = ev.rawX - origX
                val vy = ev.rawY - origY

                latestVX = vx.toInt()
                latestVY = vy.toInt()

                prevX = ev.rawX
                prevY = ev.rawY
            }
        }

        if (lm is ISnappyLayoutManager
            && (ev.action == MotionEvent.ACTION_UP ||
                    ev.action == MotionEvent.ACTION_CANCEL)
            && ((lm.canScrollHorizontally() && latestVX.absoluteValue > touchSlop &&
                    (latestVY.absoluteValue < touchSlop || latestVX.absoluteValue > latestVY.absoluteValue) ||
                    (lm.canScrollVertically() && latestVY.absoluteValue > touchSlop &&
                            (latestVX.absoluteValue < touchSlop || latestVY.absoluteValue > latestVX.absoluteValue))))
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
                val vx = latestVX
                val vy = latestVY

                handler?.post {
                    smoothScrollToPosition(lm.getFixScrollPos(vx, vy))
                }
            }

            latestVX = 0
            latestVY = 0
        }

        // No scrolling if no selected item.
        if (selectedItem) return super.dispatchTouchEvent(ev)

        var handled = false

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                dispatchDownX = ev.rawX
                dispatchDownY = ev.rawY

                dispatchPrevX = dispatchDownX
                dispatchPrevY = dispatchDownY
            }

            MotionEvent.ACTION_MOVE -> {
                val vx = (dispatchDownX - ev.rawX).absoluteValue
                val vy = (dispatchDownY - ev.rawY).absoluteValue

                val overThreshold = when {
                    layoutManager?.canScrollHorizontally() == true -> {
                        vy > touchSlop
                    }
                    layoutManager?.canScrollVertically() == true -> {
                        vx > touchSlop
                    }
                    else -> {
                        false
                    }
                }

                if (isNestedSwipe || overThreshold) {
                    isNestedSwipe = true

                    requestDisallowInterceptTouchEvent(true)
                    nestedScrollingListener?.invoke(true)
                    handled = super.dispatchTouchEvent(ev)
                }

                dispatchPrevX = ev.rawX
                dispatchPrevY = ev.rawY
            }

            MotionEvent.ACTION_UP -> {
                requestDisallowInterceptTouchEvent(false)
                nestedScrollingListener?.invoke(false)
                isNestedSwipe = false
            }
        }

        if (!handled || nestedScrollTargetWasUnableToScroll) {
            requestDisallowInterceptTouchEvent(false)
            handled = super.dispatchTouchEvent(ev)
        }

        return handled
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val canScroll = when {
            layoutManager?.canScrollHorizontally() == true -> computeHorizontalScrollRange() > width
            layoutManager?.canScrollVertically() == true -> computeVerticalScrollRange() > height
            else -> false
        }
        overScrollMode = if (canScroll) OVER_SCROLL_ALWAYS else OVER_SCROLL_NEVER
    }
}