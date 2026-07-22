package tk.zwander.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.absoluteValue

//https://stackoverflow.com/a/68318211/5496177
open class NestedRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ScrollingItemTouchRecyclerView(context = context, attrs = attrs, defStyleAttr = defStyleAttr) {
    private var dispatchDownX = 0f
    private var dispatchDownY = 0f

    private var dispatchPrevX = 0f
    private var dispatchPrevY = 0f

    private var isNestedSwipe = false

    init {
        tag = TAG
        clipToPadding = false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // No scrolling if no selected item.
        if (selectedItem) {
            return super.dispatchTouchEvent(ev)
        }

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
                    // Assuming cross-axis "nested" scrolling here for the widget frame.
                    // This could probably be cleaned up but it works.
                    layoutManager?.canScrollHorizontally() == true -> {
                        vx > touchSlop
                    }
                    layoutManager?.canScrollVertically() == true -> {
                        vy > touchSlop
                    }
                    else -> {
                        false
                    }
                }

                if ((isNestedSwipe || overThreshold) && !nestedScrollTargetWasUnableToScroll) {
                    isNestedSwipe = true

                    requestDisallowInterceptTouchEvent(true)
                    nestedScrollingListener?.invoke(true)
                    handled = super.dispatchTouchEvent(ev)
                }

                dispatchPrevX = ev.rawX
                dispatchPrevY = ev.rawY
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                requestDisallowInterceptTouchEvent(false)
                nestedScrollTarget = null
                nestedScrollingListener?.invoke(false)
                isNestedSwipe = false
                nestedScrollTargetWasUnableToScroll = false
                handled = false
            }
        }

        if (!handled || nestedScrollTargetWasUnableToScroll) {
            requestDisallowInterceptTouchEvent(false)
            handled = super.dispatchTouchEvent(ev)
        }

        return handled
    }

    companion object {
        const val TAG = "nestedRecyclerView"
    }
}
