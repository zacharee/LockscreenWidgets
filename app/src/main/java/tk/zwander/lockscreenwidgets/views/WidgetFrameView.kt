package tk.zwander.lockscreenwidgets.views

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.widget_frame.view.*

class WidgetFrameView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {
    var onMoveListener: ((velX: Float, velY: Float) -> Unit)? = null
    var onResizeListener: ((velX: Float, velY: Float) -> Unit)? = null
    var onInterceptListener: ((down: Boolean) -> Unit)? = null

    private var maxPointerCount = 0
    private var isInEditingMode = false
    private var alreadyIndicatedMoving = false

    private val vibrator by lazy { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    @SuppressLint("ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()

        move.setOnTouchListener(MoveTouchListener())
        expand.setOnTouchListener(ExpandTouchListener())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        setEditMode(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        onTouch(ev, null)

        maxPointerCount = ev.pointerCount

        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return (maxPointerCount > 1).also {
            if (it) {
                setEditMode(!isInEditingMode)
                maxPointerCount = 0
            }
        }
    }

    private fun onTouch(event: MotionEvent, sup: ((event: MotionEvent) -> Boolean)? = null): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onInterceptListener?.invoke(true)
                false
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                onInterceptListener?.invoke(false)
                alreadyIndicatedMoving = false
                false
            }
            else -> sup?.invoke(event) ?: false
        }
    }

    private fun setEditMode(editing: Boolean) {
        isInEditingMode = editing

        move.isVisible = editing
        expand.isVisible = editing
        edit_outline.isVisible = editing
    }

    private fun vibrate() {
        val time = 50L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(time, 100))
        } else {
            vibrator.vibrate(time)
        }
    }

    inner class ExpandTouchListener : OnTouchListener {
        private var prevExpandX = 0f
        private var prevExpandY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    prevExpandX = event.rawX
                    prevExpandY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!alreadyIndicatedMoving) {
                        alreadyIndicatedMoving = true

                        vibrate()
                    }
                    val newX = event.rawX
                    val newY = event.rawY

                    val velX = newX - prevExpandX
                    val velY = newY - prevExpandY

                    prevExpandX = newX
                    prevExpandY = newY

                    onResizeListener?.invoke(velX, velY)
                    onResizeListener != null
                }
                else -> false
            }
        }
    }

    inner class MoveTouchListener : OnTouchListener {
        private var prevX = 0f
        private var prevY = 0f

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    prevX = event.rawX
                    prevY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!alreadyIndicatedMoving) {
                        alreadyIndicatedMoving = true

                        vibrate()
                    }
                    val newX = event.rawX
                    val newY = event.rawY

                    val velX = newX - prevX
                    val velY = newY - prevY

                    prevX = newX
                    prevY = newY

                    onMoveListener?.invoke(velX, velY)
                    onMoveListener != null
                }
                else -> false
            }
        }
    }
}