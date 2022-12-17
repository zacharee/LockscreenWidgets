package tk.zwander.common.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.common.util.ISnappyLayoutManager
import kotlin.math.absoluteValue

//Based on https://stackoverflow.com/a/26445064/5496177
class SnappyRecyclerView(context: Context, attrs: AttributeSet) : RecyclerView(context, attrs) {
    private var latestVX = 0
    private var latestVY = 0

    override fun fling(velocityX: Int, velocityY: Int): Boolean {
        val slop = ViewConfiguration.get(context).scaledPagingTouchSlop

        latestVX = velocityX
        latestVY = velocityY

        if (velocityX.absoluteValue > slop || velocityY.absoluteValue > slop) {
            val layoutManager = layoutManager
            if (layoutManager is ISnappyLayoutManager && layoutManager.canSnap()) {
                smoothScrollToPosition(layoutManager.getPositionForVelocity(velocityX, velocityY))
                return true
            }
        }

        return super.fling(velocityX, velocityY)
    }

    private var origX = 0f
    private var origY = 0f

    private var prevX = 0f
    private var prevY = 0f

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

                val slopTestX = e.rawX - origX
                val slopTestY = e.rawY - origY

                latestVX = if (slopTestX.absoluteValue > vx.absoluteValue) slopTestX.toInt() else vx.toInt()
                latestVY = if (slopTestY.absoluteValue > vy.absoluteValue) slopTestY.toInt() else vy.toInt()
            }
        }

        if (lm is ISnappyLayoutManager
            && (e.action == MotionEvent.ACTION_UP ||
                    e.action == MotionEvent.ACTION_CANCEL)
            && scrollState == SCROLL_STATE_IDLE
            && (latestVX != 0 || latestVY != 0)
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