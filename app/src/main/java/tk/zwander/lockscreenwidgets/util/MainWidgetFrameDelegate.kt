package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.compose.util.createComposeViewHolder
import tk.zwander.common.compose.util.findAccessibility
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.BaseDelegate
import tk.zwander.common.util.DrawerOrFrame
import tk.zwander.common.util.Event
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.GlobalState
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.ISnappyLayoutManager
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.fadeAndScaleIn
import tk.zwander.common.util.fadeAndScaleOut
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.globalState
import tk.zwander.common.util.handler
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.mainHandler
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.safeAddView
import tk.zwander.common.util.safeRemoveView
import tk.zwander.common.util.safeUpdateViewLayout
import tk.zwander.common.util.themedContext
import tk.zwander.common.util.wallpaperUtils
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.compose.WidgetFrameLayout
import tk.zwander.lockscreenwidgets.data.Mode
import tk.zwander.lockscreenwidgets.databinding.WidgetGridHolderBinding
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Handle most of the logic involving the widget frame.
 */
open class MainWidgetFrameDelegate protected constructor(
    context: Context,
    protected val id: Int = -1,
    displayId: String,
) : BaseDelegate<MainWidgetFrameDelegate.State>(context, displayId) {
    companion object {
        private val instance = MutableStateFlow<MainWidgetFrameDelegate?>(null)

        val readOnlyInstance = instance.asStateFlow()

        @Synchronized
        fun peekInstance(context: Context): MainWidgetFrameDelegate? {
            if (instance.value == null) {
                context.logUtils.debugLog("Accessibility isn't running yet")

                return null
            }

            return instance.value
        }

        @Synchronized
        fun getInstance(context: Context, displayId: String): MainWidgetFrameDelegate {
            return instance.value ?: run {
                val accessibilityContext = context.findAccessibility()

                if (accessibilityContext == null) {
                    throw IllegalStateException("Delegate can only be initialized by Accessibility Service!")
                } else {
                    MainWidgetFrameDelegate(accessibilityContext, displayId = displayId).also {
                        instance.value = it
                    }
                }
            }
        }

        @Synchronized
        fun invalidateInstance() {
            instance.value = null
        }
    }

    override var commonState: BaseState = BaseState()

    override var state: State = State()
        set(newState) {
            var actualNewState = newState
            val oldState = field

            if (actualNewState.isTempHide != oldState.isTempHide && actualNewState.isTempHide) {
                actualNewState = actualNewState.copy(isPreview = false)
            }

            if (actualNewState.screenOrientation != oldState.screenOrientation) {
                actualNewState = actualNewState.copy(isPendingOrientationStateChange = true, isPreview = false)
            }

            // ------------ //

            field = actualNewState

            if (actualNewState.isPreview != oldState.isPreview) {
                scope.launch(Dispatchers.Main) {
                    if (actualNewState.isPreview) {
                        if (canShow()) {
                            addWindow()
                        }
                    } else {
                        if (!canShow()) {
                            removeWindow()
                        }
                    }
                }
            }

            if (actualNewState.selectionPreviewRequestCode != oldState.selectionPreviewRequestCode) {
                scope.launch(Dispatchers.Main) {
                    if (actualNewState.selectionPreviewRequestCode != null) {
                        if (canShow()) {
                            addWindow()
                            viewModel.isSelectingFrame.value = true
                        }
                    } else {
                        if (!canShow()) {
                            removeWindow()
                            viewModel.isSelectingFrame.value = false
                        }
                    }
                }
            }
        }

    private val saveMode: FrameSizeAndPosition.FrameType
        get() {
            val isLandscape = prefManager.separateFrameLayoutForLandscape &&
                    (state.screenOrientation == Surface.ROTATION_90 ||
                        state.screenOrientation == Surface.ROTATION_270)
            val isMainFrame = id == -1

            return when {
                globalState.notificationsPanelFullyExpanded.value && framePrefs.showInNotificationShade -> {
                    if (kgm.isKeyguardLocked && framePrefs.separateLockNCPosition) {
                        if (isMainFrame) {
                            FrameSizeAndPosition.FrameType.LockNotification.select(!isLandscape)
                        } else {
                            FrameSizeAndPosition.FrameType.SecondaryLockNotification.select(!isLandscape, id)
                        }
                    } else {
                        if (isMainFrame) {
                            FrameSizeAndPosition.FrameType.NotificationNormal.select(!isLandscape)
                        } else {
                            FrameSizeAndPosition.FrameType.SecondaryNotification.select(!isLandscape, id)
                        }
                    }
                }
                else -> {
                    if (isMainFrame) {
                        FrameSizeAndPosition.FrameType.LockNormal.select(!isLandscape)
                    } else {
                        FrameSizeAndPosition.FrameType.SecondaryLockscreen.select(!isLandscape, id)
                    }
                }
            }
        }

    //The size, position, and such of the widget frame on the lock screen.
    final override val params by lazy {
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

            frameSizeAndPosition.getSizeForType(saveMode, display).let { size ->
                width = display.dpToPx(size.x)
                height = display.dpToPx(size.y)
            }
            frameSizeAndPosition.getPositionForType(saveMode, display).let { pos ->
                x = pos.x
                y = pos.y
            }

            gravity = Gravity.CENTER

            flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.RGBA_8888
        }
    }

    private val widgetGrid by lazy {
        WidgetGridHolderBinding.inflate(
            LayoutInflater.from(themedContext),
        ).root
    }

    private val frame by lazy {
        viewModel.createComposeViewHolder {
            WidgetFrameLayout(
                widgetGrid = widgetGrid,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    override val gridLayoutManager = SpannedLayoutManager()

    override val rootView: View
        get() = frame
    override val recyclerView: RecyclerView
        get() = widgetGrid
    override var currentWidgets: List<WidgetData>
        get() = FramePrefs.getWidgetsForFrame(this, id).toList()
        set(value) {
            FramePrefs.setWidgetsForFrame(this, id, value)
        }

    override val viewModel = WidgetFrameViewModel(this)

    override val adapter by lazy {
        WidgetFrameAdapter(
            frameId = id,
            context = context,
            rootView = rootView,
            onRemoveCallback = { item, _ ->
                viewModel.itemToRemove.value = item
            },
            displayId = displayId,
            saveTypeGetter = { saveMode },
            viewModel = viewModel,
        )
    }

    protected val framePrefs = FrameSpecificPreferences(context = context, frameId = id)

    override val prefsHandler = HandlerRegistry {
        handler(FramePrefs.generateCurrentWidgetsKey(id)) {
            //Make sure the adapter knows of any changes to the widget list
            if (!commonState.updatedForMoveOrRemove) {
                //Only run the update if it wasn't generated by a reorder event
                adapter.updateWidgets(currentWidgets.toList())

                mainHandler.postDelayed({
                    scrollToStoredPosition(true)
                }, 50)
            } else {
                updateCommonState { it.copy(updatedForMoveOrRemove = false) }
            }
        }
        handler(
            FramePrefs.generatePrefKey(FramePrefs.KEY_FRAME_ROW_COUNT, id),
            FramePrefs.generatePrefKey(FramePrefs.KEY_FRAME_COL_COUNT, id),
        ) {
            updateCounts()
            adapter.updateViews()
        }
        handler(framePrefs.keyFor(PrefManager.KEY_FRAME_MASKED_MODE)) {
            updateWallpaperLayerIfNeeded()
        }
        handler(framePrefs.keyFor(PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER)) {
            updateState { it.copy(isPendingNotificationStateChange = true) }
        }
        handler(
            PrefManager.KEY_LOCK_WIDGET_FRAME,
        ) {
            viewModel.currentEditingInterfacePosition.value = -1
        }
        handler(PrefManager.KEY_WIDGET_FRAME_ENABLED) {
            scope.launch {
                updateWindowState()
            }
        }
        handler(PrefManager.KEY_CAN_SHOW_FRAME_FROM_TASKER, PrefManager.KEY_FORCE_SHOW_FRAME) {
            scope.launch {
                updateWindowState()
            }
        }
    }

    private val showWallpaperLayerCondition: Boolean
        get() = !state.isPreview &&
                state.selectionPreviewRequestCode == null &&
                framePrefs.maskedMode &&
                (!globalState.notificationsPanelFullyExpanded.value || !framePrefs.showInNotificationShade) &&
                (!globalState.showingNotificationsPanel.value || framePrefs.hideOnNotificationShade)

    override suspend fun onEvent(event: Event) {
        super.onEvent(event)

        when (event) {
            is Event.FrameMoveFinished -> {
                if (event.frameId == id) {
                    updateWallpaperLayerIfNeeded()
                }
            }
            is Event.TempHide -> {
                if (event.frameId == id) {
                    updateState { it.copy(isTempHide = true) }
                    updateWindowState()
                }
            }
            Event.NightModeUpdate -> {
                adapter.updateViews()
            }
            is Event.CenterFrameHorizontally -> {
                if (event.frameId == id) {
                    frameSizeAndPosition.setPositionForType(
                        saveMode,
                        Point(
                            0,
                            frameSizeAndPosition.getPositionForType(saveMode, display).y
                        )
                    )
                    updateWindow()
                }
            }
            is Event.CenterFrameVertically -> {
                if (event.frameId == id) {
                    frameSizeAndPosition.setPositionForType(
                        saveMode,
                        Point(
                            frameSizeAndPosition.getPositionForType(saveMode, display).x,
                            0
                        )
                    )
                    updateWindow()
                }
            }
            is Event.FrameResized -> {
                if (event.frameId == id) {
                    when (event.which) {
                        Event.FrameResized.Side.LEFT -> {
                            params.width -= event.velocity
                            params.x += (event.velocity / 2)
                        }
                        Event.FrameResized.Side.TOP -> {
                            params.height -= event.velocity
                            params.y += (event.velocity / 2)
                        }
                        Event.FrameResized.Side.RIGHT -> {
                            params.width += event.velocity
                            params.x += (event.velocity / 2)
                        }
                        Event.FrameResized.Side.BOTTOM -> {
                            params.height += event.velocity
                            params.y += (event.velocity / 2)
                        }
                    }

                    updateOverlay()

                    frameSizeAndPosition.setPositionForType(
                        saveMode,
                        Point(params.x, params.y)
                    )
                    frameSizeAndPosition.setSizeForType(
                        saveMode,
                        PointF(display.pxToDp(params.width), display.pxToDp(params.height))
                    )

                    if (event.isUp) {
                        eventManager.sendEvent(Event.FrameResizeFinished(id))
                        updateWallpaperLayerIfNeeded()
                        adapter.updateViews()
                    }
                }
            }
            //We only really want to be listening to widget changes
            //while the frame is on-screen. Otherwise, we're wasting battery.
            is Event.FrameAttachmentState -> {
                if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) {
                    return
                }

                if (event.frameId == id) {
                    try {
                        if (event.attached) {
                            if (lifecycleRegistry.currentState < Lifecycle.State.CREATED) {
                                lifecycleRegistry.currentState = Lifecycle.State.CREATED
                            }
                            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                            widgetHost.startListening(this)
                            updateWallpaperLayerIfNeeded()
                            //Even with the startListening() call above,
                            //it doesn't seem like pending updates always get
                            //dispatched. Rebinding all the widgets forces
                            //them to update.
                            if (prefManager.frameForceWidgetReload) {
                                adapter.updateViews()
                            }
                            scrollToStoredPosition(false)
                        } else {
                            widgetHost.stopListening(this)
                            lifecycleRegistry.currentState = Lifecycle.State.STARTED
                        }
                    } catch (_: NullPointerException) {
                        //The stupid "Attempt to read from field 'com.android.server.appwidget.AppWidgetServiceImpl$ProviderId
                        //com.android.server.appwidget.AppWidgetServiceImpl$Provider.id' on a null object reference"
                        //Exception is thrown on stopListening() as well for some reason.
                    }
                }
            }
            is Event.FrameMoved -> {
                if (event.frameId == id) {
                    params.x += event.velX.toInt()
                    params.y += event.velY.toInt()

                    updateOverlay()

                    frameSizeAndPosition.setPositionForType(
                        saveMode,
                        Point(params.x, params.y)
                    )
                }
            }
            is Event.FrameIntercept -> {
                if (event.frameId == id) {
                    forceWakelock(event.down)
                }
            }
            Event.ScreenOff -> {
                //If the device has some sort of AOD or ambient display, by the time we receive
                //an accessibility event and see that the display is off, it's usually too late
                //and the current screen content has "frozen," causing the widget frame to show
                //where it shouldn't. ACTION_SCREEN_OFF is called early enough that we can remove
                //the frame before it's frozen in place.
                forceWakelock(false)
                updateStateAndWindowState(
                    transform = {
                        it.copy(
                            isTempHide = false,
                        )
                    },
                )
            }
            is Event.PreviewFrames -> {
                if (event.show == Event.PreviewFrames.ShowMode.SHOW_FOR_SELECTION && prefManager.currentSecondaryFramesWithStringDisplay.isEmpty()) {
                    eventManager.sendEvent(Event.LaunchAddWidget(id))
                } else {
                    if (event.includeMainFrame || id != -1) {
                        updateState {
                            val isPreview = event.show == Event.PreviewFrames.ShowMode.SHOW ||
                                    (event.show == Event.PreviewFrames.ShowMode.TOGGLE && !it.isPreview)
                            it.copy(
                                isPreview = isPreview,
                                selectionPreviewRequestCode = event.requestCode.takeIf {
                                    event.show == Event.PreviewFrames.ShowMode.SHOW_FOR_SELECTION
                                },
                                isTempHide = if (isPreview) false else it.isTempHide,
                            )
                        }
                    }
                }
            }
            is Event.FrameSelected -> {
                if (event.frameId == null) {
                    updateState { it.copy(selectionPreviewRequestCode = null) }
                }
            }
            else -> {}
        }
    }

    override fun onWidgetClick(trigger: Boolean): Boolean {
        val ignoreTouches = framePrefs.ignoreWidgetTouches || commonState.isItemHighlighted

        if (!ignoreTouches) {
            if (trigger && prefManager.requestUnlock && prefManager.frameDirectlyCheckForActivity) {
                DismissOrUnlockActivity.launch(this)
            } else {
                if (prefManager.requestUnlock) {
                    globalState.handlingClick.value = globalState.handlingClick.value.toMutableMap().also {
                        it[id] = Unit
                    }
                }
            }
        }

        return !ignoreTouches
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate() {
        super.onCreate()

        //Scroll to the stored page, making sure to catch a potential
        //out-of-bounds error.
        try {
            scrollToStoredPosition(false)
        } catch (_: Exception) {}

        scope.launch(Dispatchers.Main) {
            globalState.isScreenOn.collect { isScreenOn ->
                if (!isScreenOn) {
                    globalState.notificationsPanelFullyExpanded.value = false
                    updateState { it.copy(isPreview = false, isTempHide = false) }
                }

                updateWindowState()
            }
        }

        scope.launch(Dispatchers.Main) {
            globalState.notificationsPanelFullyExpanded.collect {
                updateState { it.copy(isPendingNotificationStateChange = true, isPreview = false) }
            }
        }

        scope.launch(Dispatchers.Main) {
            globalState.notificationCount.collect {
                //Receive updates from our notification listener service on how many
                //notifications are currently shown to the user. This count excludes
                //notifications not visible on the lock screen.
                //If the notification count is > 0, and the user has the option enabled,
                //make sure to hide the widget frame.
                updateWindowState()
            }
        }
    }

    override fun onDestroy() {
        if (lifecycleRegistry.currentState >= Lifecycle.State.INITIALIZED) {
            removeWindow()
        }

        super.onDestroy()

        if (id == -1) {
            invalidateInstance()
        }
    }

    override fun isLocked(): Boolean {
        return prefManager.lockWidgetFrame
    }

    suspend fun updateStateAndWindowState(
        updateAccessibility: Boolean = false,
        transform: (State) -> State = { it },
        commonTransform: (BaseState) -> BaseState = { it },
    ) {
        updateState(transform)
        updateCommonState(commonTransform)
        updateWindowState(updateAccessibility)
    }

    override fun retrieveCounts(): Pair<Int, Int> {
        return FramePrefs.getGridSizeForFrame(this, id)
    }

    override fun widgetRemovalConfirmed(event: Event.RemoveWidgetConfirmed, position: Int) {
        if (event.remove) {
            widgetGrid.post {
                val pos = when (val pos = gridLayoutManager.firstVisiblePosition) {
                    RecyclerView.NO_POSITION -> (position - 1).coerceAtLeast(0)
                    else -> pos
                }

                widgetGrid.scrollToPosition(pos)
            }
        }
    }

    private fun addWindow() {
        logUtils.debugLog("Adding overlay")

        if (!frame.isAttachedToWindow) {
            updateWindow()
        }

        mainHandler.post {
            logUtils.debugLog("Trying to add overlay ${viewModel.animationState.value}", null)

            if (!frame.isAttachedToWindow && viewModel.animationState.value != AnimationState.STATE_ADDING) {
                logUtils.debugLog("Adding overlay", null)

                viewModel.animationState.value = AnimationState.STATE_ADDING

                if (!wm.safeAddView(frame, params)) {
                    viewModel.animationState.value = AnimationState.STATE_IDLE
                } else {
                    frame.fadeAndScaleIn(DrawerOrFrame.FRAME) {
                        viewModel.animationState.value = AnimationState.STATE_IDLE
                        eventManager.sendEvent(Event.FrameAttachmentState(id, true))
                    }
                }
            }
        }
    }

    private fun removeWindow() {
        if (isAttached) {
            logUtils.debugLog("Removing overlay")
        }

        viewModel.currentEditingInterfacePosition.value = -1

        globalState.handlingClick.value = globalState.handlingClick.value.toMutableMap().also {
            it.remove(id)
        }
        forceWakelock(on = false, updateOverlay = false)

        mainHandler.post {
            logUtils.debugLog("Trying to remove overlay ${viewModel.animationState.value}", null)

            if (frame.isAttachedToWindow && viewModel.animationState.value != AnimationState.STATE_REMOVING) {
                viewModel.animationState.value = AnimationState.STATE_REMOVING

                logUtils.debugLog("Pre-animation removal", null)

                frame.fadeAndScaleOut(DrawerOrFrame.FRAME) {
                    logUtils.debugLog("Post-animation removal", null)

                    mainHandler.postDelayed({
                        logUtils.debugLog("Posted removal", null)

                        if (frame.isAttachedToWindow) {
                            wm.safeRemoveView(frame)
                        }
                        viewModel.animationState.value = AnimationState.STATE_IDLE
                    }, 50)
                }
            } else if (!frame.isAttachedToWindow) {
                wm.safeRemoveView(frame, false)

                viewModel.animationState.value = AnimationState.STATE_IDLE
            }
        }
    }

    fun setNewDebugIdItems(items: List<String>) {
        viewModel.debugIdItems.value = items.toSet()
    }

    suspend fun updateWindowState(updateAccessibility: Boolean = false) {
        if (canShow()) {
            if (updateAccessibility) updateAccessibilityPass()
            addWindow()
        } else {
            removeWindow()
            if (updateAccessibility) updateAccessibilityPass()
        }
    }

    /**
     * Check if the widget frame should show onscreen. There are quite a few conditions for this.
     * This method attempts to check those conditions in increasing order of intensiveness (check simple
     * members first, then try SharedPreferences, then use IPC methods).
     *
     * The widget frame can only show if ALL of the following conditions are met:
     *
     * =======
     * - [saveMode] is [Mode.PREVIEW]
     * =======
     * OR
     * =======
     * - [GlobalState.isScreenOn] is true
     * - [State.isTempHide] is false
     * - [GlobalState.notificationsPanelFullyExpanded] is true AND [FrameSpecificPreferences.showInNotificationShade] is true
     * - [GlobalState.hideForPresentIds] is false
     * - [GlobalState.hideForNonPresentIds] is false
     * - [PrefManager.widgetFrameEnabled] is true
     * - [PrefManager.hideInLandscape] is false OR [State.screenOrientation] represents a portrait rotation
     * =======
     * OR
     * =======
     * - [GlobalState.wasOnKeyguard] is true
     * - [GlobalState.isScreenOn] is true (i.e. the display is properly on: not in Doze or on the AOD)
     * - [State.isTempHide] is false
     * - [FrameSpecificPreferences.showOnMainLockScreen] is true OR [FrameSpecificPreferences.showInNotificationShade] is false
     * - [FrameSpecificPreferences.hideOnFaceWidgets] is false OR [GlobalState.isOnFaceWidgets] is false
     * - [GlobalState.currentAppLayer] is less than 0 (i.e. doesn't exist)
     * - [GlobalState.isOnEdgePanel] is false OR [FrameSpecificPreferences.hideOnEdgePanel] is false
     * - [GlobalState.isOnScreenOffMemo] is false
     * - [GlobalState.onMainLockScreen] is true OR [GlobalState.showingNotificationsPanel] is true OR [FrameSpecificPreferences.hideOnSecurityPage] is false
     * - [GlobalState.showingNotificationsPanel] is false OR [FrameSpecificPreferences.hideOnNotificationShade] is false (OR [GlobalState.notificationsPanelFullyExpanded] is true AND [FrameSpecificPreferences.showInNotificationShade] is true
     * - [GlobalState.notificationCount] is 0 (i.e. no notifications shown on lock screen, not necessarily no notifications at all) OR [FrameSpecificPreferences.hideOnNotifications] is false
     * - [GlobalState.hideForPresentIds] is false OR [PrefManager.presentIds] is empty
     * - [GlobalState.hideForNonPresentIds] is false OR [PrefManager.nonPresentIds] is empty
     * - [PrefManager.hideInLandscape] is false OR [State.screenOrientation] represents a portrait rotation
     * - [PrefManager.widgetFrameEnabled] is true (i.e. the widget frame is actually enabled)
     * =======
     */
    private suspend fun canShow(): Boolean {
        fun forPreview(): Boolean {
            return (state.isPreview || state.selectionPreviewRequestCode != null) && !state.isTempHide
        }

        fun forCommon(): Boolean {
            return globalState.isScreenOn.value
                    && !state.isTempHide
                    && !globalState.hideForPresentIds.value
                    && !globalState.hideForNonPresentIds.value
                    && prefManager.widgetFrameEnabled
                    && (!prefManager.hideInLandscape || state.screenOrientation == Surface.ROTATION_0 || state.screenOrientation == Surface.ROTATION_180)
                    && prefManager.canShowFrameFromTasker
                    && (!framePrefs.hideWhenKeyboardShown || !globalState.showingKeyboard.value)
        }

        fun forSecondaryDisplay(): Boolean {
            return display.displayId != Display.DEFAULT_DISPLAY && forCommon()
        }

        fun forNotificationCenter(): Boolean {
            return (globalState.notificationsPanelFullyExpanded.value && framePrefs.showInNotificationShade)
                    && forCommon()
        }

        fun forLockscreen(): Boolean {
            return globalState.wasOnKeyguard.value
                    && (framePrefs.showOnMainLockScreen || !framePrefs.showInNotificationShade)
                    && (!framePrefs.hideOnFaceWidgets || !globalState.isOnFaceWidgets.value)
                    && (globalState.currentAppLayer.value < 0 && globalState.currentAppPackage.value == null)
                    && (!globalState.isOnEdgePanel.value || !framePrefs.hideOnEdgePanel)
                    && !globalState.isOnScreenOffMemo.value
                    && (globalState.onMainLockScreen.value || globalState.showingNotificationsPanel.value || !framePrefs.hideOnSecurityPage)
                    && (!globalState.showingNotificationsPanel.value || !framePrefs.hideOnNotificationShade)
                    && (globalState.notificationCount.value == 0 || !framePrefs.hideOnNotifications)
                    && !globalState.hidingForPresentApp.value
                    && forCommon()
        }

        fun forced(): Boolean {
            return prefManager.widgetFrameEnabled && prefManager.forceShowFrame
        }

        return withContext(Dispatchers.IO) {
            (forced() || forSecondaryDisplay() || forPreview() || forNotificationCenter() || forLockscreen()).also {
                logUtils.debugLog(
                    "canShow $id: $it\n" +
                            "state: $state\n " +
                            "showOnMainLockScreen: ${framePrefs.showOnMainLockScreen}\n" +
                            "widgetFrameEnabled: ${prefManager.widgetFrameEnabled}\n" +
                            "hideOnSecurityPage: ${framePrefs.hideOnSecurityPage}\n" +
                            "hideOnNotifications: ${framePrefs.hideOnNotifications}\n" +
                            "hideOnNotificationShade: ${framePrefs.hideOnNotificationShade}\n" +
                            "presentIds: ${prefManager.presentIds}\n" +
                            "nonPresentIds: ${prefManager.nonPresentIds}\n" +
                            "hideInLandscape: ${prefManager.hideInLandscape}\n" +
                            "showInNotificationCenter: ${framePrefs.showInNotificationShade}\n" +
                            "hideOnEdgePanel: ${framePrefs.hideOnEdgePanel}\n" +
                            "hidingForPresentApp: ${globalState.hidingForPresentApp.value}\n" +
                            "canShowFrameFromTasker: ${prefManager.canShowFrameFromTasker}\n" +
                            "forceShowFrame: ${prefManager.forceShowFrame}\n" +
                            "hideOnFaceWidgets: ${framePrefs.hideOnFaceWidgets}\n" +
                            "hideWhenKeyboardShown: ${framePrefs.hideWhenKeyboardShown}\n",
                )
            }
        }
    }

    /**
     * Compute and draw the appropriate portion of the wallpaper as the widget background,
     * if masked mode is enabled.
     *
     * TODO: this doesn't work properly on a lot of devices. It seems to be something to do with the scale.
     * TODO: I don't know enough about [Matrix]es to fix it.
     *
     * TODO: There are also a lot of limitations related to wallpaper offsets. It doesn't seem to be
     * TODO: possible to retrieve those offsets unless you're the active wallpaper, so this method
     * TODO: just assumes that a wallpaper [Bitmap] that's larger than the screen is centered,
     * TODO: which isn't always true.
     *
     * TODO: You can only specifically retrieve the lock screen wallpaper on Nougat and up.
     */
    private fun updateWallpaperLayerIfNeeded() {
        val showWallpaperLayer = showWallpaperLayerCondition

        logUtils.debugLog("updateWallpaperLayerIfNeeded() called $showWallpaperLayer")

        val wallpaperInfo = if (showWallpaperLayer) {
            logUtils.debugLog("Trying to retrieve wallpaper", null)

            try {
                val drawable = wallpaperUtils.wallpaperDrawable

                logUtils.debugLog("Retrieved wallpaper: $drawable", null)

                drawable?.mutate()?.let {
                    logUtils.debugLog("Setting wallpaper drawable.", null)

                    val realSize = display.realSize
                    val loc = frame.locationOnScreen ?: intArrayOf(0, 0)

                    val dWidth: Int = it.intrinsicWidth
                    val dHeight: Int = it.intrinsicHeight

                    val wallpaperAdjustmentX = (dWidth - realSize.x) / 2f
                    val wallpaperAdjustmentY = (dHeight - realSize.y) / 2f

                    val dx = (-loc[0].toFloat() - wallpaperAdjustmentX)
                    //TODO: a bunch of skins don't like this
                    val dy = (-loc[1].toFloat() - wallpaperAdjustmentY)

                    WallpaperInfo(
                        drawable = it,
                        dx = dx,
                        dy = dy,
                    )
                }
            } catch (e: Exception) {
                logUtils.normalLog("Error setting wallpaper", e)
                null
            }
        } else {
            logUtils.debugLog("Removing wallpaper", null)
            null
        }

        viewModel.wallpaperInfo.value = wallpaperInfo
    }

    /**
     * This is called by the Accessibility Service on an event,
     * after all other logic has finished (e.g. frame removal/addition
     * calls). Use this method for updating things that are conditional
     * upon the frame's state after an event.
     */
    private fun updateAccessibilityPass() {
        if (viewModel.animationState.value == AnimationState.STATE_IDLE) {
            if (state.isPendingNotificationStateChange || state.isPendingOrientationStateChange) {
                updateWindow()
                updateState {
                    it.copy(
                        isPendingNotificationStateChange = false,
                        isPendingOrientationStateChange = false,
                    )
                }
            }
        }
    }

    /**
     * Force the display to remain on, or remove that force.
     *
     * @param wm the WindowManager to use.
     * @param on whether to add or remove the force flag.
     */
    private fun forceWakelock(on: Boolean, updateOverlay: Boolean = true) {
        if (on) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }

        if (updateOverlay) {
            updateOverlay()
        }
    }

    private fun updateOverlay() {
        wm.safeUpdateViewLayout(frame, params)
    }

    /**
     * Update the frame's params for its current state (normal
     * or in expanded notification center).
     */
    override fun updateWindow() {
        logUtils.debugLog("Checking if params need to be updated")

        logUtils.debugLog("Possibly updating params with display size ${display.realSize}", null)

        val (newX, newY) = frameSizeAndPosition.getPositionForType(saveMode, display)
        val (newW, newH) = frameSizeAndPosition.getSizeForType(saveMode, display).run {
            Point(display.dpToPx(x), display.dpToPx(y))
        }

        var changed = false

        if (params.x != newX) {
            logUtils.debugLog("x changed", null)

            changed = true
            params.x = newX
        }

        if (params.y != newY) {
            logUtils.debugLog("y changed", null)

            changed = true
            params.y = newY
        }

        if (params.width != newW) {
            logUtils.debugLog("w changed", null)

            changed = true
            params.width = newW
        }

        if (params.height != newH) {
            logUtils.debugLog("h changed", null)

            changed = true
            params.height = newH
        }

        if (changed) {
            logUtils.debugLog("Updating params", null)

            updateOverlay()
            mainHandler.post {
                updateWallpaperLayerIfNeeded()
                adapter.updateViews()
                scrollToStoredPosition(true)
            }
        }
    }

    private fun scrollToStoredPosition(override: Boolean = false) {
        gridLayoutManager.scrollToPosition(if (override || prefManager.rememberFramePosition) prefManager.currentPage else 0)
    }

    //Parts based on https://stackoverflow.com/a/26445064/5496177
    inner class SpannedLayoutManager : LayoutManager(
        this@MainWidgetFrameDelegate,
        RecyclerView.HORIZONTAL,
        FramePrefs.getRowCountForFrame(this@MainWidgetFrameDelegate, id),
        FramePrefs.getColCountForFrame(this@MainWidgetFrameDelegate, id),
    ), ISnappyLayoutManager {
        override fun canScrollHorizontally(): Boolean {
            return (viewModel.currentEditingInterfacePosition.value == -1 || commonState.isHoldingItem) && super.canScrollHorizontally()
        }

        override fun getFixScrollPos(velocityX: Int, velocityY: Int): Int {
            return getPositionForVelocity(-velocityX, -velocityY)
        }

        override fun getPositionForVelocity(velocityX: Int, velocityY: Int): Int {
            if (childCount == 0) return 0

            return if (velocityX > 0) {
                val targetRow = (ceil(rectsHelper.getRowIndexForItemPosition(lastVisiblePosition).toDouble() / columnCount) * columnCount).toInt()
                rectsHelper.rows[targetRow]?.lastOrNull() ?: lastVisiblePosition
            } else {
                val targetRow = (floor(rectsHelper.getRowIndexForItemPosition(firstVisiblePosition).toDouble() / columnCount) * columnCount).toInt()
                rectsHelper.rows[targetRow]?.firstOrNull() ?: firstVisiblePosition
            }.also {
                prefManager.currentPage = it
            }.run { if (this == -1) 0 else this }
        }

        override fun canSnap(): Boolean {
            return viewModel.currentEditingInterfacePosition.value == -1
        }
    }

    data class State(
        val isPreview: Boolean = false,
        val selectionPreviewRequestCode: Int? = null,
        //This is used to track when the notification shade has
        //been expanded/collapsed or when the "show in NC" setting
        //has been changed. Since the params are different for the
        //widget frame depending on whether it's showing in the NC
        //or not, we need to update them. We could do it directly,
        //but that causes weird shifting and resizing since it happens
        //before the Accessibility Service hides or shows the frame.
        //So instead, we set this flag to true when the params should be
        //updated. The Accessibility Service takes care of calling
        //the update method after it starts a frame removal or addition.
        //The method itself checks whether it can run (i.e. the
        //animation state of the frame is IDLE) and then updates
        //the params.
        val isPendingNotificationStateChange: Boolean = false,
        val isPendingOrientationStateChange: Boolean = false,
        val isTempHide: Boolean = false,
        val screenOrientation: Int = Surface.ROTATION_0,
    )

    open class WidgetFrameViewModel(delegate: MainWidgetFrameDelegate) : BaseViewModel<State, MainWidgetFrameDelegate>(delegate) {
        val isSelectingFrame = MutableStateFlow(false)
        val wallpaperInfo = MutableStateFlow<WallpaperInfo?>(null)
        val debugIdItems = MutableStateFlow<Set<String>>(setOf())
        val animationState = MutableStateFlow(AnimationState.STATE_IDLE)
        val acknowledgedTwoFingerTap = MutableStateFlow<Boolean?>(null)
        val acknowledgedThreeFingerTap = MutableStateFlow(false)

        override val containerCornerRadiusKey: String = PrefManager.KEY_FRAME_CORNER_RADIUS
        override val widgetCornerRadiusKey: String = PrefManager.KEY_FRAME_WIDGET_CORNER_RADIUS

        val framePrefs: FrameSpecificPreferences
            get() = delegate.framePrefs

        val frameId: Int
            get() = delegate.id
    }

    data class WallpaperInfo(
        val drawable: Drawable?,
        val dx: Float,
        val dy: Float,
    )

    enum class AnimationState {
        STATE_ADDING,
        STATE_REMOVING,
        STATE_IDLE,
    }
}
