package tk.zwander.lockscreenwidgets.listeners

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import tk.zwander.lockscreenwidgets.util.widgetBlockHeight
import tk.zwander.lockscreenwidgets.util.widgetBlockWidth
import kotlin.math.absoluteValue
import kotlin.math.sign

/**
 * A helper class for listening to drag events on the widget resize handles.
 * Detects a user dragging on the specified resize handle and only triggers
 * an update if the user has dragged far enough.
 */
class WidgetResizeListener(
    private val context: Context,
    private val which: Which,
    private val resizeCallback: (Int) -> Unit,
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

    //The threshold for a drag event.
    //Either the standard widget width or
    //height depending on the current handle.
    private val _thresholdPx: Int
        get() = context.run { if (which == Which.LEFT || which == Which.RIGHT) widgetBlockWidth else widgetBlockHeight }

    private var thresholdPx: Int = _thresholdPx

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                origX = event.rawX
                origY = event.rawY

                prevX = origX
                prevY = origY

                //The user might change the row/col count
                //after this class has been initialized,
                //so make sure the threshold stays up-to-date.
                //While we could just inline the getter with
                //this variable, it's less resource-intensive
                //to only update it once per gesture.
                thresholdPx = _thresholdPx
            }

            MotionEvent.ACTION_MOVE -> {
                val newX = event.rawX
                val newY = event.rawY

                val distX = newX - prevX
                val distY = newY - prevY

                if (which == Which.LEFT || which == Which.RIGHT) {
                    if (distX.absoluteValue > thresholdPx) {
                        prevX += thresholdPx * distX.sign

                        resizeCallback(distX.sign.toInt())
                    }
                } else {
                    if (distY.absoluteValue > thresholdPx) {
                        prevY += thresholdPx * distY.sign
                        resizeCallback(distY.sign.toInt())
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                liftCallback()
            }
        }

        return true
    }
}