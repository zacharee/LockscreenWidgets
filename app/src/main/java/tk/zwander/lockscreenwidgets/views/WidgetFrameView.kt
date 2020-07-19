package tk.zwander.lockscreenwidgets.views

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.widget_frame.view.*
import kotlinx.android.synthetic.main.widget_frame_id_view.view.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.adapters.IDAdapter
import tk.zwander.lockscreenwidgets.util.*
import kotlin.math.roundToInt

/**
 * The widget frame itself.
 *
 * This contains the widget pager, along with the editing interface. While most of
 * the logic relating to moving, resizing, etc, is handled by the Accessibility service,
 * this View listens for and notifies of the relevant events.
 */
class WidgetFrameView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {
    var onMoveListener: ((velX: Float, velY: Float) -> Unit)? = null
    var onInterceptListener: ((down: Boolean) -> Unit)? = null
    var onAddListener: (() -> Unit)? = null

    var onLeftDragListener: ((velX: Int) -> Unit)? = null
    var onRightDragListener: ((velX: Int) -> Unit)? = null
    var onTopDragListener: ((velY: Int) -> Unit)? = null
    var onBottomDragListener: ((velY: Int) -> Unit)? = null

    var onTempHideListener: (() -> Unit)? = null
    var onAfterResizeListener: (() -> Unit)? = null

    var attachmentStateListener: ((isAttached: Boolean) -> Unit)? = null

    private var maxPointerCount = 0
    private var alreadyIndicatedMoving = false

    var isInEditingMode = false

    private val vibrator by lazy { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    @SuppressLint("ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()

        frame_card.alpha = 0f
        frame_card.scaleX = 0.95f
        frame_card.scaleY = 0.95f

        updateCornerRadius()

        move.setOnTouchListener(MoveTouchListener())

        left_dragger.setOnTouchListener(ExpandTouchListener { velX, _, isUp ->
            onLeftDragListener?.invoke(velX)
            if (isUp) {
                onAfterResizeListener?.invoke()
            }
            onLeftDragListener != null
        })
        right_dragger.setOnTouchListener(ExpandTouchListener { velX, _, isUp ->
            onRightDragListener?.invoke(velX)
            if (isUp) {
                onAfterResizeListener?.invoke()
            }
            onRightDragListener != null
        })
        top_dragger.setOnTouchListener(ExpandTouchListener { _, velY, isUp ->
            onTopDragListener?.invoke(velY)
            if (isUp) {
                onAfterResizeListener?.invoke()
            }
            onTopDragListener != null
        })
        bottom_dragger.setOnTouchListener(ExpandTouchListener { _, velY, isUp ->
            onBottomDragListener?.invoke(velY)
            if (isUp) {
                onAfterResizeListener?.invoke()
            }
            onBottomDragListener != null
        })
        add_widget.setOnClickListener {
            onAddListener?.invoke()
        }
        temp_hide_frame.setOnClickListener {
            onTempHideListener?.invoke()
        }

        if (context.prefManager.firstViewing) {
            hint_view.isVisible = true
        }

        id_list.adapter = IDAdapter()
        id_list.itemAnimator = null

        updateDebugIdViewVisibility()
        updatePageIndicatorBehavior()
        updateFrameBackground()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        frame_card.fadeAndScaleIn {
            attachmentStateListener?.invoke(true)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        setEditMode(false)
        attachmentStateListener?.invoke(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        onTouch(ev)

        maxPointerCount = ev.pointerCount.run {
            var properMax = this

            for (i in 0 until this) {
                val coords = MotionEvent.PointerCoords().apply { ev.getPointerCoords(i, this) }

                if (coords.x < 0 || coords.x > width || coords.y < 0 || coords.y > height) {
                    properMax--
                }
            }

            if (properMax > maxPointerCount) properMax else maxPointerCount
        }

        when (ev.action) {
            MotionEvent.ACTION_UP -> {
                val max = maxPointerCount
                maxPointerCount = 0

                when (max) {
                    2 -> {
                        setEditMode(!isInEditingMode)
                        return true
                    }
                    3 -> {
                        onTempHideListener?.invoke()
                        return true
                    }
                }

                if (ev.buttonState == MotionEvent.BUTTON_SECONDARY
                    || ev.buttonState == MotionEvent.BUTTON_STYLUS_SECONDARY) {
                    setEditMode(!isInEditingMode)
                    return true
                }

                if (ev.buttonState == MotionEvent.BUTTON_TERTIARY) {
                    onTempHideListener?.invoke()
                    return true
                }
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return (maxPointerCount > 1)
    }

    fun updateCornerRadius() {
        val radius = context.dpAsPx(context.prefManager.cornerRadiusDp).toFloat()
        frame_card.radius = radius

        //Since all instances of a Drawable are technically the same instance
        //(unless someone uses mutate()), setting the corner radius for the
        //editing outline here means it applies to both the main frame editing UI
        //and each widget editing UI.
        (edit_outline.background as GradientDrawable).apply {
            this.cornerRadius = radius
        }
    }

    fun updateFrameBackground() {
        frame_card.setCardBackgroundColor(context.prefManager.backgroundColor)
    }

    fun updatePageIndicatorBehavior() {
        widgets_pager.apply {
            when (context.prefManager.pageIndicatorBehavior) {
                PrefManager.VALUE_PAGE_INDICATOR_BEHAVIOR_HIDDEN -> {
                    isHorizontalScrollBarEnabled = false
                    isScrollbarFadingEnabled = false
                }
                PrefManager.VALUE_PAGE_INDICATOR_BEHAVIOR_AUTO_HIDE -> {
                    isHorizontalScrollBarEnabled = true
                    isScrollbarFadingEnabled = true
                    scrollBarFadeDuration = ViewConfiguration.getScrollBarFadeDuration()
                }
                PrefManager.VALUE_PAGE_INDICATOR_BEHAVIOR_SHOWN -> {
                    isHorizontalScrollBarEnabled = true
                    scrollBarFadeDuration = 0
                    isScrollbarFadingEnabled = false
                }
            }
        }
    }

    fun setNewDebugIdItems(items: List<String>) {
        (id_list.adapter as IDAdapter).setItems(items)
    }

    fun updateDebugIdViewVisibility() {
        id_view.isVisible = context.prefManager.showDebugIdView
    }

    fun addWindow(wm: WindowManager, params: WindowManager.LayoutParams) {
        try {
            wm.addView(this, params)
        } catch (e: Exception) {}
    }

    fun updateWindow(wm: WindowManager, params: WindowManager.LayoutParams) {
        try {
            wm.updateViewLayout(this, params)
        } catch (e: Exception) {}
    }

    fun removeWindow(wm: WindowManager) {
        frame_card.fadeAndScaleOut {
            try {
                wm.removeView(this)
            } catch (e: Exception) {}
        }
    }

    private fun onTouch(event: MotionEvent): Boolean {
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
            else -> false
        }
    }

    private fun setEditMode(editing: Boolean) {
        isInEditingMode = editing

        edit_wrapper.isVisible = editing
    }

    private fun vibrate() {
        val time = 50L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(time, 100))
        } else {
            vibrator.vibrate(time)
        }
    }

    inner class ExpandTouchListener(private val listener: ((velX: Int, velY: Int, isUp: Boolean) -> Boolean)?) : OnTouchListener {
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

                    val velX = try {
                        (newX - prevExpandX).roundToInt()
                    } catch (e: IllegalArgumentException) {
                        0
                    }
                    val velY = try {
                        (newY - prevExpandY).roundToInt()
                    } catch (e: IllegalArgumentException) {
                        0
                    }

                    prevExpandX = newX
                    prevExpandY = newY

                    //Make sure the velocities are even.
                    //This prevents an issue where the frame moves when resizing,
                    //since its position needs to be compensated by half the velocity.
                    //If the velocity is odd, the frame may over-compensate, causing it
                    //to push the opposite side the opposite direction by a pixel every time
                    //this is invoked.
                    listener?.invoke(velX.makeEven(), velY.makeEven(), false) ?: false
                }
                MotionEvent.ACTION_UP -> {
                    listener?.invoke(0, 0, true)
                    false
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