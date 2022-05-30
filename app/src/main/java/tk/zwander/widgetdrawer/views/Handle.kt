package tk.zwander.widgetdrawer.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
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
import tk.zwander.helperlib.dpAsPx
import tk.zwander.widgetdrawer.R
import tk.zwander.widgetdrawer.utils.PrefsManager
import tk.zwander.widgetdrawer.utils.screenSize
import tk.zwander.widgetdrawer.utils.vibrate
import kotlin.math.absoluteValue

class Handle : LinearLayout, SharedPreferences.OnSharedPreferenceChangeListener {
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
    private val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = PrefsManager.getInstance(context)

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
        type = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_PRIORITY_PHONE
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        width = context.dpAsPx(prefs.handleWidthDp)
        height = context.dpAsPx(prefs.handleHeightDp)
        gravity = Gravity.TOP or prefs.handleSide
        y = prefs.handleYPx.toInt()
        format = PixelFormat.RGBA_8888
    }

    init {
        setSide()
        setTint(prefs.handleColor)
        isClickable = true
        prefs.addPrefListener(this)
        elevation = if (prefs.handleShadow) context.dpAsPx(8).toFloat() else 0f
        contentDescription = resources.getString(R.string.open_widget_drawer)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                screenWidth = context.screenSize().x
                longClickHandler.sendEmptyMessageAtTime(
                    MSG_LONG_PRESS,
                    event.downTime + LONG_PRESS_DELAY
                )
            }
            MotionEvent.ACTION_UP -> {
                longClickHandler.removeMessages(MSG_LONG_PRESS)
                setMoveMove(false)
                prefs.handleYPx = params.y.toFloat()
            }
            MotionEvent.ACTION_MOVE -> {
                if (inMoveMode) {
                    val gravity = when {
                        event.rawX <= 1 / 3f * screenWidth -> {
                            PrefsManager.HANDLE_LEFT
                        }
                        event.rawX >= 2 / 3f * screenWidth -> {
                            PrefsManager.HANDLE_RIGHT
                        }
                        else -> -1
                    }
                    params.y = (event.rawY - params.height / 2f).toInt()
                    if (gravity != PrefsManager.HANDLE_UNCHANGED) {
                        params.gravity = Gravity.TOP or gravity
                        prefs.handleSide = gravity
                        setSide(gravity)
                    }
                    updateLayout()
                }
            }
        }

        gestureManager.onTouchEvent(event)

        return true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PrefsManager.HANDLE_HEIGHT -> {
                params.height = context.dpAsPx(prefs.handleHeightDp)
                updateLayout()
            }

            PrefsManager.HANDLE_WIDTH -> {
                params.width = context.dpAsPx(prefs.handleWidthDp)
                updateLayout()
            }

            PrefsManager.HANDLE_COLOR -> {
                setTint(prefs.handleColor)
            }

            PrefsManager.HANDLE_SHADOW -> {
                elevation = if (prefs.handleShadow) context.dpAsPx(8).toFloat() else 0f
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        longClickHandler.removeMessages(MSG_LONG_PRESS)
        setMoveMove(false)
    }

    fun onDestroy() {
        prefs.removePrefListener(this)
    }

    fun show(wm: WindowManager = this.wm, overrideType: Int = params.type) {
        try {
            wm.addView(this, params.apply { type = overrideType })
        } catch (e: Exception) {}
    }

    fun hide(wm: WindowManager = this.wm) {
        try {
            wm.removeView(this)
        } catch (e: Exception) {}
    }

    private fun updateLayout(params: WindowManager.LayoutParams = this.params) {
        try {
            wm.updateViewLayout(this, params)
        } catch (e: Exception) {}
    }

    private fun setSide(gravity: Int = prefs.handleSide) {
        background = if (gravity == PrefsManager.HANDLE_RIGHT) handleRight
        else handleLeft
    }

    private fun setMoveMove(inMoveMode: Boolean) {
        this.inMoveMode = inMoveMode
        val tint = if (inMoveMode)
            Color.argb(255, 120, 200, 255)
        else
            prefs.handleColor

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

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
            return if (distanceX.absoluteValue > distanceY.absoluteValue && !inMoveMode) {
                if ((distanceX > SWIPE_THRESHOLD && prefs.handleSide == PrefsManager.HANDLE_RIGHT)
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
            context.vibrate(50)
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