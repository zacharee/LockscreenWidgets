package tk.zwander.widgetdrawer.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.activities.SelectIconPackActivity
import tk.zwander.common.compose.WidgetGrid
import tk.zwander.common.compose.components.DrawerHandle
import tk.zwander.common.compose.util.createComposeViewHolder
import tk.zwander.common.compose.util.findAccessibility
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.provider.IDrawerProvider
import tk.zwander.common.listeners.WidgetResizeListener
import tk.zwander.common.util.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.widgetdrawer.activities.TaskerIsShowingDrawer
import tk.zwander.widgetdrawer.activities.add.ReconfigureDrawerWidgetActivity
import tk.zwander.widgetdrawer.compose.DrawerLayout
import kotlin.math.absoluteValue
import kotlin.math.sign

class DrawerDelegate private constructor(context: Context, displayId: String) :
    BaseDelegate<DrawerDelegate.State>(context = context, targetDisplayId = displayId), IDrawerProvider {
    companion object {
        const val ID = -2

        @SuppressLint("StaticFieldLeak")
        private val instance = MutableStateFlow<DrawerDelegate?>(null)

        val readOnlyInstance = instance.asStateFlow()

        @Synchronized
        fun peekInstance(context: Context): DrawerDelegate? {
            if (instance.value == null) {
                context.logUtils.debugLog("Accessibility isn't running yet")

                return null
            }

            return instance.value
        }

        @Synchronized
        fun getInstance(context: Context, displayId: String): DrawerDelegate {
            return instance.value ?: run {
                val accessibilityContext = context.findAccessibility()

                if (accessibilityContext == null) {
                    throw IllegalStateException("Delegate can only be initialized by Accessibility Service!")
                } else {
                    DrawerDelegate(accessibilityContext, displayId).also {
                        instance.value = it
                    }
                }
            }
        }

        fun invalidateInstance() {
            instance.value = null
        }
    }

    override val holderId: Int
        get() = ID

    override var state = State()
        set(value) {
            field = value
            lifecycleScope.launch(Dispatchers.Main) {
                updateWindow()
            }
        }

    override val params by lazy {
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

            this@DrawerDelegate.display?.rotatedRealSize?.let { displaySize ->
                width = displaySize.x
                height = displaySize.y
            }

            format = PixelFormat.RGBA_8888
            gravity = Gravity.TOP or Gravity.CENTER
        }
    }
    override val rootView: View
        get() = drawer

    private val handleParams by lazy {
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            this@DrawerDelegate.display?.let { display ->
                width = display.dpToPx(context.prefManager.drawerHandleWidth)
                height = display.dpToPx(context.prefManager.drawerHandleHeight)
            }

            gravity = Gravity.TOP or context.prefManager.drawerHandleSide
            y = context.prefManager.drawerHandleYPosition
            format = PixelFormat.RGBA_8888
        }
    }

    private val drawer by lazy {
        viewModel.createComposeViewHolder {
            val cutoutPadding = WindowInsets.displayCutout

            val drawerSidePadding by rememberPreferenceState(
                key = PrefManager.KEY_DRAWER_SIDE_PADDING,
                value = {
                    context.prefManager.drawerSidePadding.dp
                },
            )
            val closeOnTap by rememberPreferenceState(
                key = PrefManager.KEY_CLOSE_DRAWER_ON_EMPTY_TAP,
            ) {
                prefManager.closeOnEmptyTap
            }

            val combinedPadding = cutoutPadding.add(
                WindowInsets(left = drawerSidePadding, right = drawerSidePadding),
            )

            DrawerLayout(
                widgetGrid = { modifier ->
                    val rowCount = remember(viewModel.display) {
                        viewModel.display.orDefault(context).rotatedRealSize.y / resources.getDimensionPixelSize(R.dimen.drawer_row_height)
                    }
                    val columnCount by rememberPreferenceState(
                        key = PrefManager.KEY_DRAWER_COL_COUNT,
                    ) {
                        prefManager.drawerColCount
                    }
                    var currentWidgetsState by rememberPreferenceState(
                        key = PrefManager.KEY_DRAWER_WIDGETS,
                        value = { currentWidgets.toList() },
                        onChanged = { _, value -> currentWidgets = value.toSet() },
                    )

                    WidgetGrid(
                        currentWidgets = currentWidgetsState,
                        onWidgetsChanged = { widgets ->
                            currentWidgetsState = widgets
                        },
                        orientation = Orientation.Vertical,
                        columnCount = columnCount,
                        rowCount = rowCount,
                        resizeThresholdPx = { which ->
                            if (which == WidgetResizeListener.Which.LEFT || which == WidgetResizeListener.Which.RIGHT) {
                                viewModel.display.orDefault(context).rotatedRealSize.x / colCount
                            } else {
                                resources.getDimensionPixelSize(R.dimen.drawer_row_height)
                            }
                        },
                        launchAddActivity = {
                            eventManager.sendEvent(Event.CloseDrawer)
                            eventManager.sendEvent(Event.LaunchAddDrawerWidget(true))
                        },
                        launchReconfigure = { id, providerInfo ->
                            eventManager.sendEvent(Event.CloseDrawer)
                            ReconfigureDrawerWidgetActivity.launch(context, id, providerInfo)
                        },
                        launchShortcutIconOverride = {
                            eventManager.sendEvent(Event.CloseDrawer)
                            SelectIconPackActivity.launchForOverride(context, holderId, true)
                        },
                        modifier = modifier,
                        rowSpanForAddButton = 1,
                        enableSnapping = false,
                        contentPadding = combinedPadding.asPaddingValues(),
                    )
                },
                modifier = Modifier.fillMaxSize()
                    .then(
                        if (closeOnTap) {
                            Modifier.clickable(
                                enabled = true,
                                onClick = {
                                    eventManager.sendEvent(Event.CloseDrawer)
                                },
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            )
                        } else { Modifier },
                    ),
            )
        }.apply {
            addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.hideNavBarsForGestureExclusion()
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        eventManager.sendEvent(Event.DrawerAttachmentState(false))
                    }
                },
            )
        }
    }
    private val handle by lazy {
        viewModel.createComposeViewHolder {
            DrawerHandle(
                params = handleParams,
                displayId = displayId,
                updateWindow = {
                    it.hideNavBarsForGestureExclusion()
                    wm?.safeUpdateViewLayout(it, handleParams)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }.apply {
            addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.hideNavBarsForGestureExclusion()
                    }

                    override fun onViewDetachedFromWindow(v: View) {}
                },
            )
        }
    }

    override val prefsHandler = HandlerRegistry {
        handler(PrefManager.KEY_DRAWER_ENABLED) {
            if (prefManager.drawerEnabled) {
                tryShowHandle()
            } else {
                hideAll()
            }
        }
        handler(PrefManager.KEY_SHOW_DRAWER_HANDLE) {
            if (prefManager.showDrawerHandle) {
                tryShowHandle()
            } else {
                hideHandle()
            }
        }
        handler(PrefManager.KEY_LOCK_WIDGET_DRAWER) {
            viewModel.currentEditingInterfaceId.value = RecyclerView.NO_POSITION
        }
        handler(PrefManager.KEY_SHOW_DRAWER_HANDLE_ONLY_WHEN_LOCKED) {
            if (!prefManager.showDrawerHandleOnlyWhenLocked) {
                tryShowHandle()
            } else if (!globalState.wasOnKeyguard.value) {
                hideHandle()
            }
        }
    }

    override val viewModel = DrawerViewModel(this)

    override val rootViewAttachmentStateListener = object : View.OnAttachStateChangeListener {
        val superObj = super@DrawerDelegate.rootViewAttachmentStateListener

        override fun onViewAttachedToWindow(v: View) {
            superObj.onViewAttachedToWindow(v)
        }

        override fun onViewDetachedFromWindow(v: View) {
            if (!drawer.isAttachedToWindow && !handle.isAttachedToWindow) {
                superObj.onViewDetachedFromWindow(v)
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    override suspend fun onEvent(event: Event) {
        super.onEvent(event)

        when (event) {
            Event.ShowDrawer -> {
                params.x = 0
                showDrawer()
            }

            Event.CloseDrawer -> {
                hideDrawer()
            }

            Event.ShowHandle -> {
                tryShowHandle()
            }

            Event.DrawerShown -> {
                hideHandle()
            }

            Event.DrawerHidden -> {
                tryShowHandle()
            }

            Event.CloseSystemDialogs -> {
                hideDrawer()
            }

            is Event.DrawerIntercept -> {
                forceWakelock(event.down)
            }

            is Event.DrawerAttachmentState -> {
                TaskerIsShowingDrawer::class.java.requestQuery(this)
                if (event.attached) {
                    if (!viewModel.scrollingOpen.value) {
                        drawer.fadeIn(DrawerOrFrame.DRAWER)
                        eventManager.sendEvent(Event.DrawerShown)
                        viewModel.drawerAnimationState.value = AnimationState.IDLE
                    } else {
                        drawer.alpha = 1f
                    }
                }
            }

            Event.DrawerBackButtonClick -> {
                hideDrawer()
            }

            is Event.ScrollInDrawer -> {
                if (event.velocity.sign != viewModel.latestScrollInVelocity.value.sign) {
                    viewModel.latestScrollInVelocity.value = 0f
                }

                viewModel.latestScrollInVelocity.value += event.velocity

                val distanceFromEdge = (params.width - event.dist)

                params.gravity = event.from
                params.x = -distanceFromEdge.toInt()

                if (event.initial) {
                    showDrawer(hideHandle = false)
                } else {
                    updateWindow()
                }
            }

            is Event.ScrollOpenFinish -> {
                val distanceFromEdge = (params.width + params.x).absoluteValue
                val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
                val velocityMatches = when {
                    viewModel.latestScrollInVelocity.value.absoluteValue < touchSlop -> true
                    event.from == Gravity.LEFT -> viewModel.latestScrollInVelocity.value > 0
                    else -> viewModel.latestScrollInVelocity.value < 0
                }
                val metThreshold = this@DrawerDelegate.display?.let { display ->
                    distanceFromEdge > display.dpToPx(100f) && velocityMatches
                } ?: false

                val animator = ValueAnimator.ofInt(params.x, if (metThreshold) 0 else -params.width)
                animator.addUpdateListener {
                    params.x = it.animatedValue as Int
                    lifecycleScope.launch {
                        updateWindow()
                    }
                }
                animator.duration = with(DrawerOrFrame.DRAWER) { duration() }
                animator.interpolator =
                    if (metThreshold) DecelerateInterpolator() else AccelerateInterpolator()
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!metThreshold) {
                            lifecycleScope.launch {
                                hideDrawer()
                            }
                        } else {
                            eventManager.sendEvent(Event.DrawerShown)
                        }
                    }
                })
                animator.start()
            }

            Event.LockscreenDismissed -> {
                if (prefManager.showDrawerHandleOnlyWhenLocked && !globalState.wasOnKeyguard.value) {
                    hideHandle()
                }
            }

            else -> {}
        }
    }

    override fun onWidgetClick(trigger: Boolean): Boolean {
        return if (!globalState.itemIsActive.value) {
            if (trigger && prefManager.requestUnlockDrawer && prefManager.drawerDirectlyCheckForActivity) {
                DismissOrUnlockActivity.launch(this)
                eventManager.sendEvent(Event.CloseDrawer)
            } else {
                if (prefManager.requestUnlockDrawer) {
                    globalState.handlingClick[ID] = Unit
                }
            }

            true
        } else {
            false
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()

        if (created) {
            return
        }

        handle.setViewTreeLifecycleOwner(this)
        handle.setViewTreeSavedStateRegistryOwner(this)
        handle.compositionContext = recomposer

        viewModel.viewModelScope.launch(Dispatchers.Main) {
            tryShowHandle()
        }

        viewModel.viewModelScope.launch(Dispatchers.Main) {
            lsDisplayManager.displayPowerStates
                .map { it.displayStates[this@DrawerDelegate.display?.uniqueIdCompat] == true }
                .collect { isScreenOn ->
                    if (isScreenOn) {
                        tryShowHandle()
                    } else {
                        hideAll()
                    }
                }
        }

        created = true
    }

    override suspend fun onDestroy() {
        hideDrawer(false)
        hideHandle()

        super.onDestroy()

        invalidateInstance()
    }

    override fun isLocked(): Boolean {
        return prefManager.lockWidgetDrawer
    }

    private suspend fun hideAll() {
        hideDrawer(false)
        hideHandle()
    }

    private suspend fun tryShowHandle() {
        logUtils.debugLog("Trying to show handle on display ${this@DrawerDelegate.display?.uniqueIdCompat}", null)
        if (prefManager.drawerEnabled && prefManager.showDrawerHandle &&
            lsDisplayManager.displayPowerStates.value.displayStates[this@DrawerDelegate.display?.uniqueIdCompat] == true) {
            if (prefManager.showDrawerHandleOnlyWhenLocked && !globalState.wasOnKeyguard.value) {
                return
            }

            if (!handle.isAttachedToWindow && viewModel.handleAnimationState.value != AnimationState.ADDING) {
                wm?.safeAddView(handle, handleParams)
                handle.alpha = 0f
                handle.fadeIn(DrawerOrFrame.DRAWER)
                viewModel.handleAnimationState.value = AnimationState.IDLE
            }
        }
    }

    private suspend fun hideHandle() {
        withContext(Dispatchers.Main) {
            logUtils.debugLog("Trying to hide handle", null)
            if (!drawer.isAttachedToWindow) {
                lifecycleRegistry.safeCurrentState = Lifecycle.State.STARTED
            }

            if (handle.isAttachedToWindow && viewModel.handleAnimationState.value != AnimationState.REMOVING) {
                handle.fadeOut(DrawerOrFrame.DRAWER)
                viewModel.handleAnimationState.value = AnimationState.IDLE
                wm?.safeRemoveView(handle)
            }
        }
    }

    private suspend fun showDrawer(hideHandle: Boolean = true) {
        withContext(Dispatchers.Main) {
            logUtils.debugLog("Trying to show drawer", null)
            if (!drawer.isAttachedToWindow && viewModel.drawerAnimationState.value != AnimationState.ADDING) {
                eventManager.sendEvent(Event.DrawerAttachmentState(true))
                viewModel.drawerAnimationState.value = AnimationState.ADDING
                drawer.alpha = 0f
                wm?.safeAddView(drawer, params)
                if (hideHandle) {
                    hideHandle()
                }
            }
        }
    }

    override suspend fun updateWindow() {
        withContext(Dispatchers.Main) {
            logUtils.debugLog("Updating drawer window", null)
            params.apply {
                this@DrawerDelegate.display?.rotatedRealSize?.let { displaySize ->
                    width = displaySize.x
                    height = displaySize.y
                }
            }

            if (isAttached) {
                drawer.hideNavBarsForGestureExclusion()
                wm?.safeUpdateViewLayout(drawer, params)
            }
        }
    }

    private suspend fun hideDrawer(callListener: Boolean = true) {
        withContext(Dispatchers.Main) {
            logUtils.debugLog("Trying to hide drawer, $callListener", null)
            if (drawer.isAttachedToWindow && viewModel.drawerAnimationState.value != AnimationState.REMOVING) {
                viewModel.drawerAnimationState.value = AnimationState.REMOVING
                globalState.handlingClick.remove(ID)
                viewModel.currentEditingInterfaceId.value = RecyclerView.NO_POSITION

                drawer.fadeOut(DrawerOrFrame.DRAWER)
                wm?.safeRemoveView(drawer)
                viewModel.drawerAnimationState.value = AnimationState.IDLE
                if (callListener) eventManager.sendEvent(Event.DrawerHidden)
            }
        }
    }

    class State

    class DrawerViewModel(delegate: DrawerDelegate) :
        BaseViewModel<State, DrawerDelegate>(delegate) {
        val scrollingOpen = MutableStateFlow(false)
        val latestScrollInVelocity = MutableStateFlow(0f)
        val handleAnimationState = MutableStateFlow(AnimationState.IDLE)
        val drawerAnimationState = MutableStateFlow(AnimationState.IDLE)

        override val containerCornerRadiusKey: String? = null
        override val widgetCornerRadiusKey: String = PrefManager.KEY_DRAWER_WIDGET_CORNER_RADIUS
        override val ignoreWidgetTouchesKey: String? = null
        override val doubleTapTurnOffDisplayKey: String = PrefManager.KEY_DOUBLE_TAP_EMPTY_DRAWER_SPACE_TURN_OFF_DISPLAY
    }
}

enum class AnimationState {
    IDLE,
    ADDING,
    REMOVING;
}
