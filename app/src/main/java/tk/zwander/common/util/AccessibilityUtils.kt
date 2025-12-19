package tk.zwander.common.util

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.data.window.WindowInfo
import tk.zwander.common.data.window.WindowRootPair
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.appwidget.IDListProvider
import tk.zwander.lockscreenwidgets.services.Accessibility
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate
import tk.zwander.widgetdrawer.util.DrawerDelegate
import java.util.concurrent.ConcurrentLinkedQueue

object AccessibilityUtils {
    data class NodeState(
        val onMainLockscreen: AtomicBoolean = atomic(false),
        val showingNotificationsPanel: AtomicBoolean = atomic(false),
        val hasMoreButton: AtomicBoolean = atomic(false),
        val hideForPresentIds: AtomicBoolean = atomic(false),
        val hideForNonPresentIds: AtomicBoolean = atomic(false),
        val hasClearAllButton: AtomicBoolean = atomic(false),
        val hasSettingsContainerButton: AtomicBoolean = atomic(false),
        val onFaceWidgets: AtomicBoolean = atomic(false),
        val hasNotificationsShowing: AtomicBoolean = atomic(false),
    )

    data object IDMaps {
        val mainLockScreenIds = unitMapOf(
            "com.android.systemui:id/notification_panel",
            "com.android.systemui:id/left_button",
            "com.android.systemui:id/camera_button",
            "com.android.systemui:id/keyguard_indication_text_bottom",
            "com.android.systemui:id/keyguard_carrier_text",
        )

        val notificationsPanelIds = unitMapOf(
            "com.android.systemui:id/quick_settings_panel",
            "com.android.systemui:id/settings_button",
            "com.android.systemui:id/tile_label",
            "com.android.systemui:id/header_label",
            "com.android.systemui:id/split_shade_status_bar",
            "com.android.systemui:id/quick_qs_panel".takeIf { Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2 },
        )

        val notificationIds = unitMapOf(
            "com.android.systemui:id/notification_guts",
            "com.android.systemui:id/expandableNotificationRow",
            "con.android.systemui:id/notificationShelf",
        )

        val moreButtonIds = unitMapOf(
            "com.android.systemui:id/more_button",
            "com.android.systemui:id/edit_button",
        )

        val settingsContainerButtonIds = unitMapOf(
            "com.android.systemui:id/settings_button_container",
        )

        val clearAllButtonIds = unitMapOf(
            "com.android.systemui:id/clear_all",
        )
    }

    private fun processNode(
        nodeState: NodeState,
        node: AccessibilityNodeInfo,
        presentIds: Map<String, Unit>,
        nonPresentIds: Map<String, Unit>,
        isPixelUI: Boolean,
    ) {
        if (!nodeState.onFaceWidgets.value) {
            if (node.hasWildcardId("com.samsung.android.app.aodservice:id/facewidget_")) {
                nodeState.onFaceWidgets.value = true
            }
        }

        //If the user has enabled the option to hide the frame on security (pin, pattern, password)
        //input, we need to check if they're on the main lock screen. This is more reliable than
        //checking for the existence of a security input, since there are a lot of different possible
        //IDs, and OEMs change them. "notification_panel" and "left_button" are largely unchanged,
        //although this method isn't perfect.
        if (!nodeState.onMainLockscreen.value) {
            if (node.hasVisibleIds(IDMaps.mainLockScreenIds)) {
                nodeState.onMainLockscreen.value = true
            }
        }

        if (!nodeState.showingNotificationsPanel.value) {
            if (node.hasVisibleIds(IDMaps.notificationsPanelIds)) {
                //Used for "Hide When Notification Shade Shown" so we know when it's actually expanded.
                //Some devices don't even have left shortcuts, so also check for keyguard_indication_area.
                //Just like the showingSecurityInput check, this is probably unreliable for some devices.
                nodeState.showingNotificationsPanel.value = true
            }
        }

        if (!nodeState.hasNotificationsShowing.value) {
            if (node.hasVisibleIds(IDMaps.notificationIds)) {
                nodeState.hasNotificationsShowing.value = true
            }
        }

        //If the option to show when the NC is fully expanded is enabled,
        //check if the frame can actually show. This checks to the "more_button"
        //ID, which is unique to One UI (it's the three-dot button), and is only
        //visible when the NC is fully expanded.
        if (!nodeState.hasMoreButton.value) {
            if (node.hasVisibleIds(IDMaps.moreButtonIds)) {
                nodeState.hasMoreButton.value = true
            }
        }

        if (!nodeState.hasSettingsContainerButton.value) {
            if (node.hasVisibleIds(IDMaps.settingsContainerButtonIds)) {
                nodeState.hasSettingsContainerButton.value = isPixelUI
            }
        }

        if (!nodeState.hasClearAllButton.value) {
            if (node.hasVisibleIds(IDMaps.clearAllButtonIds)) {
                nodeState.hasClearAllButton.value = true
            }
        }

        //Check to see if any of the user-specified IDs are present.
        //If any are, the frame will be hidden.
        if (!nodeState.hideForPresentIds.value && presentIds.isNotEmpty()) {
            if (node.hasVisibleIds(presentIds)) {
                nodeState.hideForPresentIds.value = true
            }
        }

        //Check to see if any of the user-specified IDs aren't present
        //(or are present but not visible). If any aren't, the frame will be hidden.
        if (!nodeState.hideForNonPresentIds.value && nonPresentIds.isNotEmpty()) {
            if (!node.hasVisibleIds(nonPresentIds)) {
                nodeState.hideForNonPresentIds.value = true
            }
        }
    }

    /**
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] objects corresponding to the System UI windows.
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] corresponding to the topmost app window.
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] corresponding to the topmost non-System UI window.
     */
    private suspend fun Context.processWindows(
        windows: List<AccessibilityWindowInfo>,
    ): WindowInfo {
        val nodeState = NodeState()

        val systemUiWindows = ConcurrentLinkedQueue<WindowRootPair>()
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

        val presentIds = prefManager.presentIds.associateWith {}
        val nonPresentIds = prefManager.nonPresentIds.associateWith {}

        val processed = windows.mapIndexedParallel { index, rawWindow ->
            val safeRoot = try {
                rawWindow.root
            } catch (_: NullPointerException) {
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

                addAllNodesToList(
                    safeRoot,
                    sysUiWindowNodes,
                    sysUiWindowViewIds,
                    sysUiWindowAwaits,
                ) { node ->
                    launch(Dispatchers.IO) {
                        processNode(
                            nodeState = nodeState,
                            node = node,
                            presentIds = presentIds,
                            nonPresentIds = nonPresentIds,
                            isPixelUI = isPixelUI,
                        )
                    }
                }
            }

            if ((topAppWindowIndex == -1 || topAppWindowIndex > index) &&
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
                (topNonSysUiWindowIndex == -1 || topNonSysUiWindowIndex > index) &&
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
    private fun CoroutineScope.addAllNodesToList(
        parentNode: AccessibilityNodeInfo,
        list: ConcurrentLinkedQueue<AccessibilityNodeInfo>,
        ids: ConcurrentLinkedQueue<String>,
        awaits: ConcurrentLinkedQueue<Deferred<*>>,
        nodeAddedCallback: (CoroutineScope.(AccessibilityNodeInfo) -> Unit),
    ) {
        list.add(parentNode)
        nodeAddedCallback(parentNode)

        if (parentNode.isVisibleToUser && parentNode.viewIdResourceName != null) {
            ids.add(parentNode.viewIdResourceName)
        }

        for (i in 0 until parentNode.childCount) {
            awaits.add(async {
                val child = try {
                    parentNode.getChild(i)
                } catch (_: SecurityException) {
                    //Sometimes a SecurityException gets thrown here (on Huawei devices)
                    //so just return null if it happens
                    null
                } catch (_: NullPointerException) {
                    //Sometimes a NullPointerException is thrown here with this error:
                    //"Attempt to read from field 'com.android.server.appwidget.AppWidgetServiceImpl$ProviderId
                    //com.android.server.appwidget.AppWidgetServiceImpl$Provider.id' on a null object reference"
                    //so just return null if that happens.
                    null
                } catch (_: IllegalStateException) {
                    try {
                        parentNode.isSealed = true
                        parentNode.getChild(i).also {
                            parentNode.isSealed = false
                        }
                    } catch (_: Exception) {
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

    suspend fun Context.runWindowOperation(
        frameDelegates: Map<Int, MainWidgetFrameDelegate>,
        drawerDelegate: DrawerDelegate,
        isScreenOn: Boolean,
        isOnKeyguard: Boolean,
        getWindows: () -> List<AccessibilityWindowInfo>?,
        initialRun: Boolean = false,
    ) {
        logUtils.debugLog(
            "Trying to run window operation " +
                    "${initialRun}, " +
                    "${isScreenOn}, " +
                    "${isOnKeyguard}, " +
                    "${prefManager.widgetFrameEnabled}, " +
                    "${prefManager.drawerEnabled}, " +
                    "${drawerDelegate.isAttached}, " +
                    "${prefManager.drawerHideWhenNotificationPanelOpen}",
            null,
        )

        //The below block can (very rarely) take over half a second to execute, so only run it
        //if we actually need to (i.e. on the lock screen and screen is on).
        if (initialRun || (isScreenOn &&
                    (((isOnKeyguard || FrameSpecificPreferences.doAnyFramesHaveSettingEnabled(
                        this,
                        PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER,
                    )) &&
                            prefManager.widgetFrameEnabled /* This is only needed when the frame is enabled */) ||
                            (prefManager.drawerEnabled && drawerDelegate.isAttached && prefManager.drawerHideWhenNotificationPanelOpen)))
        ) {

            logUtils.debugLog(
                "Running window operation.",
                if (initialRun) LogUtils.DefaultException() else null
            )

            val windowInfo = getWindows()?.let {
                processWindows(it).also { windowInfo ->
                    logUtils.debugLog("Got windows $windowInfo", null)
                }
            }

            windowInfo?.sysUiWindowViewIds?.let { sysUiWindowViewIds ->
                logUtils.debugLog("Found IDs\n${sysUiWindowViewIds.joinToString("\n")}", null)
                //Update any ID list widgets on the new IDs
                coroutineScope {
                    DebugIDsManager.setItems(sysUiWindowViewIds)
                    launch(Dispatchers.Main) {
                        IDListProvider.sendUpdate(this@runWindowOperation)
                    }
                }
            }

            if (isDebug) {
                windowInfo?.sysUiWindowNodes?.let { sysUiWindowNodes ->
                    logUtils.debugLog(
                        sysUiWindowNodes.filter { it.isVisibleToUser }.map { it.viewIdResourceName }
                            .toString(),
                        null,
                    )
                }
            }

            windowInfo?.let {
                val notificationsWereOpen = globalState.showingNotificationsPanel.value
                val notificationsAreOpen = windowInfo.nodeState.showingNotificationsPanel.value

                //Samsung's Screen-Off Memo is really just a normal Activity that shows over the lock screen.
                //However, it's not an Application-type window for some reason, so it won't hide with the
                //currentAppLayer check. Explicitly check for its existence here.
                globalState.isOnScreenOffMemo.value =
                    isOnKeyguard && windowInfo.hasScreenOffMemoWindow
                globalState.isOnEdgePanel.value = windowInfo.hasEdgePanelWindow
                globalState.isOnFaceWidgets.value =
                    windowInfo.hasFaceWidgetsWindow || windowInfo.nodeState.onFaceWidgets.value
                //Generate "layer" values for the System UI window and for the topmost app window, if
                //it exists.
                //currentAppLayer *should* be -1 even if there's an app open in the background,
                //since getWindows() is only meant to return windows that can actually be
                //interacted with. The only time it should be anything else (usually 1) is
                //if an app is displaying above the keyguard, such as the incoming call
                //screen or the camera.
                globalState.currentAppLayer.value =
                    if (windowInfo.topAppWindowIndex != -1) windowInfo.windows.size - windowInfo.topAppWindowIndex else windowInfo.topAppWindowIndex
                globalState.currentSysUiLayer.value =
                    if (windowInfo.minSysUiWindowIndex != -1) windowInfo.windows.size - windowInfo.minSysUiWindowIndex else windowInfo.minSysUiWindowIndex
                globalState.currentSystemLayer.value =
                    if (windowInfo.topNonSysUiWindowIndex != -1) windowInfo.windows.size - windowInfo.topNonSysUiWindowIndex else windowInfo.topNonSysUiWindowIndex
                //This is mostly a debug value to see which app LSWidg thinks is on top.
                globalState.currentAppPackage.value = windowInfo.topAppWindowPackageName
                globalState.hidingForPresentApp.value = windowInfo.hasHideForPresentApp
                globalState.onMainLockScreen.value = windowInfo.nodeState.onMainLockscreen.value
                globalState.accessibilitySeesNotificationsOnMainLockScreen.value =
                    windowInfo.nodeState.onMainLockscreen.value &&
                            windowInfo.nodeState.hasNotificationsShowing.value
                globalState.showingNotificationsPanel.value = notificationsAreOpen
                globalState.notificationsPanelFullyExpanded.value =
                    (windowInfo.nodeState.hasMoreButton.value) || (windowInfo.nodeState.hasSettingsContainerButton.value &&
                            !windowInfo.nodeState.hasClearAllButton.value)
                globalState.hideForPresentIds.value = windowInfo.nodeState.hideForPresentIds.value
                globalState.hideForNonPresentIds.value =
                    windowInfo.nodeState.hideForNonPresentIds.value

                frameDelegates.forEach { (_, frameDelegate) ->
                    frameDelegate.updateWindowState(
                        updateAccessibility = true,
                    )
                }

                if (!drawerDelegate.viewModel.scrollingOpen.value &&
                    notificationsWereOpen != notificationsAreOpen &&
                    notificationsAreOpen &&
                    drawerDelegate.isAttached &&
                    prefManager.drawerHideWhenNotificationPanelOpen
                ) {
                    eventManager.sendEvent(Event.CloseDrawer)
                }
            }

            logUtils.debugLog(
                "NewState\n" +
                        "${frameDelegates.values.first().state}\n" +
                        "${drawerDelegate.state}\n" +
                        "$globalState",
                null,
            )

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
    }

    fun CoroutineScope.runAccessibilityJob(
        context: Context,
        event: AccessibilityEvent,
        frameDelegates: Map<Int, MainWidgetFrameDelegate>,
        drawerDelegate: DrawerDelegate,
        power: PowerManager,
        kgm: KeyguardManager,
        imm: InputMethodManager,
        getWindows: () -> List<AccessibilityWindowInfo>?,
    ) = async(Dispatchers.Main) {
        with(context) {
            logUtils.debugLog("Running accessibility job")

            //This block here runs even when unlocked, but it only takes a millisecond at most,
            //so it shouldn't be noticeable to the user. We use this to check the current keyguard
            //state and, if applicable, send the keyguard dismissal broadcast.
            globalState.showingKeyboard.value = try {
                imm.inputMethodWindowVisibleHeight > 0
            } catch (e: Throwable) {
                logUtils.debugLog("Unable to check if keyboard is showing, assuming it's not", e)
                // Fetching the IME height can cause the system to throw an NPE:
                // "Attempt to read from field 'com.android.server.wm.DisplayFrames com.android.server.wm.DisplayContent.mDisplayFrames' on a null object reference".
                // If this happens, assume the keyboard isn't showing.
                false
            }

            //Check if the lock screen is shown.
            globalState.wasOnKeyguard.value = kgm.isKeyguardLocked

            if (isDebug) {
                // Nest this in the debug check so that loop doesn't have to run always.
                try {
                    logUtils.debugLog(
                        "Source Node ID: ${event.sourceNodeId}, Window ID: ${event.windowId}, Source ID Name: ${event.source?.viewIdResourceName}",
                        null
                    )

                    if (event.recordCount > 0) {
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
                            }",
                            null,
                        )
                    }
                } catch (e: Exception) {
                    logUtils.debugLog("Error printing debug info", e)
                }
            }

            logUtils.debugLog(
                "Accessibility event: $event, isScreenOn: ${requireLsDisplayManager.isAnyDisplayOn.value}, wasOnKeyguard: ${globalState.wasOnKeyguard.value}, ${drawerDelegate.state}",
                null
            )

            frameDelegates.forEach { (_, frameDelegate) ->
                frameDelegate.updateStateAndWindowState(
                    updateAccessibility = true,
                    transform = {
                        it.copy(
                            screenOrientation = frameDelegate.display.screenOrientation,
                        )
                    },
                )
            }

            runWindowOperation(
                frameDelegates = frameDelegates,
                drawerDelegate = drawerDelegate,
                isOnKeyguard = globalState.wasOnKeyguard.value,
                isScreenOn = requireLsDisplayManager.isAnyDisplayOn.value,
                getWindows = getWindows,
            )

            // Some logic for making the drawer go away or system dialogs dismiss when widgets launch Activities indirectly.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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
                            "handlingClick: ${globalState.handlingClick.value}",
                    null,
                )

                if ((matchesWindowsChanged || matchesWindowStateChanged)
                    && event.packageName != packageName
                    && globalState.handlingClick.value.isNotEmpty()
                ) {
                    logUtils.debugLog("Starting dismiss Activity because of window change.", null)
                    DismissOrUnlockActivity.launch(context)

                    if (globalState.handlingClick.value.containsKey(-2)) {
                        logUtils.debugLog("Hiding drawer because of window change", null)
                        eventManager.sendEvent(Event.CloseDrawer)
                    }

                    globalState.handlingClick.value = mapOf()
                }
            }

            if ((event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                        || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) && event.packageName != null
            ) {
                globalState.handlingClick.value = mapOf()
            }

            try {
                //Make sure to recycle the copy of the event.
                @Suppress("DEPRECATION")
                event.recycle()
            } catch (_: IllegalStateException) {
                //Sometimes the event is already recycled somehow.
            }

            frameDelegates.forEach { (_, frameDelegate) ->
                frameDelegate.updateStateAndWindowState(true)
            }
        }
    }
}

//Check if the Accessibility service is enabled
val Context.isAccessibilityEnabled: Boolean
    get() = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )?.contains(ComponentName(this, Accessibility::class.java).flattenToString()) == true

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
            $$"com.android.settings.Settings$AccessibilityInstalledServiceActivity"
        )
        startActivity(accIntent)
    } catch (e: Exception) {
        logUtils.debugLog("Error opening Installed Services:", e)

        try {
            val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(accIntent)
        } catch (e: Exception) {
            logUtils.debugLog("Error opening Accessibility Settings", e)

            try {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                startActivity(settingsIntent)
            } catch (e: Exception) {
                logUtils.debugLog("Error opening Settings", e)

                Toast.makeText(this, R.string.unable_to_open_settings, Toast.LENGTH_SHORT).show()
            }
        }
    }
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

fun AccessibilityNodeInfo.hasVisibleIds(ids: Map<String, Unit>): Boolean {
    return ids.containsKey(viewIdResourceName) && isVisibleToUser
}

fun AccessibilityNodeInfo.hasWildcardId(id: String): Boolean {
    return viewIdResourceName?.contains(id) == true && isVisibleToUser
}

fun <T> unitMapOf(vararg keys: T?): Map<T, Unit> {
    return keys.filterNotNull().associateWith {}
}
