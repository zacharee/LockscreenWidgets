package tk.zwander.widgetdrawer.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.*
import kotlin.math.absoluteValue

class Handle : LinearLayout {
    companion object {
        private const val MSG_LONG_PRESS = 0

        private const val LONG_PRESS_DELAY = 300
        private const val SWIPE_THRESHOLD = 50
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var onOpenListener: (() -> Unit)? = null

    private var inMoveMode = false
    private var calledOpen = false
    private var screenWidth = -1

    private val gestureManager = GestureManager()
    private val wm =
        context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val handleLeft = AppCompatResources.getDrawable(context, R.drawable.handle_left)
    private val handleRight = AppCompatResources.getDrawable(context, R.drawable.handle_right)

    private val longClickHandler = @SuppressLint("HandlerLeak")
    object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
                MSG_LONG_PRESS -> gestureManager.onLongPress()
            }
        }
    }

    val params = WindowManager.LayoutParams().apply {
        type =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_PRIORITY_PHONE
            else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        width = context.dpAsPx(context.prefManager.drawerHandleWidth)
        height = context.dpAsPx(context.prefManager.drawerHandleHeight)
        gravity = Gravity.TOP or context.prefManager.drawerHandleSide
        y = context.prefManager.drawerHandleYPosition
        format = PixelFormat.RGBA_8888
    }

    private val prefsHandler = HandlerRegistry {
        handler(PrefManager.KEY_DRAWER_HANDLE_HEIGHT) {
            params.height = context.dpAsPx(context.prefManager.drawerHandleHeight)
            updateLayout()
        }

        handler(PrefManager.KEY_DRAWER_HANDLE_WIDTH) {
            params.width = context.dpAsPx(context.prefManager.drawerHandleWidth)
            updateLayout()
        }

        handler(PrefManager.KEY_DRAWER_HANDLE_COLOR) {
            setTint(context.prefManager.drawerHandleColor)
        }

        handler(PrefManager.KEY_SHOW_DRAWER_HANDLE_SHADOW) {
            elevation = if (context.prefManager.drawerHandleShadow) context.dpAsPx(8).toFloat() else 0f
        }
    }

    init {
        setSide()
        setTint(context.prefManager.drawerHandleColor)
        isClickable = true
        elevation = if (context.prefManager.drawerHandleShadow) context.dpAsPx(8).toFloat() else 0f
        contentDescription = resources.getString(R.string.open_widget_drawer)
        prefsHandler.register(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                screenWidth = context.screenSize.x
                longClickHandler.sendEmptyMessageAtTime(
                    MSG_LONG_PRESS,
                    event.downTime + LONG_PRESS_DELAY
                )
            }
            MotionEvent.ACTION_UP -> {
                longClickHandler.removeMessages(MSG_LONG_PRESS)
                setMoveMove(false)
                context.prefManager.drawerHandleYPosition = params.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (inMoveMode) {
                    val gravity = when {
                        event.rawX <= 1 / 3f * screenWidth -> {
                            Gravity.LEFT
                        }
                        event.rawX >= 2 / 3f * screenWidth -> {
                            Gravity.RIGHT
                        }
                        else -> -1
                    }
                    params.y = (event.rawY - params.height / 2f).toInt()
                    if (gravity != -1) {
                        params.gravity = Gravity.TOP or gravity
                        context.prefManager.drawerHandleSide = gravity
                        setSide(gravity)
                    }
                    updateLayout()
                }
            }
        }

        gestureManager.onTouchEvent(event)

        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        longClickHandler.removeMessages(MSG_LONG_PRESS)
        setMoveMove(false)
    }

    fun onDestroy() {
        prefsHandler.unregister(context)
    }

    fun show(wm: WindowManager = this.wm) {
        try {
            wm.addView(this, params)
        } catch (e: Exception) {
        }
    }

    fun hide(wm: WindowManager = this.wm) {
        try {
            wm.removeView(this)
        } catch (e: Exception) {
        }
    }

    private fun updateLayout(params: WindowManager.LayoutParams = this.params) {
        try {
            wm.updateViewLayout(this, params)
        } catch (e: Exception) {
        }
    }

    private fun setSide(gravity: Int = context.prefManager.drawerHandleSide) {
        background = if (gravity == Gravity.RIGHT) handleRight
        else handleLeft
    }

    private fun setMoveMove(inMoveMode: Boolean) {
        this.inMoveMode = inMoveMode
        val tint = if (inMoveMode)
            Color.argb(255, 120, 200, 255)
        else
            context.prefManager.drawerHandleColor

        setTint(tint)
    }

    private fun setTint(tint: Int) {
        handleLeft?.setTint(tint)
        handleRight?.setTint(tint)
    }

    inner class GestureManager : GestureDetector.SimpleOnGestureListener() {
        private val gestureDetector = GestureDetector(context, this, handler)

        fun onTouchEvent(event: MotionEvent?): Boolean {
            when (event?.action) {
                MotionEvent.ACTION_UP -> calledOpen = false
            }
            return gestureDetector.onTouchEvent(event)
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent?,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            return if (distanceX.absoluteValue > distanceY.absoluteValue && !inMoveMode) {
                if ((distanceX > SWIPE_THRESHOLD && context.prefManager.drawerHandleSide == Gravity.RIGHT)
                    || distanceX < -SWIPE_THRESHOLD
                ) {
                    if (!calledOpen) {
                        calledOpen = true
                        onOpenListener?.invoke()
                        true
                    } else false
                } else false
            } else false
        }

        override fun onLongPress(e: MotionEvent?) {
            onLongPress()
        }

        fun onLongPress() {
            context.vibrate()
            setMoveMove(true)
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            return if (!calledOpen) {
                calledOpen = true
                onOpenListener?.invoke()
                true
            } else false
        }
    }
}