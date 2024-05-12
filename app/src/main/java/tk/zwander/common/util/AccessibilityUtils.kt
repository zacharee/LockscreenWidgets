package tk.zwander.common.util

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.data.window.WindowInfo
import tk.zwander.common.data.window.WindowRootPair
import tk.zwander.lockscreenwidgets.appwidget.IDListProvider
import tk.zwander.lockscreenwidgets.services.hasVisibleIds
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate
import tk.zwander.widgetdrawer.util.DrawerDelegate
import java.util.concurrent.ConcurrentLinkedQueue

object AccessibilityUtils {
    data class NodeState(
        val onMainLockscreen: Boolean = false,
        val showingNotificationsPanel: Boolean = false,
        val hasMoreButton: Boolean = false,
        val hideForPresentIds: Boolean = false,
        val hideForNonPresentIds: Boolean = false,
        val hasClearAllButton: Boolean = false,
    )

    private fun Context.processNode(
        initialState: NodeState,
        node: AccessibilityNodeInfo?,
        isOnKeyguard: Boolean,
    ): NodeState {
        var newState = initialState

        if (node == null) {
            return initialState
        }

        //If the user has enabled the option to hide the frame on security (pin, pattern, password)
        //input, we need to check if they're on the main lock screen. This is more reliable than
        //checking for the existence of a security input, since there are a lot of different possible
        //IDs, and OEMs change them. "notification_panel" and "left_button" are largely unchanged,
        //although this method isn't perfect.
        if (isOnKeyguard && prefManager.hideOnSecurityPage && !newState.onMainLockscreen) {
            if (node.hasVisibleIds(
                    "com.android.systemui:id/notification_panel",
                    "com.android.systemui:id/left_button"
                )
            ) {
                newState = newState.copy(
                    onMainLockscreen = true
                )
            }
        }

        if (isOnKeyguard && prefManager.hideOnNotificationShade && !newState.showingNotificationsPanel) {
            if (node.hasVisibleIds(
                    "com.android.systemui:id/quick_settings_panel",
                    "com.android.systemui:id/settings_button",
                    "com.android.systemui:id/tile_label",
                    "com.android.systemui:id/header_label",
                    "com.android.systemui:id/split_shade_status_bar",
                ) || ((Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) &&
                        node.hasVisibleIds(
                            "com.android.systemui:id/quick_qs_panel",
                        ))
            ) {
                //Used for "Hide When Notification Shade Shown" so we know when it's actually expanded.
                //Some devices don't even have left shortcuts, so also check for keyguard_indication_area.
                //Just like the showingSecurityInput check, this is probably unreliable for some devices.
                newState = newState.copy(
                    showingNotificationsPanel = true
                )
            }
        }

        //If the option to show when the NC is fully expanded is enabled,
        //check if the frame can actually show. This checks to the "more_button"
        //ID, which is unique to One UI (it's the three-dot button), and is only
        //visible when the NC is fully expanded.
        if (prefManager.showInNotificationCenter && !newState.hasMoreButton) {
            if (node.hasVisibleIds("com.android.systemui:id/more_button")) {
                newState = newState.copy(
                    hasMoreButton = true
                )
            }
        }

        if (prefManager.showInNotificationCenter && !newState.hasClearAllButton) {
            if (node.hasVisibleIds("com.android.systemui:id/clear_all")) {
                newState = newState.copy(
                    hasClearAllButton = true
                )
            }
        }

        //Check to see if any of the user-specified IDs are present.
        //If any are, the frame will be hidden.
        val presentIds = prefManager.presentIds
        if (!newState.hideForPresentIds && presentIds.isNotEmpty()) {
            if (node.hasVisibleIds(presentIds)) {
                newState = newState.copy(
                    hideForPresentIds = true
                )
            }
        }

        //Check to see if any of the user-specified IDs aren't present
        //(or are present but not visible). If any aren't, the frame will be hidden.
        val nonPresentIds = prefManager.nonPresentIds
        if (!newState.hideForNonPresentIds && nonPresentIds.isNotEmpty()) {
            if (!node.hasVisibleIds(nonPresentIds)) {
                newState = newState.copy(
                    hideForNonPresentIds = true
                )
            }
        }

        return newState
    }

    /**
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] objects corresponding to the System UI windows.
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] corresponding to the topmost app window.
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] corresponding to the topmost non-System UI window.
     */
    private suspend fun Context.getWindows(
        windows: List<AccessibilityWindowInfo>,
        isOnKeyguard: Boolean,
    ): WindowInfo {
        val systemUiWindows = ConcurrentLinkedQueue<WindowRootPair>()

        val stateMutex = Mutex()
        var nodeState = NodeState()

        val sysUiWindowViewIds = ConcurrentLinkedQueue<String>()
        val sysUiWindowNodes = ConcurrentLinkedQueue<AccessibilityNodeInfo>()
        val sysUiWindowAwaits = ConcurrentLinkedQueue<Deferred<*>>()

        var topAppWindowIndex: Int = -1
        var topNonSysUiWindowIndex: Int = -1

        var topAppWindowPackageName: String? = null

        var hasScreenOffMemoWindow = false
        var hasFaceWidgetsWindow = false
        var hasEdgePanelWindow = false
        var hasHideForPresentApp = false

        var minSysUiWindowIndex = -1

        val processed = windows.mapIndexedParallel { index, rawWindow ->
            val safeRoot = try {
                rawWindow.root
            } catch (e: NullPointerException) {
                null
            } catch (e: Exception) {
                logUtils.normalLog("Error getting window root", e)
                null
            }
            val isSysUi = safeRoot?.packageName == "com.android.systemui"
            val window = WindowRootPair(rawWindow, safeRoot, index)

            if (isSysUi) {
                systemUiWindows.add(window)

                if (minSysUiWindowIndex == -1) {
                    minSysUiWindowIndex = index
                }

                safeRoot?.let { root ->
                    addAllNodesToList(
                        root,
                        sysUiWindowNodes,
                        sysUiWindowViewIds,
                        sysUiWindowAwaits
                    ) { node ->
                        launch(Dispatchers.IO) {
                            stateMutex.withLock {
                                nodeState = processNode(nodeState, node, isOnKeyguard)
                            }
                        }
                    }
                }
            }

            if (topAppWindowIndex == -1 &&
                (window.window.type == AccessibilityWindowInfo.TYPE_APPLICATION || (
                        // This is a workaround for the Google Assistant popup on Pixel devices.
                        // It reports a window type of "-1" since the popup's real type is TYPE_VOICE_INTERACTION,
                        // but AccessibilityWindowManager#getTypeForWindowManagerWindowType() doesn't filter
                        // for this type and returns "-1" or "unknown".
                        // The package name check is because Samsung's One Handed Mode+ gesture window also reports an unknown type.
                        safeRoot?.packageName == "com.google.android.googlequicksearchbox" && window.window.type == -1))
            ) {
                logUtils.debugLog("Found app window $window", null)
                topAppWindowIndex = index
                topAppWindowPackageName = safeRoot?.packageName?.toString()
            }

            if (
                topNonSysUiWindowIndex == -1 &&
                window.window.type != AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.window.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY &&
                !isSysUi
            ) {
                topNonSysUiWindowIndex = index
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

            if (prefManager.hideFrameOnApps.contains(safeRoot?.packageName)) {
                hasHideForPresentApp = true
            }

            window
        }

        sysUiWindowAwaits.awaitAll()

        return WindowInfo(
            windows = processed,
            topAppWindowIndex = topAppWindowIndex,
            topNonSysUiWindowIndex = topNonSysUiWindowIndex,
            hasScreenOffMemoWindow = hasScreenOffMemoWindow,
            hasFaceWidgetsWindow = hasFaceWidgetsWindow,
            minSysUiWindowIndex = minSysUiWindowIndex,
            sysUiWindowNodes = sysUiWindowNodes,
            sysUiWindowViewIds = sysUiWindowViewIds,
            hasEdgePanelWindow = hasEdgePanelWindow,
            topAppWindowPackageName = topAppWindowPackageName,
            hasHideForPresentApp = hasHideForPresentApp,
            nodeState = nodeState,
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
        awaits: ConcurrentLinkedQueue<Deferred<*>>,
        nodeAddedCallback: (suspend CoroutineScope.(AccessibilityNodeInfo) -> Unit)
    ) {
        coroutineScope {
            list.add(parentNode)
            nodeAddedCallback(parentNode)

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
                    } catch (e: IllegalStateException) {
                        try {
                            parentNode.isSealed = true
                            parentNode.getChild(i)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (child != null) {
                        if (child.childCount > 0) {
                            addAllNodesToList(child, list, ids, awaits, nodeAddedCallback)
                        } else {
                            list.add(child)
                            nodeAddedCallback(child)
                            if (child.isVisibleToUser && child.viewIdResourceName != null) {
                                ids.add(child.viewIdResourceName)
                            }
                        }
                    }
                })
            }
        }
    }

    suspend fun Context.runAccessibilityJob(
        event: AccessibilityEvent?,
        frameDelegate: WidgetFrameDelegate,
        drawerDelegate: DrawerDelegate,
        power: PowerManager,
        kgm: KeyguardManager,
        wm: WindowManager,
        imm: InputMethodManager,
        getWindows: () -> List<AccessibilityWindowInfo>?,
    ) = coroutineScope {
        //This block here runs even when unlocked, but it only takes a millisecond at most,
        //so it shouldn't be noticeable to the user. We use this to check the current keyguard
        //state and, if applicable, send the keyguard dismissal broadcast.

        var newState = frameDelegate.state.copy()
        var newFrameCommonState = frameDelegate.commonState.copy()

        newState = try {
            // Check if keyboard is shown.
            try {
                newState.copy(showingKeyboard = imm.inputMethodWindowVisibleHeight > 0)
            } catch (e: NullPointerException) {
                // Fetching the IME height can cause the system to throw an NPE:
                // "Attempt to read from field 'com.android.server.wm.DisplayFrames com.android.server.wm.DisplayContent.mDisplayFrames' on a null object reference".
                // If this happens, assume the keyboard isn't showing.
                newState.copy(showingKeyboard = false)
            }
        } catch (_: NullPointerException) {
            // It's possible for `inputMethodWindowVisibleHeight` to throw an NPE on certain Android versions.
            // If that happens, assume the keyboard isn't visible.
            // "Attempt to read from field 'com.android.server.wm.DisplayFrames com.android.server.wm.DisplayContent.mDisplayFrames' on a null object reference"
            newState.copy(showingKeyboard = false)
        }

        //Check if the screen is on.
        val isScreenOn = power.isInteractive
        if (frameDelegate.commonState.isScreenOn != isScreenOn) {
            //Make sure to turn off temp hide if it was on.
            newState = newState.copy(isTempHide = false)
            newFrameCommonState = newFrameCommonState.copy(isScreenOn = isScreenOn)
            drawerDelegate.updateCommonState { it.copy(isScreenOn = isScreenOn) }
        }

        //Check if the lock screen is shown.
        val isOnKeyguard = kgm.isKeyguardLocked

        if (isOnKeyguard != drawerDelegate.commonState.wasOnKeyguard) {
            drawerDelegate.updateCommonState { it.copy(wasOnKeyguard = isOnKeyguard) }
        }

        if (isOnKeyguard != frameDelegate.commonState.wasOnKeyguard) {
            newFrameCommonState = newFrameCommonState.copy(wasOnKeyguard = isOnKeyguard)
            //Update the keyguard dismissal Activity that the lock screen
            //has been dismissed.
            if (!isOnKeyguard) {
                eventManager.sendEvent(Event.LockscreenDismissed)
            }
        }

        val rotation = defaultDisplayCompat.rotation
        newFrameCommonState = newFrameCommonState.copy(screenOrientation = rotation)
        drawerDelegate.updateCommonState { it.copy(screenOrientation = rotation) }

        if (isDebug && event != null) {
            // Nest this in the debug check so that loop doesn't have to run always.
            try {
                logUtils.debugLog("Source Node ID: ${event.sourceNodeId}, Window ID: ${event.windowId}, Source ID Name: ${event.source?.viewIdResourceName}")
                logUtils.debugLog(
                    "Records: ${
                        run {
                            val records = ArrayList<String>()
                            for (i in 0 until event.recordCount) {
                                val record = event.getRecord(i)
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

        logUtils.debugLog("Accessibility event: $event, isScreenOn: ${isScreenOn}, wasOnKeyguard: $isOnKeyguard, ${drawerDelegate.state}")

        frameDelegate.updateStateAndWindowState(
            wm = wm,
            updateAccessibility = true,
            transform = { newState },
            commonTransform = { newFrameCommonState },
        )

        newState = frameDelegate.state.copy()

        //The below block can (very rarely) take over half a second to execute, so only run it
        //if we actually need to (i.e. on the lock screen and screen is on).
        if ((isOnKeyguard || prefManager.showInNotificationCenter) && isScreenOn && prefManager.widgetFrameEnabled /* This is only needed when the frame is enabled */) {
            val windowInfo = getWindows()?.let {
                getWindows(it, isOnKeyguard).also { windowInfo ->
                    logUtils.debugLog("Got windows $windowInfo", null)
                }
            }

            windowInfo?.sysUiWindowViewIds?.let { sysUiWindowViewIds ->
                //Update any ID list widgets on the new IDs
                coroutineScope {
                    launch {
                        eventManager.sendEvent(Event.DebugIdsUpdated(sysUiWindowViewIds))
                        IDListProvider.sendUpdate(this@runAccessibilityJob)
                    }
                }
            }

            if (isDebug) {
                windowInfo?.sysUiWindowNodes?.let { sysUiWindowNodes ->
                    logUtils.debugLog(
                        sysUiWindowNodes.filter { it.isVisibleToUser }.map { it.viewIdResourceName }
                            .toString()
                    )
                }

                windowInfo?.sysUiWindowViewIds?.let {sysUiWindowViewIds ->
                    coroutineScope {
                        launch(Dispatchers.Main) {
                            frameDelegate.setNewDebugIdItems(sysUiWindowViewIds.toList())
                        }
                    }
                }
            }

            windowInfo?.let {
                newState = newState.copy(
                    //Samsung's Screen-Off Memo is really just a normal Activity that shows over the lock screen.
                    //However, it's not an Application-type window for some reason, so it won't hide with the
                    //currentAppLayer check. Explicitly check for its existence here.
                    isOnScreenOffMemo = isOnKeyguard && windowInfo.hasScreenOffMemoWindow,
                    isOnEdgePanel = windowInfo.hasEdgePanelWindow,
                    isOnFaceWidgets = windowInfo.hasFaceWidgetsWindow,
                    //Generate "layer" values for the System UI window and for the topmost app window, if
                    //it exists.
                    //currentAppLayer *should* be -1 even if there's an app open in the background,
                    //since getWindows() is only meant to return windows that can actually be
                    //interacted with. The only time it should be anything else (usually 1) is
                    //if an app is displaying above the keyguard, such as the incoming call
                    //screen or the camera.
                    currentAppLayer = if (windowInfo.topAppWindowIndex != -1) windowInfo.windows.size - windowInfo.topAppWindowIndex else windowInfo.topAppWindowIndex,
                    currentSysUiLayer = if (windowInfo.minSysUiWindowIndex != -1) windowInfo.windows.size - windowInfo.minSysUiWindowIndex else windowInfo.minSysUiWindowIndex,
                    currentSystemLayer = if (windowInfo.topNonSysUiWindowIndex != -1) windowInfo.windows.size - windowInfo.topNonSysUiWindowIndex else windowInfo.topNonSysUiWindowIndex,
                    //This is mostly a debug value to see which app LSWidg thinks is on top.
                    currentAppPackage = windowInfo.topAppWindowPackageName,
                    hidingForPresentApp = windowInfo.hasHideForPresentApp,
                    onMainLockscreen = windowInfo.nodeState.onMainLockscreen,
                    showingNotificationsPanel = windowInfo.nodeState.showingNotificationsPanel,
                    notificationsPanelFullyExpanded = windowInfo.nodeState.hasMoreButton && !windowInfo.nodeState.hasClearAllButton,
                    hideForPresentIds = windowInfo.nodeState.hideForPresentIds,
                    hideForNonPresentIds = windowInfo.nodeState.hideForNonPresentIds,
                )
            }

            logUtils.debugLog("NewState $newState", null)

            launch {
                frameDelegate.updateStateAndWindowState(
                    wm = wm,
                    updateAccessibility = true,
                    transform = { newState },
                    commonTransform = { newFrameCommonState },
                )
            }

            windowInfo?.let {
                windowInfo.sysUiWindowNodes.forEachParallel { node ->
                    try {
                        node.isSealed = false
                    } catch (_: Throwable) {
                    }

                    try {
                        @Suppress("DEPRECATION")
                        node.recycle()
                    } catch (_: IllegalStateException) {
                    }
                }

                windowInfo.windows.forEachParallel {
                    try {
                        it.root?.isSealed = false
                    } catch (_: Throwable) {
                    }

                    try {
                        @Suppress("DEPRECATION")
                        it.root?.recycle()
                    } catch (_: IllegalStateException) {
                    }
                }
            }
        }

        // Some logic for making the drawer go away or system dialogs dismiss when widgets launch Activities indirectly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && event != null) {
            val matchesWindowsChanged =
                event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                        && ((event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_ADDED != 0)
                        || (event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_ACTIVE != 0))
            val matchesWindowStateChanged =
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            logUtils.debugLog(
                "Checking for dismiss eligibility.\n" +
                        "matchesWindowsChanges: $matchesWindowsChanged\n" +
                        "matchesWindowStateChanged: $matchesWindowStateChanged\n" +
                        "packageName: ${event.packageName}\n" +
                        "handlingDrawerClick: ${drawerDelegate.commonState.handlingClick}\n" +
                        "handlingFrameClick: ${frameDelegate.commonState.handlingClick}"
            )

            if ((matchesWindowsChanged || matchesWindowStateChanged)
                && event.packageName != packageName
                && (drawerDelegate.commonState.handlingClick || frameDelegate.commonState.handlingClick)
            ) {
                logUtils.debugLog("Starting dismiss Activity because of window change.")
                DismissOrUnlockActivity.launch(this@runAccessibilityJob)

                if (drawerDelegate.commonState.handlingClick) {
                    logUtils.debugLog("Hiding drawer because of window change")
                    eventManager.sendEvent(Event.CloseDrawer)
                }

                drawerDelegate.updateCommonState { it.copy(handlingClick = false) }
                frameDelegate.updateCommonState { it.copy(handlingClick = false) }
            }
        }

        if (event != null && (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            && event.packageName != null
        ) {
            drawerDelegate.updateCommonState { it.copy(handlingClick = false) }
            frameDelegate.updateCommonState { it.copy(handlingClick = false) }
        }

        try {
            //Make sure to recycle the copy of the event.
            @Suppress("DEPRECATION")
            event?.recycle()
        } catch (e: IllegalStateException) {
            //Sometimes the event is already recycled somehow.
        }
    }
}
