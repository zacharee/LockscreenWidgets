package tk.zwander.widgetdrawer.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import tk.zwander.common.util.Event
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.dpAsPx
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.handler
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.screenSize
import tk.zwander.common.util.vibrate
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.*
import tk.zwander.widgetdrawer.util.DrawerDelegate
import kotlin.math.absoluteValue

class Handle : LinearLayout {
    companion object {
        private const val MSG_LONG_PRESS = 0

        private const val LONG_PRESS_DELAY = 300
        private const val SWIPE_THRESHOLD = 20
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private var inMoveMode = false
    private var calledOpen = false
    var scrollingOpen = false
        private set
    private var scrollTotalX = 0f
    private var initialScrollRawX = 0f
    private var moveRawY = 0f
    private var screenWidth = -1

    private var currentVisibilityAnim: Animator? = null

    private val gestureManager = GestureManager()
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val handleLeft = AppCompatResources.getDrawable(context, R.drawable.drawer_handle_left)
    private val handleRight = AppCompatResources.getDrawable(context, R.drawable.drawer_handle_right)

    private val longClickHandler = @SuppressLint("HandlerLeak")
    object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
                MSG_LONG_PRESS -> gestureManager.onLongPress()
            }
        }
    }

    val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
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
                initialScrollRawX = event.rawX
                moveRawY = event.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longClickHandler.removeMessages(MSG_LONG_PRESS)
                setMoveMode(false)
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
                    params.y += (event.rawY - moveRawY).toInt()
                    moveRawY = event.rawY
                    if (gravity != -1) {
                        params.gravity = Gravity.TOP or gravity
                        context.prefManager.drawerHandleSide = gravity
                        setSide(gravity)
                    }
                    updateLayout()
                } else if (scrollingOpen) {
                    scrollTotalX += (event.rawX - initialScrollRawX)
                    initialScrollRawX = event.rawX

                    context.eventManager.sendEvent(Event.ScrollInDrawer(
                        context.prefManager.drawerHandleSide,
                        scrollTotalX.absoluteValue,
                        false
                    ))
                }
            }
        }

        gestureManager.onTouchEvent(event)

        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        handler?.postDelayed({
            currentVisibilityAnim?.cancel()
            val anim = ValueAnimator.ofFloat(0f, 1f)
            currentVisibilityAnim = anim

            anim.interpolator = DecelerateInterpolator()
            anim.duration = DrawerDelegate.ANIM_DURATION
            anim.addUpdateListener {
                alpha = it.animatedValue.toString().toFloat()
            }
            anim.start()
        }, 10)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        longClickHandler.removeMessages(MSG_LONG_PRESS)
        setMoveMode(false)
        calledOpen = false
        scrollingOpen = false
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
        currentVisibilityAnim?.cancel()
        val anim = ValueAnimator.ofFloat(1f, 0f)
        currentVisibilityAnim = anim

        anim.interpolator = AccelerateInterpolator()
        anim.duration = DrawerDelegate.ANIM_DURATION
        anim.addUpdateListener {
            this.alpha = it.animatedValue.toString().toFloat()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                handler?.postDelayed({
                    try {
                        wm.removeView(this@Handle)
                    } catch (e: Exception) {
                    }
                }, 10)
            }
        })
        anim.start()
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

    private fun setMoveMode(inMoveMode: Boolean) {
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
                MotionEvent.ACTION_UP -> {
                    if (scrollingOpen) {
                        scrollingOpen = false
                        context.eventManager.sendEvent(Event.ScrollOpenFinish(
                            context.prefManager.drawerHandleSide
                        ))
                    }

                    calledOpen = false
                    scrollTotalX = 0f
                }
            }
            return gestureDetector.onTouchEvent(event)
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent?,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            return if (distanceX.absoluteValue > distanceY.absoluteValue && !inMoveMode && distanceX.absoluteValue > SWIPE_THRESHOLD) {
                val initial = !scrollingOpen

                if (initial) {
                    context.vibrate(25L)
                    scrollingOpen = true

                    context.eventManager.sendEvent(Event.ScrollInDrawer(
                        context.prefManager.drawerHandleSide,
                        scrollTotalX.absoluteValue,
                        true
                    ))
                }
                true
            } else false
        }

        override fun onLongPress(e: MotionEvent?) {
            if (isAttachedToWindow) {
                onLongPress()
            }
        }

        fun onLongPress() {
            if (!scrollingOpen && !calledOpen && !inMoveMode) {
                context.vibrate()
                setMoveMode(true)
            }
        }

        override fun onDoubleTap(e: MotionEvent?): Boolean {
            return if (!calledOpen) {
                calledOpen = true
                context.vibrate(25L)
                context.eventManager.sendEvent(Event.ShowDrawer)
                true
            } else false
        }
    }
}