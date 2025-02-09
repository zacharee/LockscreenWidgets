package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import com.android.internal.R.attr.screenOrientation
import com.bugsnag.android.performance.compose.MeasuredComposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.BaseDelegate
import tk.zwander.common.util.BlurManager
import tk.zwander.common.util.Event
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.ISnappyLayoutManager
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.dpAsPx
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.handler
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.mainHandler
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.pxAsDp
import tk.zwander.common.util.screenSize
import tk.zwander.common.util.wallpaperUtils
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.data.Mode
import tk.zwander.lockscreenwidgets.databinding.WidgetFrameBinding
import tk.zwander.lockscreenwidgets.services.Accessibility
import tk.zwander.lockscreenwidgets.views.WidgetFrameView

/**
 * Handle most of the logic involving the widget frame.
 */
open class MainWidgetFrameDelegate protected constructor(context: Context, protected val id: Int = -1) : BaseDelegate<MainWidgetFrameDelegate.State>(context) {
    companion object {
        private val instance = MutableStateFlow<MainWidgetFrameDelegate?>(null)

        val readOnlyInstance = instance.asStateFlow()

        @Synchronized
        fun peekInstance(context: Context): MainWidgetFrameDelegate? {
            if (instance.value == null) {
                context.logUtils.debugLog("Accessibility isn't running yet")

                return null
            }

            return getInstance(context)
        }

        @Synchronized
        fun getInstance(context: Context): MainWidgetFrameDelegate {
            return instance.value ?: run {
                if (context !is Accessibility) {
                    throw IllegalStateException("Delegate can only be initialized by Accessibility Service!")
                } else {
                    MainWidgetFrameDelegate(context).also {
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
        set(newCommonState) {
            val oldState = field
            field = newCommonState

            var newFrameState = state

            if (newCommonState.screenOrientation != oldState.screenOrientation) {
                newFrameState =
                    newFrameState.copy(isPendingOrientationStateChange = true, isPreview = false)
            }

            if (newCommonState.isScreenOn != oldState.isScreenOn && !newCommonState.isScreenOn) {
                newFrameState =
                    newFrameState.copy(isPreview = false, notificationsPanelFullyExpanded = false)
            }

            state = newFrameState
        }

    override var state: State = State()
        set(newState) {
            var actualNewState = newState
            val oldState = field

            // Extra state checks //
            if (actualNewState.notificationsPanelFullyExpanded != oldState.notificationsPanelFullyExpanded) {
                actualNewState =
                    actualNewState.copy(isPendingNotificationStateChange = true, isPreview = false)
            }

            if (actualNewState.isTempHide != oldState.isTempHide && actualNewState.isTempHide) {
                actualNewState = actualNewState.copy(isPreview = false)
            }
            // ------------ //

            field = actualNewState

            if (actualNewState.isPreview != oldState.isPreview) {
                if (actualNewState.isPreview) {
                    if (canShow()) {
                        addWindow(wm)
                    }
                } else {
                    if (!canShow()) {
                        removeWindow(wm)
                    }
                }
            }

            if (actualNewState.selectionPreviewRequestCode != oldState.selectionPreviewRequestCode) {
                if (actualNewState.selectionPreviewRequestCode != null) {
                    if (canShow()) {
                        addWindow(wm)
                        binding.selectFrameLayout.isVisible = true
                    }
                } else {
                    if (!canShow()) {
                        removeWindow(wm)
                        binding.selectFrameLayout.isVisible = false
                    }
                }
            }
        }

    private val saveMode: FrameSizeAndPosition.FrameType
        get() {
            val isLandscape = prefManager.separateFrameLayoutForLandscape &&
                    (commonState.screenOrientation == Surface.ROTATION_90 ||
                        commonState.screenOrientation == Surface.ROTATION_270)

            return when {
                id != -1 -> FrameSizeAndPosition.FrameType.SecondaryLockscreen.select(!isLandscape, id)
                state.notificationsPanelFullyExpanded && framePrefs.showInNotificationShade -> {
                    if (kgm.isKeyguardLocked && framePrefs.separateLockNCPosition) {
                        FrameSizeAndPosition.FrameType.LockNotification.select(!isLandscape)
                    } else {
                        FrameSizeAndPosition.FrameType.NotificationNormal.select(!isLandscape)
                    }
                }
                else -> FrameSizeAndPosition.FrameType.LockNormal.select(!isLandscape)
            }
        }

    //The size, position, and such of the widget frame on the lock screen.
    final override val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        frameSizeAndPosition.getSizeForType(saveMode).let { size ->
            width = dpAsPx(size.x)
            height = dpAsPx(size.y)
        }
        frameSizeAndPosition.getPositionForType(saveMode).let { pos ->
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
    override val rootView: View
        get() = binding.root
    override val recyclerView: RecyclerView
        get() = binding.widgetsPager
    override var currentWidgets: List<WidgetData>
        get() = FramePrefs.getWidgetsForFrame(this, id).toList()
        set(value) {
            FramePrefs.setWidgetsForFrame(this, id, value)
        }

    //The actual frame View
    private val binding by lazy {
        WidgetFrameBinding.inflate(LayoutInflater.from(ContextThemeWrapper(this, R.style.AppTheme)))
    }
    override val gridLayoutManager = SpannedLayoutManager()
    override val adapter by lazy {
        WidgetFrameAdapter(
            frameId = id,
            context = context,
            rootView = rootView,
            onRemoveCallback = { item, _ ->
                binding.removeWidgetConfirmation.root.show(item)
            },
            saveTypeGetter = { saveMode },
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
        handler(PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR) {
            binding.frame.updatePageIndicatorBehavior()
        }
        handler(
            FramePrefs.generatePrefKey(FramePrefs.KEY_FRAME_ROW_COUNT, id),
            FramePrefs.generatePrefKey(FramePrefs.KEY_FRAME_COL_COUNT, id),
        ) {
            updateCounts()
            adapter.updateViews()
        }
        handler(FrameSpecificPreferences.keyFor(id, PrefManager.KEY_FRAME_BACKGROUND_COLOR)) {
            binding.frame.updateFrameBackground()
        }
        handler(
            framePrefs.keyFor(PrefManager.KEY_FRAME_MASKED_MODE),
            framePrefs.keyFor(PrefManager.KEY_MASKED_MODE_DIM_AMOUNT),
        ) {
            updateWallpaperLayerIfNeeded()
        }
        handler(
            PrefManager.KEY_SHOW_DEBUG_ID_VIEW,
            PrefManager.KEY_DEBUG_LOG,
        ) {
            binding.frame.updateDebugIdViewVisibility()
        }
        handler(framePrefs.keyFor(PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER)) {
            updateState { it.copy(isPendingNotificationStateChange = true) }
        }
        handler(PrefManager.KEY_FRAME_CORNER_RADIUS) {
            updateCornerRadius()
        }
        handler(
            PrefManager.KEY_LOCK_WIDGET_FRAME,
        ) {
            adapter.currentEditingInterfacePosition = -1
        }
        handler(
            PrefManager.KEY_FRAME_WIDGET_CORNER_RADIUS,
        ) {
            if (binding.frame.isAttachedToWindow) {
                adapter.updateViews()
            }
        }
        handler(PrefManager.KEY_WIDGET_FRAME_ENABLED) {
            updateWindowState(wm)
        }
        handler(PrefManager.KEY_CAN_SHOW_FRAME_FROM_TASKER, PrefManager.KEY_FORCE_SHOW_FRAME) {
            updateWindowState(wm)
        }
    }

    private val showWallpaperLayerCondition: Boolean
        get() = !state.isPreview &&
                state.selectionPreviewRequestCode == null &&
                framePrefs.maskedMode &&
                (!state.notificationsPanelFullyExpanded || !framePrefs.showInNotificationShade)

    override val blurManager by lazy {
        BlurManager(
            context = this,
            params = params,
            targetView = binding.blurBackground,
            listenKeys = listOf(
                framePrefs.keyFor(PrefManager.KEY_BLUR_BACKGROUND),
                framePrefs.keyFor(PrefManager.KEY_BLUR_BACKGROUND_AMOUNT),
            ),
            shouldBlur = { framePrefs.blurBackground && !showWallpaperLayerCondition },
            blurAmount = { framePrefs.blurBackgroundAmount },
            cornerRadius = { dpAsPx(prefManager.cornerRadiusDp).toFloat() },
            updateWindow = { binding.frame.updateWindow(wm, params) },
            windowManager = wm,
        )
    }

    override fun onEvent(event: Event) {
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
                    updateWindowState(wm)
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
                            frameSizeAndPosition.getPositionForType(saveMode).y
                        )
                    )
                    updateParamsIfNeeded()
                }
            }
            is Event.CenterFrameVertically -> {
                if (event.frameId == id) {
                    frameSizeAndPosition.setPositionForType(
                        saveMode,
                        Point(
                            frameSizeAndPosition.getPositionForType(saveMode).x,
                            0
                        )
                    )
                    updateParamsIfNeeded()
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
                        PointF(pxAsDp(params.width), pxAsDp(params.height))
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
                if (event.frameId == id) {
                    try {
                        if (event.attached) {
                            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                            widgetHost.startListening(this)
                            updateWallpaperLayerIfNeeded()
                            updateCornerRadius()
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
                    } catch (e: NullPointerException) {
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
                    forceWakelock(wm, event.down)
                }
            }
            is Event.NewNotificationCount -> {
                //Receive updates from our notification listener service on how many
                //notifications are currently shown to the user. This count excludes
                //notifications not visible on the lock screen.
                //If the notification count is > 0, and the user has the option enabled,
                //make sure to hide the widget frame.
                updateStateAndWindowState(wm, transform = { it.copy(notificationCount = event.count) })
            }
            Event.ScreenOff -> {
                //If the device has some sort of AOD or ambient display, by the time we receive
                //an accessibility event and see that the display is off, it's usually too late
                //and the current screen content has "frozen," causing the widget frame to show
                //where it shouldn't. ACTION_SCREEN_OFF is called early enough that we can remove
                //the frame before it's frozen in place.
                forceWakelock(wm, false)
                updateStateAndWindowState(
                    wm = wm,
                    transform = {
                        it.copy(
                            isTempHide = false,
                        )
                    },
                    commonTransform = {
                        it.copy(
                            isScreenOn = false,
                        )
                    },
                )
            }
            Event.ScreenOn -> {
                updateStateAndWindowState(wm) { it.copy(isScreenOn = true) }
            }
            is Event.PreviewFrames -> {
                if (prefManager.currentSecondaryFrames.isEmpty() && event.show == Event.PreviewFrames.ShowMode.SHOW_FOR_SELECTION) {
                    eventManager.sendEvent(Event.LaunchAddWidget(id))
                } else {
                    if (event.includeMainFrame || id != -1) {
                        updateState {
                            it.copy(
                                isPreview = event.show == Event.PreviewFrames.ShowMode.SHOW ||
                                        (event.show == Event.PreviewFrames.ShowMode.TOGGLE && !it.isPreview),
                                selectionPreviewRequestCode = event.requestCode.takeIf {
                                    event.show == Event.PreviewFrames.ShowMode.SHOW_FOR_SELECTION
                                },
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
                updateCommonState { it.copy(handlingClick = prefManager.requestUnlock) }
            }
        }

        return !ignoreTouches
    }

    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate() {
        super.onCreate()

        binding.frame.onCreate(id)
        binding.selectFrameLayout.setContent {
            MeasuredComposable(name = "SelectFrameLayoutContent") {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        contentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.surface),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(text = "$id")

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            eventManager.sendEvent(Event.FrameSelected(null, state.selectionPreviewRequestCode))
                                        },
                                    ) {
                                        Text(text = stringResource(R.string.cancel))
                                    }

                                    OutlinedButton(
                                        onClick = { eventManager.sendEvent(Event.FrameSelected(id, state.selectionPreviewRequestCode)) },
                                    ) {
                                        Text(text = stringResource(R.string.select))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        //Scroll to the stored page, making sure to catch a potential
        //out-of-bounds error.
        try {
            scrollToStoredPosition(false)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()

        binding.frame.removeWindow(wm)

        if (id == -1) {
            invalidateInstance()
        }
    }

    override fun isLocked(): Boolean {
        return prefManager.lockWidgetFrame
    }

    fun updateStateAndWindowState(
        wm: WindowManager,
        updateAccessibility: Boolean = false,
        transform: (State) -> State = { it },
        commonTransform: (BaseState) -> BaseState = { it },
    ) {
        updateState(transform)
        updateCommonState(commonTransform)
        updateWindowState(wm, updateAccessibility)
    }

    override fun retrieveCounts(): Pair<Int, Int> {
        return FramePrefs.getGridSizeForFrame(this, id)
    }

    override fun widgetRemovalConfirmed(event: Event.RemoveWidgetConfirmed, position: Int) {
        if (event.remove) {
            binding.widgetsPager.post {
                val pos = when (val pos = gridLayoutManager.firstVisiblePosition) {
                    RecyclerView.NO_POSITION -> (position - 1).coerceAtLeast(0)
                    else -> pos
                }

                binding.widgetsPager.scrollToPosition(pos)
            }
        }
    }

    private fun addWindow(wm: WindowManager) {
        logUtils.debugLog("Adding overlay")

        if (!binding.frame.isAttachedToWindow) {
            updateParamsIfNeeded()
        }
        binding.frame.addWindow(wm, params)
    }

    private fun removeWindow(wm: WindowManager) {
        if (isAttached) {
            logUtils.debugLog("Removing overlay")
        }

        adapter.currentEditingInterfacePosition = -1

        updateCommonState { it.copy(handlingClick = false) }
        forceWakelock(wm, false)
        binding.frame.removeWindow(wm)
    }

    fun setNewDebugIdItems(items: List<String>) {
        binding.frame.setNewDebugIdItems(items)
    }

    private fun updateWindowState(wm: WindowManager, updateAccessibility: Boolean = false) {
        if (canShow()) {
            if (updateAccessibility) updateAccessibilityPass()
            addWindow(wm)
        } else {
            removeWindow(wm)
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
     * - [BaseDelegate.BaseState.isScreenOn] is true
     * - [State.isTempHide] is false
     * - [State.notificationsPanelFullyExpanded] is true AND [FrameSpecificPreferences.showInNotificationShade] is true
     * - [State.hideForPresentIds] is false
     * - [State.hideForNonPresentIds] is false
     * - [PrefManager.widgetFrameEnabled] is true
     * - [PrefManager.hideInLandscape] is false OR [screenOrientation] represents a portrait rotation
     * =======
     * OR
     * =======
     * - [BaseDelegate.BaseState.wasOnKeyguard] is true
     * - [BaseDelegate.BaseState.isScreenOn] is true (i.e. the display is properly on: not in Doze or on the AOD)
     * - [State.isTempHide] is false
     * - [FrameSpecificPreferences.showOnMainLockScreen] is true OR [FrameSpecificPreferences.showInNotificationShade] is false
     * - [FrameSpecificPreferences.hideOnFaceWidgets] is false OR [State.isOnFaceWidgets] is false
     * - [State.currentAppLayer] is less than 0 (i.e. doesn't exist)
     * - [State.isOnEdgePanel] is false OR [FrameSpecificPreferences.hideOnEdgePanel] is false
     * - [State.isOnScreenOffMemo] is false
     * - [State.onMainLockscreen] is true OR [State.showingNotificationsPanel] is true OR [FrameSpecificPreferences.hideOnSecurityPage] is false
     * - [State.showingNotificationsPanel] is false OR [FrameSpecificPreferences.hideOnNotificationShade] is false (OR [State.notificationsPanelFullyExpanded] is true AND [FrameSpecificPreferences.showInNotificationShade] is true
     * - [State.notificationCount] is 0 (i.e. no notifications shown on lock screen, not necessarily no notifications at all) OR [FrameSpecificPreferences.hideOnNotifications] is false
     * - [State.hideForPresentIds] is false OR [PrefManager.presentIds] is empty
     * - [State.hideForNonPresentIds] is false OR [PrefManager.nonPresentIds] is empty
     * - [PrefManager.hideInLandscape] is false OR [screenOrientation] represents a portrait rotation
     * - [PrefManager.widgetFrameEnabled] is true (i.e. the widget frame is actually enabled)
     * =======
     */
    private fun canShow(): Boolean {
        fun forPreview(): Boolean {
            return state.isPreview || state.selectionPreviewRequestCode != null
        }

        fun forCommon(): Boolean {
            return commonState.isScreenOn
                    && !state.isTempHide
                    && !state.hideForPresentIds
                    && !state.hideForNonPresentIds
                    && prefManager.widgetFrameEnabled
                    && (!prefManager.hideInLandscape || commonState.screenOrientation == Surface.ROTATION_0 || commonState.screenOrientation == Surface.ROTATION_180)
                    && prefManager.canShowFrameFromTasker
                    && (!framePrefs.hideWhenKeyboardShown || !state.showingKeyboard)
        }

        fun forNotificationCenter(): Boolean {
            return (state.notificationsPanelFullyExpanded && framePrefs.showInNotificationShade)
                    && forCommon()
        }

        fun forLockscreen(): Boolean {
            return commonState.wasOnKeyguard
                    && (framePrefs.showOnMainLockScreen || !framePrefs.showInNotificationShade)
                    && (!framePrefs.hideOnFaceWidgets || !state.isOnFaceWidgets)
                    && (state.currentAppLayer < 0 && state.currentAppPackage == null)
                    && (!state.isOnEdgePanel || !framePrefs.hideOnEdgePanel)
                    && !state.isOnScreenOffMemo
                    && (state.onMainLockscreen || state.showingNotificationsPanel || !framePrefs.hideOnSecurityPage)
                    && (!state.showingNotificationsPanel || !framePrefs.hideOnNotificationShade)
                    && (state.notificationCount == 0 || !framePrefs.hideOnNotifications)
                    && !state.hidingForPresentApp
                    && forCommon()
        }

        fun forced(): Boolean {
            return prefManager.forceShowFrame
        }

        return (forced() || forPreview() || forNotificationCenter() || forLockscreen()).also {
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
                        "hidingForPresentApp: ${state.hidingForPresentApp}\n" +
                        "canShowFrameFromTasker: ${prefManager.canShowFrameFromTasker}\n" +
                        "forceShowFrame: ${prefManager.forceShowFrame}\n" +
                        "hideOnFaceWidgets: ${framePrefs.hideOnFaceWidgets}\n" +
                        "hideWhenKeyboardShown: ${framePrefs.hideWhenKeyboardShown}\n",
            )
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

        if (showWallpaperLayer) {
            logUtils.debugLog("Trying to retrieve wallpaper", null)

            try {
                val drawable = wallpaperUtils.wallpaperDrawable

                logUtils.debugLog("Retrieved wallpaper: $drawable", null)

                drawable?.mutate()?.apply {
                    logUtils.debugLog("Setting wallpaper drawable.", null)

                    binding.wallpaperBackground.setImageDrawable(this)
                    binding.wallpaperBackground.colorFilter = PorterDuffColorFilter(
                        Color.argb(
                            ((framePrefs.maskedModeDimAmount / 100f) * 255).toInt(),
                            0, 0, 0
                        ), PorterDuff.Mode.SRC_ATOP
                    )
                    binding.wallpaperBackground.scaleType = ImageView.ScaleType.MATRIX
                    binding.wallpaperBackground.imageMatrix = Matrix().apply {
                        val realSize = screenSize
                        val loc = binding.root.locationOnScreen ?: intArrayOf(0, 0)

                        val dWidth: Int = intrinsicWidth
                        val dHeight: Int = intrinsicHeight

                        val wallpaperAdjustmentX = (dWidth - realSize.x) / 2f
                        val wallpaperAdjustmentY = (dHeight - realSize.y) / 2f

                        setTranslate(
                            (-loc[0].toFloat() - wallpaperAdjustmentX),
                            //TODO: a bunch of skins don't like this
                            (-loc[1].toFloat() - wallpaperAdjustmentY)
                        )
                    }
                } ?: binding.wallpaperBackground.setImageDrawable(null)
            } catch (e: Exception) {
                logUtils.normalLog("Error setting wallpaper", e)
                binding.wallpaperBackground.setImageDrawable(null)
            }
        } else {
            logUtils.debugLog("Removing wallpaper", null)

            binding.wallpaperBackground.setImageDrawable(null)
        }
    }

    /**
     * This is called by the Accessibility Service on an event,
     * after all other logic has finished (e.g. frame removal/addition
     * calls). Use this method for updating things that are conditional
     * upon the frame's state after an event.
     */
    private fun updateAccessibilityPass() {
        if (binding.frame.animationState == WidgetFrameView.AnimationState.STATE_IDLE) {
            if (state.isPendingNotificationStateChange || state.isPendingOrientationStateChange) {
                updateParamsIfNeeded()
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
    private fun forceWakelock(wm: WindowManager, on: Boolean) {
        if (on) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }

        binding.frame.updateWindow(wm, params)
    }

    private fun updateOverlay() {
        binding.frame.updateWindow(wm, params)
    }

    private fun updateCornerRadius() {
        val radius = dpAsPx(prefManager.cornerRadiusDp).toFloat()
        binding.frameCard.radius = radius

        binding.editOutline.background = (binding.editOutline.background.mutate() as GradientDrawable).apply {
            this.cornerRadius = radius
        }
    }

    /**
     * Update the frame's params for its current state (normal
     * or in expanded notification center).
     */
    private fun updateParamsIfNeeded() {
        logUtils.debugLog("Checking if params need to be updated")

        val (newX, newY) = frameSizeAndPosition.getPositionForType(saveMode)
        val (newW, newH) = frameSizeAndPosition.getSizeForType(saveMode).run {
            Point(dpAsPx(x), dpAsPx(y))
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

            binding.frame.updateWindow(wm, params)
            mainHandler.post {
                updateWallpaperLayerIfNeeded()
                blurManager.updateBlur(fromParamsUpdate = true)
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
            return (adapter.currentEditingInterfacePosition == -1 || commonState.isHoldingItem) && super.canScrollHorizontally()
        }

        override fun getFixScrollPos(velocityX: Int, velocityY: Int): Int {
            if (childCount == 0) return 0

            return if (velocityX > 0) {
                firstVisiblePosition
            } else {
                lastVisiblePosition
            }.also {
                prefManager.currentPage = it
            }.run { if (this == -1) 0 else this }
        }

        override fun getPositionForVelocity(velocityX: Int, velocityY: Int): Int {
            if (childCount == 0) return 0

            return if (velocityX > 0) {
                lastVisiblePosition
            } else {
                firstVisiblePosition
            }.also {
                prefManager.currentPage = it
            }.run { if (this == -1) 0 else this }
        }

        override fun canSnap(): Boolean {
            return adapter.currentEditingInterfacePosition == -1
        }
    }

    data class State(
        val isOnFaceWidgets: Boolean = false,
        val currentAppLayer: Int = 0,
        val isOnScreenOffMemo: Boolean = false,
        val onMainLockscreen: Boolean = false,
        val showingNotificationsPanel: Boolean = false,
        val notificationCount: Int = 0,
        val hideForPresentIds: Boolean = false,
        val hideForNonPresentIds: Boolean = false,
        val currentSysUiLayer: Int = 1,
        val currentSystemLayer: Int = 0,
        val currentAppPackage: String? = null,
        val isOnEdgePanel: Boolean = false,
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
        val notificationsPanelFullyExpanded: Boolean = false,
        val isTempHide: Boolean = false,
        val hidingForPresentApp: Boolean = false,
        val showingKeyboard: Boolean = false,
    )
}