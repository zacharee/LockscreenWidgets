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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
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
import tk.zwander.common.compose.components.DrawerHandle
import tk.zwander.common.compose.util.createComposeViewHolder
import tk.zwander.common.compose.util.findAccessibility
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.BaseDelegate
import tk.zwander.common.util.DrawerOrFrame
import tk.zwander.common.util.Event
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.fadeIn
import tk.zwander.common.util.fadeOut
import tk.zwander.common.util.globalState
import tk.zwander.common.util.handler
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.mainHandler
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.lsDisplayManager
import tk.zwander.common.util.safeAddView
import tk.zwander.common.util.safeCurrentState
import tk.zwander.common.util.safeRemoveView
import tk.zwander.common.util.safeUpdateViewLayout
import tk.zwander.common.util.themedContext
import tk.zwander.lockscreenwidgets.R
import tk.zwander.widgetdrawer.activities.TaskerIsShowingDrawer
import tk.zwander.widgetdrawer.adapters.DrawerAdapter
import tk.zwander.widgetdrawer.compose.Drawer
import tk.zwander.widgetdrawer.views.DrawerRecycler
import kotlin.math.absoluteValue
import kotlin.math.sign

class DrawerDelegate private constructor(context: Context, displayId: String) :
    BaseDelegate<DrawerDelegate.State>(context, displayId) {
    companion object {
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

    override var state = State()
        set(value) {
            field = value
            lifecycleScope.launch(Dispatchers.Main) {
                updateWindow()
            }
        }

    override val params by lazy {
        WindowManager.LayoutParams().apply {
            val displaySize = display.rotatedRealSize
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = displaySize.x
            height = displaySize.y
            format = PixelFormat.RGBA_8888
            gravity = Gravity.TOP or Gravity.CENTER
        }
    }
    override val rootView: View
        get() = drawer
    override val recyclerView: RecyclerView
        get() = widgetGrid
    override var currentWidgets: List<WidgetData>
        get() = prefManager.drawerWidgets.toList()
        set(value) {
            prefManager.drawerWidgets = LinkedHashSet(value)
        }

    private val handleParams by lazy {
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = display.dpToPx(context.prefManager.drawerHandleWidth)
            height = display.dpToPx(context.prefManager.drawerHandleHeight)
            gravity = Gravity.TOP or context.prefManager.drawerHandleSide
            y = context.prefManager.drawerHandleYPosition
            format = PixelFormat.RGBA_8888
        }
    }

    private val widgetGrid by lazy {
        DrawerRecycler(themedContext)
    }

    private val drawer by lazy {
        viewModel.createComposeViewHolder {
            Drawer(
                widgetGrid = widgetGrid,
                modifier = Modifier.fillMaxSize(),
            )
        }.apply {
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    eventManager.sendEvent(Event.DrawerAttachmentState(false))
                }
            })
        }
    }
    private val handle by lazy {
        viewModel.createComposeViewHolder {
            DrawerHandle(
                params = handleParams,
                displayId = displayId,
                updateWindow = {
                    wm.safeUpdateViewLayout(it, handleParams)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    override val adapter by lazy {
        DrawerAdapter(context, rootView, { displayId }, viewModel) { widget, _ ->
            viewModel.itemToRemove.value = widget
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
        handler(PrefManager.KEY_DRAWER_WIDGETS) {
            if (!commonState.updatedForMoveOrRemove) {
                //Only run the update if it wasn't generated by a reorder event
                adapter.updateWidgets(currentWidgets.toList())
            } else {
                updateCommonState { it.copy(updatedForMoveOrRemove = false) }
            }
        }
        handler(PrefManager.KEY_DRAWER_COL_COUNT) {
            updateCounts()
        }
        handler(PrefManager.KEY_LOCK_WIDGET_DRAWER) {
            viewModel.currentEditingInterfacePosition.value = -1
        }
        handler(PrefManager.KEY_SHOW_DRAWER_HANDLE_ONLY_WHEN_LOCKED) {
            if (!prefManager.showDrawerHandleOnlyWhenLocked) {
                tryShowHandle()
            } else if (!globalState.wasOnKeyguard.value) {
                hideHandle()
            }
        }
    }

    override val gridLayoutManager = SpannedLayoutManager()

    override val viewModel = DrawerViewModel(this)

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
                adapter.updateViews()
                hideHandle()
            }

            Event.DrawerHidden -> {
                tryShowHandle()
            }

            Event.CloseSystemDialogs -> {
                hideDrawer()
            }

            is Event.DrawerAttachmentState -> {
                TaskerIsShowingDrawer::class.java.requestQuery(this)
                if (event.attached) {
                    if (!viewModel.scrollingOpen.value) {
                        mainHandler.postDelayed({
                            if (prefManager.drawerForceWidgetReload) {
                                adapter.updateViews()
                            }
                        }, 50)
                        widgetHost.startListening(this)

                        drawer.fadeIn(DrawerOrFrame.DRAWER)
                        eventManager.sendEvent(Event.DrawerShown)
                        viewModel.drawerAnimationState.value = AnimationState.IDLE
                    } else {
                        drawer.alpha = 1f
                    }

                    if (lifecycleRegistry.currentState < Lifecycle.State.CREATED) {
                        lifecycleRegistry.safeCurrentState = Lifecycle.State.CREATED
                    }
                    lifecycleRegistry.safeCurrentState = Lifecycle.State.RESUMED
                } else {
                    try {
                        widgetHost.stopListening(this)
                    } catch (_: NullPointerException) {
                        //AppWidgetServiceImpl$ProviderId NPE
                    }

                    if (!handle.isAttachedToWindow) {
                        lifecycleRegistry.safeCurrentState = Lifecycle.State.STARTED
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
                val metThreshold = distanceFromEdge > display.dpToPx(100f) && velocityMatches

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
                            eventManager.sendEvent(Event.DrawerAttachmentState(true))
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
        return if (!commonState.isItemHighlighted && !globalState.itemIsActive.value) {
            if (trigger && prefManager.requestUnlockDrawer && prefManager.drawerDirectlyCheckForActivity) {
                DismissOrUnlockActivity.launch(this)
                eventManager.sendEvent(Event.CloseDrawer)
            } else {
                if (prefManager.requestUnlockDrawer) {
                    globalState.handlingClick.value =
                        globalState.handlingClick.value.toMutableMap().also {
                            it[-2] = Unit
                        }
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

        handle.setViewTreeLifecycleOwner(this)
        handle.setViewTreeSavedStateRegistryOwner(this)

        lifecycleScope.launch {
            tryShowHandle()
        }

        gridLayoutManager.customHeight =
            resources.getDimensionPixelSize(R.dimen.drawer_row_height).toDouble()

        lifecycleScope.launch(Dispatchers.Main) {
            lsDisplayManager.displayPowerStates
                .map { it[display.uniqueIdCompat] == true }
                .collect { isScreenOn ->
                    if (isScreenOn) {
                        tryShowHandle()
                    } else {
                        hideAll()
                    }
                }
        }
    }

    override suspend fun onDestroy() {
        hideDrawer(false)
        hideHandle()

        super.onDestroy()

        invalidateInstance()
    }

    override fun onItemSelected(selected: Boolean, highlighted: Boolean) {
        super.onItemSelected(selected, highlighted)
        viewModel.selectedItem.value = selected
        globalState.handlingClick.value = globalState.handlingClick.value.toMutableMap().apply {
            remove(-2)
        }
    }

    override fun isLocked(): Boolean {
        return prefManager.lockWidgetDrawer
    }

    private suspend fun hideAll() {
        hideDrawer(false)
        hideHandle()
    }

    private suspend fun tryShowHandle() {
        if (prefManager.drawerEnabled && prefManager.showDrawerHandle && lsDisplayManager.displayPowerStates.value[display.uniqueIdCompat] == true) {
            if (prefManager.showDrawerHandleOnlyWhenLocked && !globalState.wasOnKeyguard.value) {
                return
            }

            if (lifecycleRegistry.currentState < Lifecycle.State.CREATED) {
                lifecycleRegistry.safeCurrentState = Lifecycle.State.CREATED
            }
            lifecycleRegistry.safeCurrentState = Lifecycle.State.RESUMED

            if (!handle.isAttachedToWindow && viewModel.handleAnimationState.value != AnimationState.ADDING) {
                wm.safeAddView(handle, handleParams)
                handle.alpha = 0f
                handle.fadeIn(DrawerOrFrame.DRAWER)
                viewModel.handleAnimationState.value = AnimationState.IDLE
            }
        }
    }

    private suspend fun hideHandle() {
        withContext(Dispatchers.Main) {
            if (!drawer.isAttachedToWindow) {
                lifecycleRegistry.safeCurrentState = Lifecycle.State.STARTED
            }

            if (handle.isAttachedToWindow && viewModel.handleAnimationState.value != AnimationState.REMOVING) {
                handle.fadeOut(DrawerOrFrame.DRAWER)
                viewModel.handleAnimationState.value = AnimationState.IDLE
                wm.safeRemoveView(handle)
            }
        }
    }

    private suspend fun showDrawer(wm: WindowManager = this.wm, hideHandle: Boolean = true) {
        withContext(Dispatchers.Main) {
            if (!drawer.isAttachedToWindow && viewModel.drawerAnimationState.value != AnimationState.ADDING) {
                if (hideHandle) {
                    eventManager.sendEvent(Event.DrawerAttachmentState(true))
                }
                viewModel.drawerAnimationState.value = AnimationState.ADDING
                drawer.alpha = 0f
                wm.safeAddView(drawer, params)
                if (hideHandle) {
                    hideHandle()
                }
            }
        }
    }

    override suspend fun updateWindow() {
        withContext(Dispatchers.Main) {
            params.apply {
                val displaySize = display.rotatedRealSize
                width = displaySize.x
                height = displaySize.y
            }

            if (isAttached) {
                wm.safeUpdateViewLayout(drawer, params)
            }
        }
    }

    private suspend fun hideDrawer(callListener: Boolean = true) {
        withContext(Dispatchers.Main) {
            if (drawer.isAttachedToWindow && viewModel.drawerAnimationState.value != AnimationState.REMOVING) {
                viewModel.drawerAnimationState.value = AnimationState.REMOVING
                globalState.handlingClick.value =
                    globalState.handlingClick.value.toMutableMap().also { it.remove(-2) }
                viewModel.currentEditingInterfacePosition.value = -1

                drawer.fadeOut(DrawerOrFrame.DRAWER)
                wm.safeRemoveView(drawer)
                viewModel.drawerAnimationState.value = AnimationState.IDLE
                if (callListener) eventManager.sendEvent(Event.DrawerHidden)
            }
        }
    }

    override fun retrieveCounts(): Pair<Int?, Int?> {
        return null to prefManager.drawerColCount
    }

    inner class SpannedLayoutManager : LayoutManager(
        this@DrawerDelegate,
        RecyclerView.VERTICAL,
        1,
        prefManager.drawerColCount,
    ) {
        override fun canScrollVertically(): Boolean {
            return (viewModel.currentEditingInterfacePosition.value == -1 || commonState.isHoldingItem) && super.canScrollVertically()
        }
    }

    class State

    class DrawerViewModel(delegate: DrawerDelegate) :
        BaseViewModel<State, DrawerDelegate>(delegate) {
        val selectedItem = MutableStateFlow(false)
        val scrollingOpen = MutableStateFlow(false)
        val latestScrollInVelocity = MutableStateFlow(0f)
        val handleAnimationState = MutableStateFlow(AnimationState.IDLE)
        val drawerAnimationState = MutableStateFlow(AnimationState.IDLE)

        override val containerCornerRadiusKey: String? = null
        override val widgetCornerRadiusKey: String = PrefManager.KEY_DRAWER_WIDGET_CORNER_RADIUS
    }
}

enum class AnimationState {
    IDLE,
    ADDING,
    REMOVING;
}
