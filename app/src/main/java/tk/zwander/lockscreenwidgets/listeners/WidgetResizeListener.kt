package tk.zwander.lockscreenwidgets.listeners

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * A helper class for listening to drag events on the widget resize handles.
 * Detects a user dragging on the specified resize handle and only triggers
 * an update if the user has dragged far enough.
 */
class WidgetResizeListener(
    private val _thresholdPx: (Which) -> Int,
    private val which: Which,
    private val resizeCallback: (Boolean, Int, Int) -> Unit,
    private val liftCallback: () -> Unit
) : View.OnTouchListener {
    enum class Which {
        LEFT,
        TOP,
        RIGHT,
        BOTTOM
    }

    private var origX = 0f
    private var origY = 0f

    private var prevX = 0f
    private var prevY = 0f

    private var prevXTrack = 0f
    private var prevYTrack = 0f

    //The threshold for a drag event.
    //Either the standard widget width or
    //height depending on the current handle.
    private var thresholdPx: Int = _thresholdPx(which)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                origX = event.rawX
                origY = event.rawY

                prevX = origX
                prevY = origY
                prevXTrack = origX
                prevYTrack = origY

                //The user might change the row/col count
                //after this class has been initialized,
                //so make sure the threshold stays up-to-date.
                //While we could just inline the getter with
                //this variable, it's less resource-intensive
                //to only update it once per gesture.
                thresholdPx = _thresholdPx(which)
            }

            MotionEvent.ACTION_MOVE -> {
                val newX = event.rawX
                val newY = event.rawY

                val distX = newX - prevX
                val distY = newY - prevY

                if (which == Which.LEFT || which == Which.RIGHT) {
                    val overThreshold = distX.absoluteValue > thresholdPx

                    if (overThreshold) {
                        prevX += thresholdPx * distX.sign
                    }

                    resizeCallback(overThreshold, (newX - prevXTrack).sign.toInt(), (newX - prevXTrack).absoluteValue.toInt())

                } else {
                    val overThreshold = distY.absoluteValue > thresholdPx

                    if (overThreshold) {
                        prevY += thresholdPx * distY.sign
                    }

                    resizeCallback(overThreshold, (newY - prevYTrack).sign.toInt(), (newY - prevYTrack).absoluteValue.toInt())
                }

                prevXTrack = newX
                prevYTrack = newY
            }

            MotionEvent.ACTION_UP -> {
                liftCallback()
            }
        }

        return true
    }
}