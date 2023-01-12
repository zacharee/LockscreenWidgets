package tk.zwander.lockscreenwidgets.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.data.window.WindowInfo
import tk.zwander.common.data.window.WindowRootPair
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.defaultDisplayCompat
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.handler
import tk.zwander.common.util.isDebug
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.appwidget.IDListProvider
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate
import tk.zwander.widgetdrawer.util.DrawerDelegate
import java.util.concurrent.ConcurrentLinkedQueue

//Check if the Accessibility service is enabled
val Context.isAccessibilityEnabled: Boolean
    get() = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )?.contains(ComponentName(this, Accessibility::class.java).flattenToString()) ?: false

fun Context.openAccessibilitySettings() {
    //Samsung devices have a separate Activity for listing
    //installed Accessibility Services, for some reason.
    //It's exported and permission-free, at least on Android 10,
    //so attempt to launch it. A "dumb" try-catch is simpler
    //than a check for the existence and state of this Activity.
    //If the Installed Services Activity can't be launched,
    //just launch the normal Accessibility Activity.
    try {
        val accIntent = Intent(Intent.ACTION_MAIN)
        accIntent.`package` = "com.android.settings"
        accIntent.component = ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$AccessibilityInstalledServiceActivity"
        )
        startActivity(accIntent)
    } catch (e: Exception) {
        logUtils.debugLog("Error opening Installed Services:", e)
        val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(accIntent)
    }
}

//Sometimes retrieving the root of a window causes an NPE
//in the framework. Catch that here and return null if it happens.
val AccessibilityWindowInfo.safeRoot: AccessibilityNodeInfo?
    get() = try {
        root
    } catch (e: NullPointerException) {
        null
    } catch (e: Exception) {
        App.globalContext?.logUtils?.normalLog("Error getting window root", e)
        null
    }

fun <T> AccessibilityNodeInfo?.use(block: (AccessibilityNodeInfo?) -> T): T {
    val result = block(this)
    @Suppress("DEPRECATION")
    this?.recycle()
    return result
}

fun AccessibilityEvent.copyCompat(): AccessibilityEvent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AccessibilityEvent(this)
    } else {
        @Suppress("DEPRECATION")
        AccessibilityEvent.obtain(this)
    }
}

fun AccessibilityNodeInfo.hasVisibleIds(vararg ids: String): Boolean {
    return ids.contains(viewIdResourceName) && isVisibleToUser
}

fun AccessibilityNodeInfo.hasVisibleIds(ids: Iterable<String>): Boolean {
    return ids.contains(viewIdResourceName) && isVisibleToUser
}

/**
 * This is where a lot of the magic happens.
 * In Android 5.1+, there's a special overlay type: [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY].
 * Accessibility overlays can show over almost all other windows, including System UI, and therefore the keyguard/lock screen.
 * To actually use this overlay type, we need an AccessibilityService.
 *
 * This service is also used to detect what's onscreen and respond appropriately. For instance, if the user
 * has enabled the "Hide When Notification Shade Shown" option, we use our access to the screen content to
 * check that the left lock screen shortcut is no longer visible, since it hides when the notification shade
 * is shown.
 */
class Accessibility : AccessibilityService(), EventObserver, CoroutineScope by MainScope() {
    private val kgm by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val frameDelegate: WidgetFrameDelegate
        get() = WidgetFrameDelegate.getInstance(this)
    private val drawerDelegate: DrawerDelegate
        get() = DrawerDelegate.getInstance(this)

    private val sharedPreferencesChangeHandler = HandlerRegistry {
        handler(PrefManager.KEY_WIDGET_FRAME_ENABLED) {
            frameDelegate.updateWindowState(wm)
        }
        handler(PrefManager.KEY_ACCESSIBILITY_EVENT_DELAY) {
            serviceInfo = serviceInfo?.apply {
                notificationTimeout = prefManager.accessibilityEventDelay.toLong()
            }
        }
        handler(PrefManager.KEY_DEBUG_LOG) {
            IDListProvider.sendUpdate(this@Accessibility)
        }
    }

    private var latestScreenOnTime: Long = 0L

    private var accessibilityJob: Job? = null

    @SuppressLint("ClickableViewAccessibility", "NewApi")
    override fun onCreate() {
        super.onCreate()

        sharedPreferencesChangeHandler.register(this)

        frameDelegate.updateState {
            it.copy(wasOnKeyguard = kgm.isKeyguardLocked, isScreenOn = power.isInteractive)
        }

        eventManager.addObserver(this)
    }

    override fun onServiceConnected() {
        eventManager.sendEvent(Event.RequestNotificationCount)
        serviceInfo = serviceInfo.apply {
            notificationTimeout = prefManager.accessibilityEventDelay.toLong()
        }

        frameDelegate.onCreate()
        drawerDelegate.onCreate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        //Since we're launching our logic on the main Thread, it's possible
        //that [event] will be reused by Android, causing some crash issues.
        //Make a copy that is recycled later.
        val eventCopy = event.copyCompat()

        if (System.currentTimeMillis() - latestScreenOnTime < 10)
            return

        accessibilityJob?.cancel()
        accessibilityJob = launch {
            //This block here runs even when unlocked, but it only takes a millisecond at most,
            //so it shouldn't be noticeable to the user. We use this to check the current keyguard
            //state and, if applicable, send the keyguard dismissal broadcast.

            var newState = frameDelegate.state.copy()

            //Check if the screen is on.
            val isScreenOn = power.isInteractive
            if (frameDelegate.state.isScreenOn != isScreenOn) {
                //Make sure to turn off temp hide if it was on.
                newState = newState.copy(isTempHide = false, isScreenOn = isScreenOn)
            }

            //Check if the lock screen is shown.
            val isOnKeyguard = kgm.isKeyguardLocked
            if (isOnKeyguard != frameDelegate.state.wasOnKeyguard) {
                newState = newState.copy(wasOnKeyguard = isOnKeyguard)
                //Update the keyguard dismissal Activity that the lock screen
                //has been dismissed.
                if (!isOnKeyguard) {
                    eventManager.sendEvent(Event.LockscreenDismissed)
                }
            }

            newState = newState.copy(screenOrientation = defaultDisplayCompat.rotation)

            if (isDebug) {
                // Nest this in the debug check so that loop doesn't have to run always.
                try {
                    logUtils.debugLog("Source Node ID: ${eventCopy.sourceNodeId}, Window ID: ${eventCopy.windowId}, Source ID Name: ${eventCopy.source?.viewIdResourceName}")
                    logUtils.debugLog(
                        "Records: ${
                            run {
                                val records = ArrayList<String>()
                                for (i in 0 until eventCopy.recordCount) {
                                    val record = eventCopy.getRecord(i)
                                    records.add("$record ${record.sourceNodeId} ${record.windowId} ${record.source?.viewIdResourceName}")
                                }
                                records.joinToString(",,,,,,,,")
                            }
                        }"
                    )
                } catch (e: Exception) {
                    logUtils.debugLog("Error printing debug info", e)
                }
            }

            logUtils.debugLog("Accessibility event: $eventCopy, isScreenOn: ${isScreenOn}, wasOnKeyguard: $isOnKeyguard, ${drawerDelegate.state}")

            //The below block can (very rarely) take over half a second to execute, so only run it
            //if we actually need to (i.e. on the lock screen and screen is on).
            if ((isOnKeyguard || prefManager.showInNotificationCenter) && isScreenOn && prefManager.widgetFrameEnabled /* This is only needed when the frame is enabled */) {
                val (
                    windows, appWindow,
                    nonAppSystemWindow, minSysUiWindowIndex,
                    hasScreenOffMemoWindow, hasFaceWidgetsWindow,
                    hasEdgePanelWindow, sysUiWindowViewIds,
                    sysUiWindowNodes,
                ) = getWindows(windows)

                //Update any ID list widgets on the new IDs
                launch {
                    eventManager.sendEvent(Event.DebugIdsUpdated(sysUiWindowViewIds))
                    IDListProvider.sendUpdate(this@Accessibility)
                }

                if (isDebug) {
                    logUtils.debugLog(
                        sysUiWindowNodes.filter { it.isVisibleToUser }.map { it.viewIdResourceName }
                            .toString()
                    )

                    frameDelegate.setNewDebugIdItems(sysUiWindowViewIds.toList())
                }

                //The logic in this block only needs to run when on the lock screen.
                //Put it in an if-check to help performance.
                if (isOnKeyguard) {
                    //Find index of the topmost application window in the set of all windows.
                    val appIndex = appWindow?.index ?: -1
                    //Find index of the topmost system window.
                    val systemIndex = nonAppSystemWindow?.index ?: -1

                    newState = newState.copy(
                        //Samsung's Screen-Off Memo is really just a normal Activity that shows over the lock screen.
                        //However, it's not an Application-type window for some reason, so it won't hide with the
                        //currentAppLayer check. Explicitly check for its existence here.
                        isOnScreenOffMemo = hasScreenOffMemoWindow,
                        isOnEdgePanel = hasEdgePanelWindow,
                        isOnFaceWidgets = hasFaceWidgetsWindow,
                        //Generate "layer" values for the System UI window and for the topmost app window, if
                        //it exists.
                        //currentAppLayer *should* be -1 even if there's an app open in the background,
                        //since getWindows() is only meant to return windows that can actually be
                        //interacted with. The only time it should be anything else (usually 1) is
                        //if an app is displaying above the keyguard, such as the incoming call
                        //screen or the camera.
                        currentAppLayer = if (appIndex != -1) windows.size - appIndex else appIndex,
                        currentSysUiLayer = if (minSysUiWindowIndex != -1) windows.size - minSysUiWindowIndex else minSysUiWindowIndex,
                        currentSystemLayer = if (systemIndex != -1) windows.size - systemIndex else systemIndex,
                        //This is mostly a debug value to see which app LSWidg thinks is on top.
                        currentAppPackage = appWindow?.root?.packageName?.toString(),
                    )
                } else {
                    //If we're not on the lock screen, whether or not Screen-Off Memo is showing
                    //doesn't matter.
                    newState = newState.copy(
                        isOnScreenOffMemo = false
                    )
                }

                var tempState = newState.copy(
                    onMainLockscreen = false,
                    showingNotificationsPanel = false,
                    notificationsPanelFullyExpanded = false,
                    hideForPresentIds = false,
                    hideForNonPresentIds = false
                )

                //Recycle all windows and nodes.
                sysUiWindowNodes.forEach { node ->
                    //If the user has enabled the option to hide the frame on security (pin, pattern, password)
                    //input, we need to check if they're on the main lock screen. This is more reliable than
                    //checking for the existence of a security input, since there are a lot of different possible
                    //IDs, and OEMs change them. "notification_panel" and "left_button" are largely unchanged,
                    //although this method isn't perfect.
                    if (isOnKeyguard && prefManager.hideOnSecurityPage) {
                        if (node.hasVisibleIds(
                                "com.android.systemui:id/notification_panel",
                                "com.android.systemui:id/left_button"
                            )) {
                            tempState = tempState.copy(
                                onMainLockscreen = true
                            )
                        }
                    }

                    if (isOnKeyguard && prefManager.hideOnNotificationShade) {
                        if (node.hasVisibleIds(
                                "com.android.systemui:id/quick_settings_panel",
                                "com.android.systemui:id/settings_button",
                                "com.android.systemui:id/tile_label",
                                "com.android.systemui:id/header_label"
                            ) || ((Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) &&
                                    node.hasVisibleIds(
                                        "com.android.systemui:id/quick_qs_panel",
                                    ))) {
                            //Used for "Hide When Notification Shade Shown" so we know when it's actually expanded.
                            //Some devices don't even have left shortcuts, so also check for keyguard_indication_area.
                            //Just like the showingSecurityInput check, this is probably unreliable for some devices.
                            tempState = tempState.copy(
                                showingNotificationsPanel = true
                            )
                        }
                    }

                    //If the option to show when the NC is fully expanded is enabled,
                    //check if the frame can actually show. This checks to the "more_button"
                    //ID, which is unique to One UI (it's the three-dot button), and is only
                    //visible when the NC is fully expanded.
                    if (prefManager.showInNotificationCenter) {
                        if (node.hasVisibleIds("com.android.systemui:id/more_button")) {
                            tempState = tempState.copy(
                                notificationsPanelFullyExpanded = true
                            )
                        }
                    }

                    //Check to see if any of the user-specified IDs are present.
                    //If any are, the frame will be hidden.
                    val presentIds = prefManager.presentIds
                    if (presentIds.isNotEmpty()) {
                        if (node.hasVisibleIds(presentIds)) {
                            newState = newState.copy(
                                hideForPresentIds = true
                            )
                        }
                    }

                    //Check to see if any of the user-specified IDs aren't present
                    //(or are present but not visible). If any aren't, the frame will be hidden.
                    val nonPresentIds = prefManager.nonPresentIds
                    if (nonPresentIds.isNotEmpty()) {
                        if (nonPresentIds.contains(node.viewIdResourceName)
                            || nonPresentIds.contains(node.viewIdResourceName) && !node.isVisibleToUser) {
                            tempState = tempState.copy(
                                hideForNonPresentIds = true
                            )
                        }
                    }

                    @Suppress("DEPRECATION")
                    node.recycle()
                }

                newState = tempState

                windows.forEach {
                    try {
                        @Suppress("DEPRECATION")
                        it.root?.recycle()
                    } catch (_: IllegalStateException) {
                    }

                    try {
                        @Suppress("DEPRECATION")
                        it.root?.recycle()
                    } catch (_: IllegalStateException) {
                    }
                }
            }

            frameDelegate.updateStateAndWindowState(wm, true) { newState }

            // Some logic for making the drawer go away or system dialogs dismiss when widgets launch Activities indirectly.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val matchesWindowsChanged =
                    eventCopy.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                            && ((eventCopy.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_ADDED != 0)
                            || (eventCopy.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_ACTIVE != 0))
                val matchesWindowStateChanged =
                    eventCopy.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

                logUtils.debugLog(
                    "Checking for dismiss eligibility.\n" +
                            "matchesWindowsChanges: $matchesWindowsChanged\n" +
                            "matchesWindowStateChanged: $matchesWindowStateChanged\n" +
                            "packageName: ${eventCopy.packageName}\n" +
                            "handlingDrawerClick: ${drawerDelegate.state.handlingDrawerClick}\n" +
                            "handlingFrameClick: ${frameDelegate.state.handlingFrameClick}"
                )

                if ((matchesWindowsChanged || matchesWindowStateChanged)
                    && eventCopy.packageName != packageName
                    && (drawerDelegate.state.handlingDrawerClick || frameDelegate.state.handlingFrameClick)
                ) {
                    logUtils.debugLog("Starting dismiss Activity because of window change.")
                    DismissOrUnlockActivity.launch(this@Accessibility)

                    if (drawerDelegate.state.handlingDrawerClick) {
                        logUtils.debugLog("Hiding drawer because of window change")
                        eventManager.sendEvent(Event.CloseDrawer)
                    }

                    drawerDelegate.updateState { it.copy(handlingDrawerClick = false) }
                    frameDelegate.updateState { it.copy(handlingFrameClick = false) }
                }
            }

            if ((eventCopy.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                        || eventCopy.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                && eventCopy.packageName != null
            ) {
                drawerDelegate.updateState { it.copy(handlingDrawerClick = false) }
                frameDelegate.updateState { it.copy(handlingFrameClick = false) }
            }

            try {
                //Make sure to recycle the copy of the event.
                @Suppress("DEPRECATION")
                eventCopy.recycle()
            } catch (e: IllegalStateException) {
                //Sometimes the event is already recycled somehow.
            }
        }
    }

    override fun onInterrupt() {}

    override fun onEvent(event: Event) {
        when (event) {
            is Event.NewNotificationCount -> {
                //Receive updates from our notification listener service on how many
                //notifications are currently shown to the user. This count excludes
                //notifications not visible on the lock screen.
                //If the notification count is > 0, and the user has the option enabled,
                //make sure to hide the widget frame.
                frameDelegate.updateStateAndWindowState(wm) { it.copy(notificationCount = event.count) }
            }

            Event.TempHide -> {
                frameDelegate.removeWindow(wm)
            }

            Event.ScreenOff -> {
                //Sometimes ACTION_SCREEN_OFF gets received *after* the display turns on,
                //so this check is here to make sure the screen is actually off when this
                //action is received.
                if (!power.isInteractive) {
                    logUtils.debugLog("Screen off")

                    accessibilityJob?.cancel()

                    //If the device has some sort of AOD or ambient display, by the time we receive
                    //an accessibility event and see that the display is off, it's usually too late
                    //and the current screen content has "frozen," causing the widget frame to show
                    //where it shouldn't. ACTION_SCREEN_OFF is called early enough that we can remove
                    //the frame before it's frozen in place.
                    frameDelegate.forceWakelock(wm, false)
                    frameDelegate.updateStateAndWindowState(wm) {
                        it.copy(
                            isTempHide = false,
                            isScreenOn = false
                        )
                    }
                }
            }

            Event.ScreenOn -> {
                latestScreenOnTime = System.currentTimeMillis()

                logUtils.debugLog("Screen on")

                accessibilityJob?.cancel()
                frameDelegate.updateStateAndWindowState(wm) { it.copy(isScreenOn = true) }
            }

            else -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
        sharedPreferencesChangeHandler.unregister(this)
        frameDelegate.onDestroy()
        drawerDelegate.onDestroy()

        eventManager.removeObserver(this)
    }

    /**
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] objects corresponding to the System UI windows.
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] corresponding to the topmost app window.
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] corresponding to the topmost non-System UI window.
     */
    private suspend fun getWindows(windows: List<AccessibilityWindowInfo>): WindowInfo {
        val systemUiWindows = ArrayList<WindowRootPair>()

        val sysUiWindowViewIds = ConcurrentLinkedQueue<String>()
        val sysUiWindowNodes = ConcurrentLinkedQueue<AccessibilityNodeInfo>()
        val sysUiWindowAwaits = ConcurrentLinkedQueue<Deferred<*>>()

        var topAppWindow: WindowRootPair? = null
        var topNonSysUiWindow: WindowRootPair? = null

        var hasScreenOffMemoWindow = false
        var hasFaceWidgetsWindow = false
        var hasEdgePanelWindow = false

        var minSysUiWindowIndex = -1

        val processed = windows.mapIndexed { index, rawWindow ->
            val window = WindowRootPair(rawWindow, rawWindow.safeRoot, index)
            val safeRoot = window.root
            val isSysUi = safeRoot?.packageName == "com.android.systemui"

            if (isSysUi) {
                systemUiWindows.add(window)

                if (minSysUiWindowIndex == -1) {
                    minSysUiWindowIndex = index
                }

                safeRoot?.let { root ->
                    addAllNodesToList(root, sysUiWindowNodes, sysUiWindowViewIds, sysUiWindowAwaits)
                }
            }

            if (topAppWindow == null && window.window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                topAppWindow = window
            }

            if (
                topNonSysUiWindow == null &&
                window.window.type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.window.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                !isSysUi
            ) {
                topNonSysUiWindow = window
            }

            if (
                safeRoot?.packageName == "com.samsung.android.app.cocktailbarservice" &&
                rawWindow.isFocused && rawWindow.isActive
            ) {
                hasEdgePanelWindow = true
            }

            if (safeRoot?.packageName == "com.samsung.android.app.notes") {
                hasScreenOffMemoWindow = true
            }

            if (safeRoot?.packageName == "com.samsung.android.app.aodservice") {
                hasFaceWidgetsWindow = true
            }

            window
        }

        sysUiWindowAwaits.awaitAll()

        return WindowInfo(
            windows = processed,
            topAppWindow = topAppWindow,
            topNonSysUiWindow = topNonSysUiWindow,
            hasScreenOffMemoWindow = hasScreenOffMemoWindow,
            hasFaceWidgetsWindow = hasFaceWidgetsWindow,
            minSysUiWindowIndex = minSysUiWindowIndex,
            sysUiWindowNodes = sysUiWindowNodes,
            sysUiWindowViewIds = sysUiWindowViewIds,
            hasEdgePanelWindow = hasEdgePanelWindow
        )
    }

    /**
     * Recursively add all [AccessibilityNodeInfo]s contained in a parent to a list.
     * We use this instead of [AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId]
     * for performance and reliability reasons.
     * @param parentNode the root [AccessibilityNodeInfo] whose children we want to add to
     * a list. List will include this node as well.
     * @param list the list that will contain the child nodes.
     * @param ids the list of all visible IDs
     * @param awaits a list of all running Jobs here. The caller should use awaitAll().
     *
     * TODO: This has to recursively traverse the View tree it's given. [AccessibilityNodeInfo.getChild]
     * TODO: can be pretty slow. All adjacent nodes of the tree are processed concurrently, but their
     * TODO: children have to be processed linearly. We need a flattened list of [AccessibilityNodeInfo] objects
     * TODO: to properly handle frame visibility, but there may be a better way to do that, since this can take
     * TODO: up to a second to finish executing.
     */
    private suspend fun addAllNodesToList(
        parentNode: AccessibilityNodeInfo,
        list: ConcurrentLinkedQueue<AccessibilityNodeInfo>,
        ids: ConcurrentLinkedQueue<String>,
        awaits: ConcurrentLinkedQueue<Deferred<*>>
    ) {
        coroutineScope {
            awaits.add(async {
                list.add(parentNode)
                if (parentNode.isVisibleToUser && parentNode.viewIdResourceName != null) {
                    ids.add(parentNode.viewIdResourceName)
                }

                for (i in 0 until parentNode.childCount) {
                    awaits.add(async {
                        val child = try {
                            parentNode.getChild(i)
                        } catch (e: SecurityException) {
                            //Sometimes a SecurityException gets thrown here (on Huawei devices)
                            //so just return null if it happens
                            null
                        } catch (e: NullPointerException) {
                            //Sometimes a NullPointerException is thrown here with this error:
                            //"Attempt to read from field 'com.android.server.appwidget.AppWidgetServiceImpl$ProviderId
                            //com.android.server.appwidget.AppWidgetServiceImpl$Provider.id' on a null object reference"
                            //so just return null if that happens.
                            null
                        }
                        if (child != null) {
                            if (child.childCount > 0) {
                                addAllNodesToList(child, list, ids, awaits)
                            } else {
                                list.add(child)
                                if (child.isVisibleToUser && child.viewIdResourceName != null) {
                                    ids.add(child.viewIdResourceName)
                                }
                            }
                        }
                    })
                }
            })
        }
    }
}