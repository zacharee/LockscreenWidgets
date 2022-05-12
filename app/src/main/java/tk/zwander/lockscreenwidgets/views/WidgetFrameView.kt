package tk.zwander.lockscreenwidgets.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.adapters.IDAdapter
import tk.zwander.lockscreenwidgets.databinding.WidgetFrameBinding
import tk.zwander.lockscreenwidgets.util.*
import kotlin.math.roundToInt

/**
 * The widget frame itself.
 *
 * This contains the widget pager, along with the editing interface. While most of
 * the logic relating to moving, resizing, etc, is handled by the Accessibility service,
 * this View listens for and notifies of the relevant events.
 */
class WidgetFrameView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {
    var onMoveListener: ((velX: Float, velY: Float) -> Unit)? = null
    var onInterceptListener: ((down: Boolean) -> Unit)? = null
    var onAddListener: (() -> Unit)? = null

    var onLeftDragListener: ((velX: Int) -> Unit)? = null
    var onRightDragListener: ((velX: Int) -> Unit)? = null
    var onTopDragListener: ((velY: Int) -> Unit)? = null
    var onBottomDragListener: ((velY: Int) -> Unit)? = null

    var onTempHideListener: (() -> Unit)? = null
    var onAfterResizeListener: (() -> Unit)? = null
    var onAfterMoveListener: (() -> Unit)? = null

    var attachmentStateListener: ((isAttached: Boolean) -> Unit)? = null
    var animationState = AnimationState.STATE_IDLE

    var informationCallback: IInformationCallback? = null

    private var maxPointerCount = 0
    private var alreadyIndicatedMoving = false
    private var isProxTooClose = false
        set(value) {
            field = value

            updateProximity(value)
        }

    var isInEditingMode = false

    private val vibrator by lazy { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    private val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    private val proximityListener = object : SensorEventListener2 {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onFlushCompleted(sensor: Sensor?) {}

        override fun onSensorChanged(event: SensorEvent) {
            val dist = event.values[0]

            isProxTooClose = dist < 5
        }
    }

    private val sharedPreferencesChangeHandler = HandlerRegistry {
        handler(PrefManager.KEY_TOUCH_PROTECTION) {
            if (isAttachedToWindow) {
                if (context.prefManager.touchProtection) {
                    registerProxListener()
                } else {
                    unregisterProxListener()
                }
            }
        }
        handler(PrefManager.KEY_LOCK_WIDGET_FRAME) {
            setEditMode(false)
        }
        handler(PrefManager.KEY_BLUR_BACKGROUND_AMOUNT) {
            blurDrawable?.setBlurRadius(context.prefManager.backgroundBlurAmount)
        }
    }

    private val binding by lazy { WidgetFrameBinding.bind(this) }

    var blurDrawable: BackgroundBlurDrawable? = null
        private set

    enum class AnimationState {
        STATE_ADDING,
        STATE_REMOVING,
        STATE_IDLE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()

        binding.frameCard.alpha = 0f
        binding.frameCard.scaleX = 0.95f
        binding.frameCard.scaleY = 0.95f

        updateCornerRadius()

        binding.move.setOnTouchListener(MoveTouchListener())
        binding.centerHorizontally.setOnClickListener {
            informationCallback?.apply {
                context.prefManager.setCorrectFrameX(saveMode, 0)
            }
        }
        binding.centerVertically.setOnClickListener {
            informationCallback?.apply {
                context.prefManager.setCorrectFrameY(saveMode, 0)
            }
        }

        binding.leftDragger.setOnTouchListener(ExpandTouchListener { velX, _, isUp ->
            onLeftDragListener?.invoke(velX)
            if (isUp) {
                onAfterResizeListener?.invoke()
            }
            onLeftDragListener != null
        })
        binding.rightDragger.setOnTouchListener(ExpandTouchListener { velX, _, isUp ->
            onRightDragListener?.invoke(velX)
            if (isUp) {
                onAfterResizeListener?.invoke()
            }
            onRightDragListener != null
        })
        binding.topDragger.setOnTouchListener(ExpandTouchListener { _, velY, isUp ->
            onTopDragListener?.invoke(velY)
            if (isUp) {
                onAfterResizeListener?.invoke()
            }
            onTopDragListener != null
        })
        binding.bottomDragger.setOnTouchListener(ExpandTouchListener { _, velY, isUp ->
            onBottomDragListener?.invoke(velY)
            if (isUp) {
                onAfterResizeListener?.invoke()
            }
            onBottomDragListener != null
        })
        binding.addWidget.setOnClickListener {
            onAddListener?.invoke()
        }
        binding.tempHideFrame.setOnClickListener {
            onTempHideListener?.invoke()
        }

        if (context.prefManager.firstViewing) {
            binding.gestureHintView.root.isVisible = true
        }

        binding.idView.idList.adapter = IDAdapter()
        binding.idView.idList.itemAnimator = null

        updateDebugIdViewVisibility()
        updatePageIndicatorBehavior()
        updateFrameBackground()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        context.prefManager.prefs.registerOnSharedPreferenceChangeListener(this)
        if (context.prefManager.touchProtection) {
            registerProxListener()
        }

        postDelayed({
            binding.frameCard.fadeAndScaleIn {
                attachmentStateListener?.invoke(true)
                animationState = AnimationState.STATE_IDLE
            }
        }, 50)

        blurDrawable = viewRootImpl.createBackgroundBlurDrawable()
        blurDrawable?.setBlurRadius(context.prefManager.backgroundBlurAmount)
        updateCornerRadius()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        context.prefManager.prefs.unregisterOnSharedPreferenceChangeListener(this)
        unregisterProxListener()

        setEditMode(false)
        attachmentStateListener?.invoke(false)
        animationState = AnimationState.STATE_IDLE
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isProxTooClose) {
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
                            if (!context.prefManager.lockWidgetFrame) {
                                setEditMode(!isInEditingMode)
                                if (binding.gestureHintView.root.isVisible) {
                                    val ghv = binding.gestureHintView.root
                                    if (!ghv.stage2) {
                                        ghv.stage2 = true
                                    } else if (ghv.stage2) {
                                        ghv.stage2 = false
                                        ghv.close()
                                        binding.hideHintView.root.isVisible = true
                                    }
                                }
                                return true
                            }
                        }
                        3 -> {
                            if (binding.hideHintView.root.isVisible) {
                                binding.hideHintView.root.close()
                            }
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
        }

        return super.dispatchTouchEvent(ev) && !isProxTooClose
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return (maxPointerCount > 1 || isProxTooClose)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        sharedPreferencesChangeHandler.handle(key)
    }

    fun updateCornerRadius() {
        val radius = context.dpAsPx(context.prefManager.cornerRadiusDp).toFloat()
        binding.frameCard.radius = radius
        blurDrawable?.setCornerRadius(radius)

        //Since all instances of a Drawable are technically the same instance
        //(unless someone uses mutate()), setting the corner radius for the
        //editing outline here means it applies to both the main frame editing UI
        //and each widget editing UI.
        (binding.editOutline.background as GradientDrawable).apply {
            this.cornerRadius = radius
        }
    }

    fun updateFrameBackground() {
        binding.frameCard.setCardBackgroundColor(context.prefManager.backgroundColor)
    }

    fun updatePageIndicatorBehavior() {
        binding.widgetsPager.apply {
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
        (binding.idView.idList.adapter as IDAdapter).setItems(items)
    }

    fun updateDebugIdViewVisibility() {
        binding.idView.root.isVisible = context.prefManager.showDebugIdView
    }

    fun addWindow(wm: WindowManager, params: WindowManager.LayoutParams) {
        context.logUtils.debugLog("Trying to add overlay $animationState")

        if (!isAttachedToWindow && animationState != AnimationState.STATE_ADDING) {
            context.logUtils.debugLog("Adding overlay")

            animationState = AnimationState.STATE_ADDING

            if (!wm.safeAddView(this, params)) {
                animationState = AnimationState.STATE_IDLE
            }
        }
    }

    fun updateWindow(wm: WindowManager, params: WindowManager.LayoutParams) {
        if (isAttachedToWindow) {
            wm.safeUpdateViewLayout(this, params)
        }
    }

    fun removeWindow(wm: WindowManager) {
        context.logUtils.debugLog("Trying to remove overlay $animationState")

        if (isAttachedToWindow && animationState != AnimationState.STATE_REMOVING) {
            animationState = AnimationState.STATE_REMOVING

            context.logUtils.debugLog("Pre-animation removal")

            binding.frameCard.fadeAndScaleOut {
                context.logUtils.debugLog("Post-animation removal")

                postDelayed({
                    context.logUtils.debugLog("Posted removal")

                    wm.safeRemoveView(this)
                    animationState = AnimationState.STATE_IDLE
                }, 50)
            }
        } else if (!isAttachedToWindow) {
            wm.safeRemoveView(this)

            animationState = AnimationState.STATE_IDLE
        }
    }

    private fun updateProximity(tooClose: Boolean) {
        binding.touchProtectionView.root.isVisible = tooClose
    }

    private fun registerProxListener() {
        sensorManager.registerListener(
            proximityListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
            1 * 200 * 1000 /* 200ms */
        )
    }

    private fun unregisterProxListener() {
        sensorManager.unregisterListener(proximityListener)
        isProxTooClose = false
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
        binding.editWrapper.isVisible = editing
    }

    private fun vibrate() {
        val time = 50L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(time, 100))
        } else {
            @Suppress("DEPRECATION")
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
                MotionEvent.ACTION_UP -> {
                    onAfterMoveListener?.invoke()
                    true
                }
                else -> false
            }
        }
    }
}