package tk.zwander.lockscreenwidgets.views

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.widget_frame.view.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.PrefManager
import tk.zwander.lockscreenwidgets.util.fadeAndScaleIn
import tk.zwander.lockscreenwidgets.util.fadeAndScaleOut
import tk.zwander.lockscreenwidgets.util.prefManager

class WidgetFrameView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs) {
    var onMoveListener: ((velX: Float, velY: Float) -> Unit)? = null
    var onInterceptListener: ((down: Boolean) -> Unit)? = null
    var onRemoveListener: (() -> Unit)? = null

    var onLeftDragListener: ((velX: Float) -> Unit)? = null
    var onRightDragListener: ((velX: Float) -> Unit)? = null
    var onTopDragListener: ((velY: Float) -> Unit)? = null
    var onBottomDragListener: ((velY: Float) -> Unit)? = null

    var attachmentStateListener: ((isAttached: Boolean) -> Unit)? = null

    private var maxPointerCount = 0
    private var alreadyIndicatedMoving = false

    var isInEditingMode = false
    var shouldShowRemove = true

    private val vibrator by lazy { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }

    init {
        scaleX = 0.95f
        scaleY = 0.95f
        alpha = 0f
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()

        move.setOnTouchListener(MoveTouchListener())
        remove.setOnClickListener {
            onRemoveListener?.invoke()
        }

        left_dragger.setOnTouchListener(ExpandTouchListener { velX, _ ->
            onLeftDragListener?.invoke(velX)
            onLeftDragListener != null
        })
        right_dragger.setOnTouchListener(ExpandTouchListener { velX, _ ->
            onRightDragListener?.invoke(velX)
            onRightDragListener != null
        })
        top_dragger.setOnTouchListener(ExpandTouchListener { _, velY ->
            onTopDragListener?.invoke(velY)
            onTopDragListener != null
        })
        bottom_dragger.setOnTouchListener(ExpandTouchListener { _, velY ->
            onBottomDragListener?.invoke(velY)
            onBottomDragListener != null
        })

        if (context.prefManager.firstViewing) {
            hint_view.isVisible = true
        }

        updatePageIndicatorBehavior()
        updateFrameBackground()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        scaleX = 0.95f
        scaleY = 0.95f
        alpha = 0f

        fadeAndScaleIn {
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

    fun updateFrameBackground() {
        if (context.prefManager.opaqueFrame) {
            frame_card.setCardBackgroundColor(TypedValue().run {
                context.theme.resolveAttribute(R.attr.colorPrimarySurface, this, true)
                data
            })
        } else {
            frame_card.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
        }
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
        fadeAndScaleOut {
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
        remove.isVisible = editing && shouldShowRemove
    }

    private fun vibrate() {
        val time = 50L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(time, 100))
        } else {
            vibrator.vibrate(time)
        }
    }

    inner class ExpandTouchListener(private val listener: ((velX: Float, velY: Float) -> Boolean)?) : OnTouchListener {
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

                    listener?.invoke(velX, velY) ?: false
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