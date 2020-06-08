package tk.zwander.lockscreenwidgets.listeners

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import tk.zwander.lockscreenwidgets.util.dpAsPx
import tk.zwander.lockscreenwidgets.util.widgetBlockHeight
import tk.zwander.lockscreenwidgets.util.widgetBlockWidth
import kotlin.math.absoluteValue
import kotlin.math.sign

class WidgetResizeListener(private val context: Context, private val which: Which, private val callback: (Int) -> Unit) : View.OnTouchListener {
    enum class Which {
        LEFT,
        TOP,
        RIGHT,
        BOTTOM
    }

    var origX = 0f
    var origY = 0f

    var prevX = 0f
    var prevY = 0f

    private val _thresholdPx: Int
        get() = context.run { if (which == Which.LEFT || which == Which.RIGHT) widgetBlockWidth else widgetBlockHeight }

    var thresholdPx: Int = _thresholdPx

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                origX = event.rawX
                origY = event.rawY

                prevX = origX
                prevY = origY

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
                        callback(distX.sign.toInt())
                    }
                } else {
                    if (distY.absoluteValue > thresholdPx) {
                        prevY += thresholdPx * distY.sign
                        callback(distY.sign.toInt())
                    }
                }
            }
        }

        return true
    }
}