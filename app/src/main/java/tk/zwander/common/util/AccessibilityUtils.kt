package tk.zwander.common.util

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.SparseArray
import android.view.Display
import android.view.WindowInsets
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.util.forEach
import dev.zwander.lswinterconnect.LogUtils
import dev.zwander.lswinterconnect.peekLogUtils
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.data.window.WindowInfo
import tk.zwander.common.data.window.WindowRootPair
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.appwidget.IDListProvider
import tk.zwander.lockscreenwidgets.services.Accessibility
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate
import tk.zwander.widgetdrawer.util.DrawerDelegate

object AccessibilityUtils {
    // The window/node tree walk in processWindows() does a lot of blocking Binder IPC
    // (AccessibilityWindowInfo.getRoot(), AccessibilityNodeInfo.getChild()), and previously
    // ran entirely on Dispatchers.Main (inherited from runAccessibilityJob), blocking the
    // main thread for up to a second per accessibility event.
    // A genuinely multithreaded dispatcher was tried here before and reverted (see "Crash fixes"
    // in git history): onAccessibilityEvent() cancels the previous event's job before starting a
    // new one, but cancellation can't interrupt a blocking call already in flight, so a stale
    // walk's getChild() could still be running on one thread while a new event's walk started on
    // another, racing on the same node tree/cache and throwing "already sealed" exceptions.
    // limitedParallelism(1) is a single shared slot across every call (every event's job), so a
    // new walk has to wait for a stale one to finish rather than running alongside it on another
    // thread - serialized, like Main was, but without blocking Main itself.
    private val windowProcessingDispatcher = Dispatchers.IO.limitedParallelism(1)

    data class NodeState(
        val onMainLockscreen: AtomicBoolean = atomic(false),
        val showingNotificationsPanel: AtomicBoolean = atomic(false),
        val showingSecurityInput: AtomicBoolean = atomic(false),
        val hasMoreButton: AtomicBoolean = atomic(false),
        val hideForPresentIds: AtomicBoolean = atomic(false),
        val hideForNonPresentIds: AtomicBoolean = atomic(false),
        val hasClearAllButton: AtomicBoolean = atomic(false),
        val hasSettingsContainerButton: AtomicBoolean = atomic(false),
        val onFaceWidgets: AtomicBoolean = atomic(false),
        val hasNotificationsShowing: AtomicBoolean = atomic(false),
        val showingPowerMenu: AtomicBoolean = atomic(false),
        val showingEdgePanel: AtomicBoolean = atomic(false),
        val sawAnyVisibleViewId: AtomicBoolean = atomic(false),
    )

    /**
     * Describes what this accessibility pass actually needs to find. Any flag that isn't
     * required can be ignored for the purposes of early-exiting the node walk.
     */
    private data class WalkRequirements(
        val collectAllVisibleIds: Boolean,
        val collectNodes: Boolean,
        val requireMainLockScreen: Boolean,
        val requireNotificationsPanel: Boolean,
        val requireSecurityInput: Boolean,
        val requireExpandedPanel: Boolean,
        val requireFaceWidgets: Boolean,
        val requireEdgePanel: Boolean,
        val requirePowerMenu: Boolean,
        /**
         * Whether the walk may skip parts of the tree that can't change any decision. This is
         * false when the full visible-ID set is needed (debugging / ID list widgets) or when the
         * user configured IDs, since hide-for-ID decisions need a complete view of the tree.
         */
        val allowSkipping: Boolean,
    ) {
        /**
         * Every "hide on X" preference can only be confirmed by *finding* X. Until each enabled
         * one has been found, stopping early could leave a frame visible that should be hidden,
         * so all of them (plus the positive show conditions) must be resolved first.
         */
        fun isSatisfied(state: NodeState): Boolean {
            return (!requireMainLockScreen || state.onMainLockscreen.value) &&
                    (!requireNotificationsPanel || state.showingNotificationsPanel.value) &&
                    (!requireSecurityInput || state.showingSecurityInput.value) &&
                    (!requireExpandedPanel || state.hasMoreButton.value) &&
                    (!requireFaceWidgets || state.onFaceWidgets.value) &&
                    (!requireEdgePanel || state.showingEdgePanel.value) &&
                    (!requirePowerMenu || state.showingPowerMenu.value)
        }

        fun canStopEarly(state: NodeState): Boolean {
            return allowSkipping && isSatisfied(state)
        }
    }

    private fun Context.buildWalkRequirements(
        frameDelegates: Map<Int, WidgetFrameDelegate>,
        drawerDelegate: DrawerDelegate,
    ): WalkRequirements {
        val framePrefs = frameDelegates.values.map { it.framePrefs }
        val collectAllVisibleIds = isDebug || DebugIDsManager.shouldCollectIds

        return WalkRequirements(
            collectAllVisibleIds = collectAllVisibleIds,
            collectNodes = isDebug,
            requireMainLockScreen = framePrefs.any { it.showOnMainLockScreen },
            requireNotificationsPanel = framePrefs.any {
                it.hideOnNotificationShade || it.showInNotificationShade || it.maskedMode
            } || (prefManager.drawerEnabled &&
                    drawerDelegate.isAttached &&
                    prefManager.drawerHideWhenNotificationPanelOpen),
            requireSecurityInput = framePrefs.any { it.hideOnSecurityPage },
            // Only hasMoreButton can positively prove that the panel is fully expanded
            // without having to see the rest of the tree.
            requireExpandedPanel = framePrefs.any { it.showInNotificationShade },
            requireFaceWidgets = framePrefs.any { it.hideOnFaceWidgets },
            requireEdgePanel = framePrefs.any { it.hideOnEdgePanel },
            requirePowerMenu = framePrefs.any { !it.showOverPowerMenu },
            allowSkipping = !collectAllVisibleIds &&
                    prefManager.presentIds.isEmpty() &&
                    prefManager.nonPresentIds.isEmpty(),
        )
    }

    @SuppressLint("DiscouragedApi")
    data object IDMaps {
        val mainLockScreenIds = unitMapOf(
            "com.android.systemui:id/notification_panel",
            "com.android.systemui:id/left_button",
            "com.android.systemui:id/camera_button",
            "com.android.systemui:id/keyguard_indication_text_bottom",
            "com.android.systemui:id/keyguard_carrier_text",
            "com.android.systemui:id/keyguard_owner_info",
            "com.android.systemui:id/footer_views",
            "com.android.systemui:id/lock_clock_blur",
        )

        val securityInputIds = unitMapOf(
            "com.android.systemui:id/keyguard_host_view",
            "com.android.systemui:id/keyguard_security_container",
            "element:BouncerContent",
        )

        val notificationsPanelIds = unitMapOf(
            "com.android.systemui:id/quick_settings_panel",
            "com.android.systemui:id/settings_button",
            "com.android.systemui:id/tile_label",
            "com.android.systemui:id/header_label",
            "com.android.systemui:id/split_shade_status_bar",
            "com.android.systemui:id/notification_shade_shortcut",
            "com.android.systemui:id/qs_footer".takeIf {
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
            },
            "com.android.systemui:id/quick_qs_panel".takeIf {
                Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2 &&
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA
            },
            // HyperOS Control Center
            "miui.systemui.plugin:id/main_panel_container",
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

        val powerMenuIds = unitMapOf(
            "com.android.systemui:id/sec_global_actions_icon",
            "com.android.systemui:id/global_actions_container",
        )

        val powerMenuTextIds by lazy {
            unitMapOf(
                App.instance.resources.getIdentifier("global_action_power_off", "string", "android"),
                App.instance.resources.getIdentifier("power_dialog", "string", "android"),
            )
        }

        val edgePanelIds = unitMapOf(
            "com.sec.android.app.launcher:id/panel_rootview",
            "com.sec.android.app.launcher:id/panel_root_view",
        )

        val sysUiPackageNames = unitMapOf(
            "com.android.systemui",
            "miui.systemui.plugin",
            "com.sec.android.app.launcher",
            "com.android.keyguard",
            "android",
        )

        val faceWidgetIds = unitMapOf(
            "com.samsung.android.app.aodservice:id/facewidget_clock_container",
        )
    }

    private fun Context.processNode(
        nodeState: NodeState,
        node: AccessibilityNodeInfo,
        isPixelUI: Boolean,
    ) {
        val sourceNodeId = node.sourceNodeId
        val viewIdResourceName = node.viewIdResourceName

        if (!nodeState.onFaceWidgets.value) {
            if (node.hasWildcardId("com.samsung.android.app.aodservice:id/facewidget_") &&
                !node.hasVisibleIds(IDMaps.faceWidgetIds)
            ) {
                logUtils.debugLog(
                    message = { "Found FaceWidgets $sourceNodeId $viewIdResourceName" },
                )
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
                logUtils.debugLog(
                    message = { "Found main lock screen $sourceNodeId $viewIdResourceName" },
                )
                nodeState.onMainLockscreen.value = true
            }
        }

        if (!nodeState.showingNotificationsPanel.value) {
            if (node.hasVisibleIds(IDMaps.notificationsPanelIds)) {
                //Used for "Hide When Notification Shade Shown" so we know when it's actually expanded.
                //Some devices don't even have left shortcuts, so also check for keyguard_indication_area.
                //Just like the showingSecurityInput check, this is probably unreliable for some devices.
                logUtils.debugLog(
                    message = { "Found notifications panel $sourceNodeId $viewIdResourceName" },
                )
                nodeState.showingNotificationsPanel.value = true
            }
        }

        if (!nodeState.showingSecurityInput.value) {
            if (node.hasVisibleIds(IDMaps.securityInputIds)) {
                logUtils.debugLog(
                    message = { "Found security input $sourceNodeId $viewIdResourceName" },
                )
                nodeState.showingSecurityInput.value = true
            }
        }

        if (!nodeState.hasNotificationsShowing.value) {
            if (node.hasVisibleIds(IDMaps.notificationIds)) {
                logUtils.debugLog(
                    message = { "Found a notification $sourceNodeId $viewIdResourceName" },
                )
                nodeState.hasNotificationsShowing.value = true
            }
        }

        //If the option to show when the NC is fully expanded is enabled,
        //check if the frame can actually show. This checks to the "more_button"
        //ID, which is unique to One UI (it's the three-dot button), and is only
        //visible when the NC is fully expanded.
        if (!nodeState.hasMoreButton.value) {
            if (node.hasVisibleIds(IDMaps.moreButtonIds)) {
                logUtils.debugLog(
                    message = { "Found NC more button $sourceNodeId $viewIdResourceName" },
                )
                nodeState.hasMoreButton.value = true
            }
        }

        if (!nodeState.hasSettingsContainerButton.value) {
            if (node.hasVisibleIds(IDMaps.settingsContainerButtonIds)) {
                logUtils.debugLog(
                    message = { "Found settings container $sourceNodeId $isPixelUI $viewIdResourceName" },
                )
                nodeState.hasSettingsContainerButton.value = isPixelUI
            }
        }

        if (!nodeState.hasClearAllButton.value) {
            if (node.hasVisibleIds(IDMaps.clearAllButtonIds)) {
                logUtils.debugLog(
                    message = { "Found clear all button $sourceNodeId $viewIdResourceName" },
                )
                nodeState.hasClearAllButton.value = true
            }
        }

        if (!nodeState.showingPowerMenu.value) {
            if (node.hasVisibleIds(IDMaps.powerMenuIds)) {
                logUtils.debugLog(
                    message = { "Found power menu node $sourceNodeId $viewIdResourceName" },
                )
                nodeState.showingPowerMenu.value = true
            }

            if (
                node.parentNodeId != 0L &&
                node.parentNodeId == globalState.powerMenuNodeId.value &&
                node.isVisibleToUser
            ) {
                logUtils.debugLog(
                    message = { "Found power menu node by global ID $sourceNodeId $viewIdResourceName" },
                )
                nodeState.showingPowerMenu.value = true
            }
        }

        if (!nodeState.showingEdgePanel.value) {
            if (node.hasVisibleIds(IDMaps.edgePanelIds)) {
                logUtils.debugLog(
                    message = { "Found edge panel $sourceNodeId $viewIdResourceName" },
                )
                nodeState.showingEdgePanel.value = true
            }
        }
    }

    /**
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] objects corresponding to the System UI windows.
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] corresponding to the topmost app window.
     * Find the [AccessibilityWindowInfo] and [AccessibilityNodeInfo] corresponding to the topmost non-System UI window.
     */
    private fun Context.processWindows(
        windows: List<AccessibilityWindowInfo>,
        requirements: WalkRequirements,
    ): WindowInfo {
        val nodeState = NodeState()

        val sysUiWindowViewIds = LinkedHashSet<String>()
        val sysUiWindowNodes = ArrayList<AccessibilityNodeInfo>()

        var topAppWindowIndex: Int = -1
        var topNonSysUiWindowIndex: Int = -1

        var topAppWindowPackageName: String? = null

        var hasScreenOffMemoWindow = false
        var hasFaceWidgetsWindow = false
        var hasEdgePanelWindow = false
        var hasHideForPresentApp = false

        var minSysUiWindowIndex = -1

        val presentIds = prefManager.presentIds
        val nonPresentIds = prefManager.nonPresentIds
        val configuredIds = presentIds + nonPresentIds
        val foundConfiguredIds = HashSet<String>()
        val isPixelUI = isPixelUI

        val processed = windows.mapIndexed { index, rawWindow ->
            val safeRoot = try {
                rawWindow.root
            } catch (e: Exception) {
                logUtils.debugLog(
                    message = "Error getting window root",
                    throwable = e,
                )
                null
            }
            val isSysUi = safeRoot != null &&
                    IDMaps.sysUiPackageNames[safeRoot.packageName] != null
            val window = WindowRootPair(rawWindow, safeRoot, index)

            if (isSysUi) {
                if (minSysUiWindowIndex == -1) {
                    minSysUiWindowIndex = index
                }

                if (requirements.collectAllVisibleIds ||
                    !requirements.canStopEarly(nodeState)
                ) {
                    addAllNodesToList(
                        parentNode = safeRoot,
                        list = sysUiWindowNodes,
                        ids = sysUiWindowViewIds,
                        nodeState = nodeState,
                        requirements = requirements,
                        configuredIds = configuredIds,
                        foundConfiguredIds = foundConfiguredIds,
                    ) { node ->
                        processNode(
                            nodeState = nodeState,
                            node = node,
                            isPixelUI = isPixelUI,
                        )
                    }
                }

                // User-configured IDs are looked up directly instead of requiring a full
                // visible-ID walk. This also keeps them working when the walk exits early.
                if (!requirements.collectAllVisibleIds && configuredIds.isNotEmpty()) {
                    configuredIds.forEach { id ->
                        if (id !in foundConfiguredIds &&
                            safeRoot.hasVisibleNodeWithId(id)
                        ) {
                            foundConfiguredIds.add(id)
                            nodeState.sawAnyVisibleViewId.value = true
                        }
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
                logUtils.debugLog(
                    message = { "Found app window ${window.index} ${safeRoot?.packageName} (type ${window.window.type})" },
                    debugMessage = { "Found app window $window" },
                )
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
                safeRoot.isVisibleToUser
            ) {
                hasEdgePanelWindow = true
            }

            if (safeRoot?.packageName == "com.samsung.android.app.notes" &&
                safeRoot.isVisibleToUser
            ) {
                hasScreenOffMemoWindow = true
            }

            if (safeRoot?.packageName == "com.samsung.android.app.aodservice" &&
                safeRoot.isVisibleToUser
            ) {
                hasFaceWidgetsWindow = true
            }

            if (prefManager.hideFrameOnApps.contains(safeRoot?.packageName) &&
                safeRoot?.isVisibleToUser == true
            ) {
                hasHideForPresentApp = true
            }

            window
        }

        val visibleConfiguredIds = if (requirements.collectAllVisibleIds) {
            sysUiWindowViewIds
        } else {
            foundConfiguredIds
        }

        nodeState.hideForPresentIds.value = presentIds.any { it in visibleConfiguredIds }
        nodeState.hideForNonPresentIds.value =
            nodeState.sawAnyVisibleViewId.value && nonPresentIds.any { it !in visibleConfiguredIds }

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
     * Iteratively add all [AccessibilityNodeInfo]s contained in [parentNode] to [list].
     *
     * We use this instead of [AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId]
     * for performance and reliability reasons, except for the small, user-configured ID set.
     *
     * Nodes are visited breadth-first so shallow System UI containers (keyguard indicators,
     * quick settings, etc.) are found before descending into large notification subtrees.
     * The walk also stops as soon as [WalkRequirements.canStopEarly] is true.
     *
     * @param parentNode the root [AccessibilityNodeInfo] whose children we want to add to
     * a list. List will include this node as well.
     * @param list the list that will contain the child nodes.
     * @param ids the list of all visible IDs
     */
    private fun addAllNodesToList(
        parentNode: AccessibilityNodeInfo,
        list: ArrayList<AccessibilityNodeInfo>,
        ids: LinkedHashSet<String>,
        nodeState: NodeState,
        requirements: WalkRequirements,
        configuredIds: Set<String>,
        foundConfiguredIds: MutableSet<String>,
        nodeAddedCallback: (AccessibilityNodeInfo) -> Unit,
    ) {
        val pendingNodes = ArrayDeque<AccessibilityNodeInfo>()
        pendingNodes.addLast(parentNode)

        fun recycleIfNeeded(node: AccessibilityNodeInfo) {
            if (!requirements.collectNodes && node !== parentNode) {
                node.recycleQuietly()
            }
        }

        while (pendingNodes.isNotEmpty()) {
            val node = pendingNodes.removeFirst()

            // All checks in processNode() require a visible node, and only visible IDs are
            // collected. An invisible node therefore cannot contribute anything.
            if (node !== parentNode && !node.isVisibleToUser) {
                recycleIfNeeded(node)
                continue
            }

            if (requirements.collectNodes) {
                list.add(node)
            }
            nodeAddedCallback(node)

            if (node.isVisibleToUser) {
                node.viewIdResourceName?.let { viewId ->
                    nodeState.sawAnyVisibleViewId.value = true

                    if (requirements.collectAllVisibleIds) {
                        ids.add(viewId)
                    }
                    if (viewId in configuredIds) {
                        foundConfiguredIds.add(viewId)
                    }
                }
            }

            if (requirements.canStopEarly(nodeState)) {
                recycleIfNeeded(node)
                while (pendingNodes.isNotEmpty()) {
                    recycleIfNeeded(pendingNodes.removeFirst())
                }
                return
            }

            if (!(requirements.allowSkipping && shouldPruneSection(node, nodeState))) {
                for (i in 0 until node.childCount) {
                    getChildOrNull(node, i)?.let(pendingNodes::addLast)
                }
            }

            recycleIfNeeded(node)
        }
    }

    /**
     * Sections whose children can't contribute anything once we've seen the section itself.
     * Only used when the full visible-ID set and user-configured IDs aren't needed.
     */
    private fun shouldPruneSection(
        node: AccessibilityNodeInfo,
        nodeState: NodeState,
    ): Boolean {
        val id = node.viewIdResourceName ?: return false

        return when {
            // Notification rows are large and can't contain any of the other state we track.
            IDMaps.notificationIds.containsKey(id) -> nodeState.hasNotificationsShowing.value

            // Once notifications have been found and clear-all has been resolved, the rest
            // of the notification stack is just more notification rows.
            id == "com.android.systemui:id/notification_stack_scroller" ||
                    id == "com.android.systemui:id/notification_list" ->
                nodeState.hasNotificationsShowing.value && nodeState.hasClearAllButton.value

            else -> false
        }
    }

    private fun AccessibilityNodeInfo.hasVisibleNodeWithId(id: String): Boolean {
        return try {
            val nodes = findAccessibilityNodeInfosByViewId(id) ?: return false

            try {
                nodes.any { it.isVisibleToUser }
            } finally {
                nodes.forEach { it.recycleQuietly() }
            }
        } catch (e: Exception) {
            peekLogUtils?.debugLog(
                message = "Error finding node by ID $id",
                throwable = e,
            )
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleQuietly() {
        try {
            recycle()
        } catch (_: IllegalStateException) {
        }
    }

    private fun getChildOrNull(parentNode: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        return try {
            parentNode.getChild(index)
        } catch (e: SecurityException) {
            //Sometimes a SecurityException gets thrown here (on Huawei devices)
            //so just return null if it happens
            peekLogUtils?.debugLog(message = "Error getting child node", throwable = e)
            null
        } catch (e: NullPointerException) {
            //Sometimes a NullPointerException is thrown here with this error:
            //"Attempt to read from field 'com.android.server.appwidget.AppWidgetServiceImpl$ProviderId
            //com.android.server.appwidget.AppWidgetServiceImpl$Provider.id' on a null object reference"
            //so just return null if that happens.
            peekLogUtils?.debugLog(message = "Error getting child node", throwable = e)
            null
        } catch (_: IllegalStateException) {
            //The system can re-seal a node while we're walking the tree. Briefly unseal the
            //parent so its child list is refreshed, then retry once. Always restore the sealed
            //state, otherwise a stale sealed node could make later passes throw.
            try {
                parentNode.isSealed = true
                parentNode.getChild(index)
            } catch (e: Exception) {
                peekLogUtils?.debugLog(message = "Error getting child node", throwable = e)
                null
            } finally {
                try {
                    parentNode.isSealed = false
                } catch (_: Exception) {
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            peekLogUtils?.debugLog(message = "Error getting child node", throwable = e)
            null
        }
    }

    suspend fun Context.runWindowOperation(
        frameDelegates: Map<Int, WidgetFrameDelegate>,
        drawerDelegate: DrawerDelegate,
        getWindows: () -> SparseArray<List<AccessibilityWindowInfo>>?,
        initialRun: Boolean = false,
    ) {
        val isScreenOn = lsDisplayManager.isAnyDisplayOn
        val isOnKeyguard = globalState.wasOnKeyguard.value

        logUtils.debugLog(
            message = { "Trying to run window operation " },
            extras = {
                mapOf(
                    "initialRun" to initialRun,
                    "isScreenOn" to isScreenOn,
                    "isOnKeyguard" to isOnKeyguard,
                    "widgetFrameEnabled" to prefManager.widgetFrameEnabled,
                    "drawerEnabled" to prefManager.drawerEnabled,
                    "drawerIsAttached" to drawerDelegate.isAttached,
                    "anyFrameAttached" to frameDelegates.any { it.value.isAttached },
                    "drawerHideWhenNotificationPanelOpen" to prefManager.drawerHideWhenNotificationPanelOpen,
                )
            },
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
                message = "Running window operation.",
                throwable = if (initialRun) LogUtils.DefaultException() else null,
            )

            val walkRequirements = buildWalkRequirements(frameDelegates, drawerDelegate)

            getWindows()?.forEach { displayId, windows ->
                val windowInfo = withContext(windowProcessingDispatcher) {
                    processWindows(windows, walkRequirements)
                }.also { windowInfo ->
                    logUtils.debugLog(
                        message = {
                            "Got windows for display $displayId: ${windowInfo.windows.size} windows, " +
                                    "${windowInfo.sysUiWindowNodes.size} nodes, " +
                                    "${windowInfo.sysUiWindowViewIds.size} IDs"
                        },
                        debugMessage = { "Got windows for display $displayId: $windowInfo" },
                    )
                }

                val relevantFrameDelegates =
                    frameDelegates.filter { (val frame = value) -> frame.display?.displayId == displayId }

                if (walkRequirements.collectAllVisibleIds) {
                    windowInfo.sysUiWindowViewIds.let { sysUiWindowViewIds ->
                        logUtils.debugLog(
                            message = { "Found ${sysUiWindowViewIds.size} IDs on display $displayId" },
                            debugMessage = {
                                "Found IDs on display $displayId\n${sysUiWindowViewIds.joinToString("\n")}"
                            },
                        )
                        //Update any ID list widgets on the new IDs
                        coroutineScope {
                            if (DebugIDsManager.setItems(displayId, sysUiWindowViewIds)) {
                                launch(Dispatchers.Main) {
                                    IDListProvider.sendUpdate()
                                }
                            }
                        }
                    }
                }

                if (isDebug) {
                    windowInfo.sysUiWindowNodes.let { sysUiWindowNodes ->
                        val logCopy = sysUiWindowNodes.filter { it.isVisibleToUser }.map { it.viewIdResourceName }
                        logUtils.debugLog(
                            message = "$displayId: $logCopy",
                            throwable = null,
                        )
                    }
                }

                val notificationsWereOpen = globalState.showingNotificationsPanel.value[displayId]
                val notificationsAreOpen = windowInfo.nodeState.showingNotificationsPanel.value

                //Samsung's Screen-Off Memo is really just a normal Activity that shows over the lock screen.
                //However, it's not an Application-type window for some reason, so it won't hide with the
                //currentAppLayer check. Explicitly check for its existence here.
                globalState.isOnScreenOffMemo[displayId] = isOnKeyguard && windowInfo.hasScreenOffMemoWindow
                globalState.isOnEdgePanel[displayId] =
                    windowInfo.hasEdgePanelWindow || windowInfo.nodeState.showingEdgePanel.value
                globalState.isOnFaceWidgets[displayId] = windowInfo.hasFaceWidgetsWindow
                        || windowInfo.nodeState.onFaceWidgets.value
                //Generate "layer" values for the System UI window and for the topmost app window, if
                //it exists.
                //currentAppLayer *should* be -1 even if there's an app open in the background,
                //since getWindows() is only meant to return windows that can actually be
                //interacted with. The only time it should be anything else (usually 1) is
                //if an app is displaying above the keyguard, such as the incoming call
                //screen or the camera.
                globalState.currentAppLayer[displayId] =
                    if (windowInfo.topAppWindowIndex != -1) windowInfo.windows.size - windowInfo.topAppWindowIndex else windowInfo.topAppWindowIndex
                globalState.currentSysUiLayer[displayId] =
                    if (windowInfo.minSysUiWindowIndex != -1) windowInfo.windows.size - windowInfo.minSysUiWindowIndex else windowInfo.minSysUiWindowIndex
                globalState.currentSystemLayer[displayId] =
                    if (windowInfo.topNonSysUiWindowIndex != -1) windowInfo.windows.size - windowInfo.topNonSysUiWindowIndex else windowInfo.topNonSysUiWindowIndex
                //This is mostly a debug value to see which app LSWidg thinks is on top.
                globalState.currentAppPackage[displayId] = windowInfo.topAppWindowPackageName
                globalState.hidingForPresentApp[displayId] = windowInfo.hasHideForPresentApp
                globalState.onMainLockScreen[displayId] = windowInfo.nodeState.onMainLockscreen.value
                globalState.showingPowerMenu[displayId] = windowInfo.nodeState.showingPowerMenu.value
                globalState.showingSecurityInput[displayId] = windowInfo.nodeState.showingSecurityInput.value
                globalState.accessibilitySeesNotificationsOnMainLockScreen[displayId] =
                    windowInfo.nodeState.onMainLockscreen.value &&
                            windowInfo.nodeState.hasNotificationsShowing.value
                globalState.showingNotificationsPanel[displayId] = notificationsAreOpen
                globalState.notificationsPanelFullyExpanded[displayId] =
                    (windowInfo.nodeState.hasMoreButton.value) || (windowInfo.nodeState.hasSettingsContainerButton.value &&
                            !windowInfo.nodeState.hasClearAllButton.value)
                globalState.hideForPresentIds[displayId] = windowInfo.nodeState.hideForPresentIds.value
                globalState.hideForNonPresentIds[displayId] = windowInfo.nodeState.hideForNonPresentIds.value

                relevantFrameDelegates.forEach { (val frameDelegate = value) ->
                    frameDelegate.updateWindowState(
                        updateAccessibility = true,
                    )
                }

                // Drawer only supports default display right now.
                if (displayId == Display.DEFAULT_DISPLAY &&
                    !drawerDelegate.viewModel.scrollingOpen.value &&
                    notificationsWereOpen != notificationsAreOpen &&
                    notificationsAreOpen &&
                    drawerDelegate.isAttached &&
                    prefManager.drawerHideWhenNotificationPanelOpen
                ) {
                    eventManager.sendEvent(Event.CloseDrawer)
                }

                logUtils.debugLog(
                    message = {
                        "NewState for display $displayId\n" +
                                "appLayer: ${globalState.currentAppLayer.value[displayId]}\n" +
                                "sysUiLayer: ${globalState.currentSysUiLayer.value[displayId]}\n" +
                                "systemLayer: ${globalState.currentSystemLayer.value[displayId]}\n" +
                                "onKeyguard: ${globalState.wasOnKeyguard.value}\n" +
                                "onMainLockScreen: ${globalState.onMainLockScreen.value[displayId]}\n" +
                                "notificationsOpen: ${globalState.showingNotificationsPanel.value[displayId]}\n" +
                                "hideForPresentIds: ${globalState.hideForPresentIds.value[displayId]}\n" +
                                "hideForNonPresentIds: ${globalState.hideForNonPresentIds.value[displayId]}\n" +
                                "frames: ${relevantFrameDelegates.size}, attached: ${relevantFrameDelegates.count { it.value.isAttached }}"
                    },
                    debugMessage = {
                        "NewState for display $displayId\n" +
                                "${relevantFrameDelegates.values.firstOrNull()?.state}\n" +
                                "${drawerDelegate.state}\n" +
                                "$globalState"
                    },
                )

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
        frameDelegates: Map<Int, WidgetFrameDelegate>,
        drawerDelegate: DrawerDelegate,
        kgm: KeyguardManager,
        imm: InputMethodManager,
        getWindows: () -> SparseArray<List<AccessibilityWindowInfo>>?,
    ) = async(Dispatchers.Main) {
        with(context) {
            logUtils.debugLog(message = "Running accessibility job")

            val powerMenuTexts = IDMaps.powerMenuTextIds.map { id -> resources.getString(id.key) }

            if (
                IDMaps.sysUiPackageNames.containsKey(event.packageName?.toString()) &&
                (event.text.any { powerMenuTexts.contains(it) } ||
                        event.source?.window?.title?.let { powerMenuTexts.contains(it) } == true)
            ) {
                logUtils.debugLog(message = "Received power menu ID ${event.sourceNodeId}", throwable = null)
                globalState.powerMenuNodeId.value = event.sourceNodeId
            }

            if (lsDisplayManager.multiDisplaySupported) {
                lsDisplayManager.displayAndWmCache.value.values.forEach { [dis, wm] ->
                    globalState.showingKeyboard[dis?.displayId ?: Display.DEFAULT_DISPLAY] =
                        wm?.currentWindowMetrics
                            ?.windowInsets?.getInsets(WindowInsets.Type.ime())
                            ?.toRect()
                            ?.isEmpty
                            .let { it == false }
                }
            } else {
                globalState.showingKeyboard[Display.DEFAULT_DISPLAY] = try {
                    imm.inputMethodWindowVisibleHeight > 0
                } catch (e: Throwable) {
                    logUtils.debugLog(
                        message = "Unable to check if keyboard is showing, assuming it's not",
                        throwable = e,
                    )
                    // Fetching the IME height can cause the system to throw an NPE:
                    // "Attempt to read from field 'com.android.server.wm.DisplayFrames com.android.server.wm.DisplayContent.mDisplayFrames' on a null object reference".
                    // If this happens, assume the keyboard isn't showing.
                    false
                }
            }

            //Check if the lock screen is shown.
            globalState.wasOnKeyguard.value = kgm.isKeyguardLocked

            if (isDebug) {
                // Nest this in the debug check so that loop doesn't have to run always.
                try {
                    logUtils.debugLog(
                        message = "Source Node ID: ${event.sourceNodeId}, Window ID: ${event.windowId}, Source ID Name: ${event.source?.viewIdResourceName}",
                        throwable = null,
                    )

                    if (event.recordCount > 0) {
                        logUtils.debugLog(
                            message = "Records: ${
                                    run {
                                        val records = ArrayList<String>()
                                        for (i in 0 until event.recordCount) {
                                            val record = event.getRecord(i)
                                            records.add("$record ${record.sourceNodeId} ${record.windowId} ${record.source?.viewIdResourceName}")
                                        }
                                        records.joinToString(",,,,,,,,")
                                    }
                                }",
                            throwable = null,
                        )
                    }
                } catch (e: Exception) {
                    logUtils.debugLog(message = "Error printing debug info", throwable = e)
                }
            }

            val eventTypeName = AccessibilityEvent.eventTypeToString(event.eventType)
            val eventPackage = event.packageName
            val screenOn = lsDisplayManager.isAnyDisplayOn
            val wasOnKeyguard = globalState.wasOnKeyguard.value

            logUtils.debugLog(
                message = {
                    "Accessibility event: $eventTypeName, " +
                            "package: $eventPackage, " +
                            "isScreenOn: $screenOn, " +
                            "wasOnKeyguard: $wasOnKeyguard"
                },
                debugMessage = {
                    "Accessibility event: $event, " +
                            "isScreenOn: $screenOn, " +
                            "wasOnKeyguard: $wasOnKeyguard, " +
                            "${drawerDelegate.state}"
                },
            )

            if (prefManager.widgetFrameEnabled) {
                frameDelegates.forEach { (val frameDelegate = value) ->
                    frameDelegate.updateState(
                        transform = {
                            it.copy(
                                screenOrientation = frameDelegate.screenOrientation ?: it.screenOrientation,
                            )
                        },
                    )

                    // Eagerly hide the widget frame before parsing SysUI nodes.
                    frameDelegate.hideIfShouldNotShow()
                }
            }

            runWindowOperation(
                frameDelegates = frameDelegates,
                drawerDelegate = drawerDelegate,
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
                val eventPackageName = event.packageName

                logUtils.debugLog(
                    message = {
                        "Checking for dismiss eligibility.\n" +
                                "matchesWindowsChanges: $matchesWindowsChanged\n" +
                                "matchesWindowStateChanged: $matchesWindowStateChanged\n" +
                                "packageName: ${eventPackageName}\n" +
                                "handlingClick: ${globalState.handlingClick.value}"
                    },
                )

                if ((matchesWindowsChanged || matchesWindowStateChanged)
                    && event.packageName != packageName
                    && globalState.handlingClick.value.isNotEmpty()
                ) {
                    logUtils.debugLog(message = "Starting dismiss Activity because of window change.", throwable = null)
                    DismissOrUnlockActivity.launch(context)

                    if (globalState.handlingClick.value.containsKey(DrawerDelegate.ID)) {
                        logUtils.debugLog(message = "Hiding drawer because of window change", throwable = null)
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

            if (prefManager.widgetFrameEnabled) {
                frameDelegates.forEach { (val frameDelegate = value) ->
                    frameDelegate.updateStateAndWindowState(true)
                }
            }
        }

    }
}

//Check if the Accessibility service is enabled
val Context.isAccessibilityEnabled: Boolean
    get() = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
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
            $$"com.android.settings.Settings$AccessibilityInstalledServiceActivity",
        )
        startActivity(accIntent)
    } catch (e: Exception) {
        logUtils.debugLog(message = "Error opening Installed Services:", throwable = e)

        try {
            val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(accIntent)
        } catch (e: Exception) {
            logUtils.debugLog(message = "Error opening Accessibility Settings", throwable = e)

            try {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS)
                startActivity(settingsIntent)
            } catch (e: Exception) {
                logUtils.debugLog(message = "Error opening Settings", throwable = e)

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
    return ids[viewIdResourceName] != null && isVisibleToUser
}

fun AccessibilityNodeInfo.hasVisibleIds(ids: Set<String>): Boolean {
    return ids.contains(viewIdResourceName) && isVisibleToUser
}

fun AccessibilityNodeInfo.hasWildcardId(id: String): Boolean {
    return viewIdResourceName?.contains(id) == true && isVisibleToUser
}

fun <T> unitMapOf(vararg keys: T?): Map<T, Unit> {
    return keys.filterNotNull().associateWith {}
}
