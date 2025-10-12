package tk.zwander.lockscreenwidgets.views

import android.annotation.SuppressLint
import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.bugsnag.android.performance.compose.MeasuredComposable
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import kotlinx.coroutines.flow.MutableStateFlow
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.components.ConfirmFrameRemovalLayout
import tk.zwander.common.compose.components.FrameEditWrapperLayout
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.fadeAndScaleIn
import tk.zwander.common.util.fadeAndScaleOut
import tk.zwander.common.util.handler
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.mainHandler
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.safeAddView
import tk.zwander.common.util.safeRemoveView
import tk.zwander.common.util.safeUpdateViewLayout
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.TaskerIsShowingFrame
import tk.zwander.lockscreenwidgets.compose.IDListLayout
import tk.zwander.lockscreenwidgets.databinding.WidgetFrameBinding
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences

/**
 * The widget frame itself.
 *
 * This contains the widget pager, along with the editing interface. While most of
 * the logic relating to moving, resizing, etc, is handled by the Accessibility service,
 * this View listens for and notifies of the relevant events.
 */
class WidgetFrameView(context: Context, attrs: AttributeSet) : ConstraintLayout(context, attrs), EventObserver {
    var animationState = AnimationState.STATE_IDLE
    private var frameId: Int = Int.MIN_VALUE

    private var maxPointerCount = 0
    private var alreadyIndicatedMoving = false
    private var isProxTooClose by mutableStateOf(false)

    private var isInEditingMode by mutableStateOf(false)

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
            isInEditingMode = false
        }
    }

    private val binding by lazy { WidgetFrameBinding.bind(this) }

    private val debugIdItems = MutableStateFlow<Set<String>>(setOf())

    private var removing by mutableStateOf(false)

    private var debugListShown by mutableStateOf(false)

    private val framePreferences by lazy {
        FrameSpecificPreferences(frameId = frameId, context = context)
    }

    enum class AnimationState {
        STATE_ADDING,
        STATE_REMOVING,
        STATE_IDLE
    }

    fun onCreate(frameId: Int) {
        this.frameId = frameId

        if (context.prefManager.firstViewing && frameId == -1) {
            binding.gestureHintView.root.isVisible = true
        }

        updateFrameBackground()
        context.eventManager.addObserver(this)
    }

    fun onDestroy() {
        context.eventManager.removeObserver(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onFinishInflate() {
        super.onFinishInflate()

        binding.frameCard.alpha = 0f
        binding.frameCard.scaleX = 0.95f
        binding.frameCard.scaleY = 0.95f

        binding.idList.setContent {
            AppTheme {
                AnimatedVisibility(
                    visible = debugListShown,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        MeasuredComposable(name = "IDList") {
                            val items by debugIdItems.collectAsState()

                            IDListLayout(
                                items = items,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }

        binding.removeFrameConfirm.setContent {
            AppTheme {
                AnimatedVisibility(
                    visible = removing,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ConfirmFrameRemovalLayout(
                            itemToRemove = frameId,
                            onItemRemovalConfirmed = { removed, data ->
                                context.eventManager.sendEvent(Event.RemoveFrameConfirmed(removed, frameId))
                                removing = false
                            },
                        )
                    }
                }
            }
        }

        binding.editWrapper.setContent {
            AppTheme {
                AnimatedVisibility(
                    visible = isInEditingMode,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        FrameEditWrapperLayout(
                            frameId = frameId,
                            onRemovePressed = {
                                removing = true
                            },
                        )
                    }
                }
            }
        }

        binding.touchProtectionView.setContent {
            AppTheme {
                AnimatedVisibility(
                    visible = isProxTooClose,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(colorResource(R.color.backdrop)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.touch_protection_active),
                            color = Color.White,
                        )
                    }
                }
            }
        }

        updateDebugIdViewVisibility()
        updatePageIndicatorBehavior()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        TaskerIsShowingFrame::class.java.requestQuery(context)

        sharedPreferencesChangeHandler.register(context)
        if (context.prefManager.touchProtection) {
            registerProxListener()
        }

        binding.frameCard.fadeAndScaleIn {
            context.eventManager.sendEvent(Event.FrameAttachmentState(frameId, true))
            animationState = AnimationState.STATE_IDLE
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        TaskerIsShowingFrame::class.java.requestQuery(context)

        sharedPreferencesChangeHandler.unregister(context)
        unregisterProxListener()

        isInEditingMode = false
        context.eventManager.sendEvent(Event.FrameAttachmentState(frameId, false))
        animationState = AnimationState.STATE_IDLE
    }

    override fun onEvent(event: Event) {
        when (event) {
            is Event.TrimMemory -> {
                @Suppress("DEPRECATION")
                if (event.level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        context.logUtils.debugLog("Attempting to destroy surface because of memory pressure.", null)
                        viewRootImpl?.mSurface?.destroy()
                    } catch (e: Throwable) {
                        context.logUtils.debugLog("Unable to destroy surface.", e)
                    }
                }
            }
            else -> {}
        }
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
                                if (!binding.selectFrameLayout.isVisible) {
                                    isInEditingMode = !isInEditingMode
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
                                }
                                return true
                            }
                        }
                        3 -> {
                            if (binding.hideHintView.root.isVisible) {
                                binding.hideHintView.root.close()
                            }
                            context.eventManager.sendEvent(Event.TempHide(frameId))
                            return true
                        }
                    }

                    if (ev.buttonState == MotionEvent.BUTTON_SECONDARY
                        || ev.buttonState == MotionEvent.BUTTON_STYLUS_SECONDARY) {
                        if (!binding.selectFrameLayout.isVisible) {
                            isInEditingMode = !isInEditingMode
                        }
                        return true
                    }

                    if (ev.buttonState == MotionEvent.BUTTON_TERTIARY) {
                        context.eventManager.sendEvent(Event.TempHide(frameId))
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

    override fun dispatchDraw(canvas: Canvas) {
        context.logUtils.debugLog("dispatchDraw() WidgetFrameView $frameId", null)
        super.dispatchDraw(canvas)
    }

    override fun draw(canvas: Canvas) {
        context.logUtils.debugLog("draw() WidgetFrameView $frameId", null)
        super.draw(canvas)
    }

    override fun drawChild(canvas: Canvas, child: View?, drawingTime: Long): Boolean {
        context.logUtils.debugLog("drawChild() WidgetFrameView $frameId", null)
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun canHaveDisplayList(): Boolean {
        context.logUtils.debugLog("canHaveDisplayList() ${this::class.java.name}")
        return super.canHaveDisplayList()
    }

    fun updateFrameBackground() {
        binding.frameCard.setCardBackgroundColor(framePreferences.backgroundColor)
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
        debugIdItems.value = items.toSet()
    }

    fun updateDebugIdViewVisibility() {
        debugListShown = context.prefManager.showDebugIdView && frameId == -1
    }

    fun addWindow(wm: WindowManager, params: WindowManager.LayoutParams) {
        mainHandler.post {
            context.logUtils.debugLog("Trying to add overlay $animationState", null)

            if (!isAttachedToWindow && animationState != AnimationState.STATE_ADDING) {
                context.logUtils.debugLog("Adding overlay", null)

                animationState = AnimationState.STATE_ADDING

                if (!wm.safeAddView(this, params)) {
                    animationState = AnimationState.STATE_IDLE
                }
            }
        }
    }

    fun updateWindow(wm: WindowManager, params: WindowManager.LayoutParams) {
        mainHandler.post {
            if (isAttachedToWindow) {
                wm.safeUpdateViewLayout(this, params)
            }
        }
    }

    fun removeWindow(wm: WindowManager) {
        mainHandler.post {
            context.logUtils.debugLog("Trying to remove overlay $animationState", null)

            if (isAttachedToWindow && animationState != AnimationState.STATE_REMOVING) {
                animationState = AnimationState.STATE_REMOVING

                context.logUtils.debugLog("Pre-animation removal", null)

                binding.frameCard.fadeAndScaleOut {
                    context.logUtils.debugLog("Post-animation removal", null)

                    postDelayed({
                        context.logUtils.debugLog("Posted removal", null)

                        if (isAttachedToWindow) {
                            wm.safeRemoveView(this)
                        }
                        animationState = AnimationState.STATE_IDLE
                    }, 50)
                }
            } else if (!isAttachedToWindow) {
                wm.safeRemoveView(this, false)

                animationState = AnimationState.STATE_IDLE
            }
        }
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

    private fun onTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                context.eventManager.sendEvent(Event.FrameIntercept(frameId, true))
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                context.eventManager.sendEvent(Event.FrameIntercept(frameId, false))
                alreadyIndicatedMoving = false
            }
        }
    }
}