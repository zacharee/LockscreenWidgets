package tk.zwander.lockscreenwidgets.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.*
import android.os.PowerManager
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.activities.DismissOrUnlockActivity
import tk.zwander.lockscreenwidgets.appwidget.IDWidgetFactory
import tk.zwander.lockscreenwidgets.appwidget.IDListProvider
import tk.zwander.lockscreenwidgets.util.*

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
class Accessibility : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener, CoroutineScope by MainScope() {
    companion object {
        const val ACTION_LOCKSCREEN_DISMISSED = "LOCKSCREEN_DISMISSED"
    }

    /**
     * On Android 8.0+, it's pretty easy to dismiss the lock screen with a simple API call.
     * On earlier Android versions, it's not so easy, and we need a way to detect when the
     * lock screen has successfully been dismissed.
     *
     * This is just a simple wrapper class around a BroadcastReceiver for [DismissOrUnlockActivity]
     * to implement so it can receive the keyguard dismissal event we generate from this service.
     */
    abstract class OnLockscreenDismissListener : BroadcastReceiver() {
        final override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_LOCKSCREEN_DISMISSED) {
                onDismissed()
            }
        }

        fun register(context: Context) {
            LocalBroadcastManager.getInstance(context)
                .registerReceiver(this, IntentFilter(ACTION_LOCKSCREEN_DISMISSED))
        }

        fun unregister(context: Context) {
            LocalBroadcastManager.getInstance(context)
                .unregisterReceiver(this)
        }

        abstract fun onDismissed()
    }

    private val kgm by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val delegate by lazy { WidgetFrameDelegate.getInstance(this) }

    //Receive updates from our notification listener service on how many
    //notifications are currently shown to the user. This count excludes
    //notifications not visible on the lock screen.
    //If the notification count is > 0, and the user has the option enabled,
    //make sure to hide the widget frame.
    private val notificationCountListener =
        object : NotificationListener.NotificationCountListener() {
            override fun onUpdate(count: Int) {
                delegate.notificationCount = count
                if (prefManager.hideOnNotifications && count > 0) {
                    removeOverlay()
                } else if (delegate.canShow()) {
                    addOverlay()
                }
            }
        }

    //Listen for the screen turning on and off.
    //This shouldn't really be necessary, but there are some quirks in how
    //Android works that makes it helpful.
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    //Sometimes ACTION_SCREEN_OFF gets received *after* the display turns on,
                    //so this check is here to make sure the screen is actually off when this
                    //action is received.
                    if (!power.isInteractive) {
                        if (isDebug) {
                            Log.e(App.DEBUG_LOG_TAG, "Screen off")
                        }

                        accessibilityJob?.cancel()

                        //If the device has some sort of AOD or ambient display, by the time we receive
                        //an accessibility event and see that the display is off, it's usually too late
                        //and the current screen content has "frozen," causing the widget frame to show
                        //where it shouldn't. ACTION_SCREEN_OFF is called early enough that we can remove
                        //the frame before it's frozen in place.
                        removeOverlay()
                        delegate.forceWakelock(wm, false)
                        delegate.isTempHide = false
                        delegate.isScreenOn = false
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    latestScreenOnTime = System.currentTimeMillis()

                    if (isDebug) {
                        Log.e(App.DEBUG_LOG_TAG, "Screen on")
                    }

                    delegate.isScreenOn = true
                    if (delegate.canShow()) {
                        accessibilityJob?.cancel()
                        addOverlay()
                    }
                }
            }
        }
    }

    private var latestScreenOnTime: Long = 0L

    private var accessibilityJob: Job? = null

    @SuppressLint("ClickableViewAccessibility", "NewApi")
    override fun onCreate() {
        super.onCreate()

        delegate.onCreate()
        prefManager.prefs.registerOnSharedPreferenceChangeListener(this)

        delegate.apply {
            binding.frame.onAfterResizeListener = {
                adapter.onResizeObservable.notifyObservers()
                delegate.updateWallpaperLayerIfNeeded()
            }

            binding.frame.onAfterMoveListener = {
                delegate.updateWallpaperLayerIfNeeded()
            }

            binding.frame.onMoveListener = { velX, velY ->
                params.x += velX.toInt()
                params.y += velY.toInt()

                updateOverlay()

                prefManager.setCorrectFramePos(saveMode, params.x, params.y)
            }

            binding.frame.onLeftDragListener = { velX ->
                params.width -= velX
                params.x += (velX / 2)

                prefManager.setCorrectFrameWidth(saveMode, pxAsDp(params.width))

                updateOverlay()
            }

            binding.frame.onRightDragListener = { velX ->
                params.width += velX
                params.x += (velX / 2)

                prefManager.setCorrectFrameWidth(saveMode, pxAsDp(params.width))

                updateOverlay()
            }

            binding.frame.onTopDragListener = { velY ->
                params.height -= velY
                params.y += (velY / 2)

                prefManager.setCorrectFrameHeight(saveMode, pxAsDp(params.height))

                updateOverlay()
            }

            binding.frame.onBottomDragListener = { velY ->
                params.height += velY
                params.y += (velY / 2)

                prefManager.setCorrectFrameHeight(saveMode, pxAsDp(params.height))

                updateOverlay()
            }

            binding.frame.onInterceptListener = { down ->
                forceWakelock(wm, down)
            }

            binding.frame.onTempHideListener = {
                isTempHide = true
                removeOverlay()
            }
        }

        notificationCountListener.register(this)
        registerReceiver(
            screenStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })

        delegate.wasOnKeyguard = kgm.isKeyguardLocked
        delegate.isScreenOn = power.isInteractive
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            notificationTimeout = prefManager.accessibilityEventDelay.toLong()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        //Since we're launching our logic on the main Thread, it's possible
        //that [event] will be reused by Android, causing some crash issues.
        //Make a copy that is recycled later.
        val eventCopy = AccessibilityEvent.obtain(event)

        if (System.currentTimeMillis() - latestScreenOnTime < 10)
            return

        accessibilityJob?.cancel()
        accessibilityJob = launch {
            //This block here runs even when unlocked, but it only takes a millisecond at most,
            //so it shouldn't be noticeable to the user. We use this to check the current keyguard
            //state and, if applicable, send the keyguard dismissal broadcast.

            //Check if the screen is on.
            val isScreenOn = power.isInteractive
            if (delegate.isScreenOn != isScreenOn) {
                //Make sure to turn off temp hide if it was on.
                delegate.isTempHide = false
                delegate.isScreenOn = isScreenOn
            }

            //Check if the lock screen is shown.
            val isOnKeyguard = kgm.isKeyguardLocked
            if (isOnKeyguard != delegate.wasOnKeyguard) {
                delegate.wasOnKeyguard = isOnKeyguard
                //Update the keyguard dismissal Activity that the lock screen
                //has been dismissed.
                if (!isOnKeyguard) {
                    LocalBroadcastManager.getInstance(this@Accessibility)
                        .sendBroadcast(Intent(ACTION_LOCKSCREEN_DISMISSED))
                }
            }

            delegate.screenOrientation = wm.defaultDisplay.rotation

            if (isDebug) {
                Log.e(App.DEBUG_LOG_TAG, "Accessibility event: $eventCopy, isScreenOn: ${isScreenOn}, wasOnKeyguard: $isOnKeyguard")
            }

            //The below block can (very rarely) take over half a second to execute, so only run it
            //if we actually need to (i.e. on the lock screen and screen is on).
            if ((delegate.wasOnKeyguard || prefManager.showInNotificationCenter) && isScreenOn) {
                //Retrieve the current window set.
                val windows = windows
                //Get all windows with the System UI package name.
                val sysUiWindows = findSystemUiWindows(windows)
                //Get the topmost application window.
                val appWindow = findTopAppWindow(windows)
                //Get the topmost window that isn't an application and doesn't belong to System UI.
                val nonAppSystemWindow = findTopNonSystemUIWindow(windows)

                //Flatted the nodes/Views in the System UI windows into
                //a single list for easier handling.
                val sysUiNodes = ArrayList<AccessibilityNodeInfo>()
                sysUiWindows.mapNotNull { it.second }.forEach {
                    addAllNodesToList(it, sysUiNodes)
                }

                //Get all View IDs from successfully-flattened System UI nodes.
                val items = sysUiNodes.filter { it.isVisibleToUser }
                    .mapNotNull { it.viewIdResourceName }

                //Update any ID list widgets on the new IDs
                IDWidgetFactory.sList.apply {
                    clear()
                    addAll(items)
                }
                IDListProvider.sendUpdate(this@Accessibility)

                if (isDebug) {
                    Log.e(
                        App.DEBUG_LOG_TAG,
                        sysUiNodes.filter { it.isVisibleToUser }.map { it.viewIdResourceName }
                            .toString()
                    )

                    delegate.binding.frame.setNewDebugIdItems(items)
                }

                //The logic in this block only needs to run when on the lock screen.
                //Put it in an if-check to help performance.
                if (isOnKeyguard) {
                    //Find index of the topmost application window in the set of all windows.
                    val appIndex = windows.indexOf(appWindow)
                    //Find the *least* index of the System UI windows in the set of all windows.
                    val sysUiIndex = sysUiWindows.map { windows.indexOf(it.first) }.filter { it > -1 }
                        .minOrNull()
                        ?: -1
                    //Find index of the topmost system window.
                    val systemIndex = windows.indexOf(nonAppSystemWindow)

                    //Samsung's Screen-Off Memo is really just a normal Activity that shows over the lock screen.
                    //However, it's not an Application-type window for some reason, so it won't hide with the
                    //currentAppLayer check. Explicitly check for its existence here.
                    delegate.isOnScreenOffMemo = windows.any { win ->
                        win.safeRoot.use {
                            it?.packageName == "com.samsung.android.app.notes"
                        }
                    }

                    //Generate "layer" values for the System UI window and for the topmost app window, if
                    //it exists.
                    //currentAppLayer *should* be -1 even if there's an app open in the background,
                    //since getWindows() is only meant to return windows that can actually be
                    //interacted with. The only time it should be anything else (usually 1) is
                    //if an app is displaying above the keyguard, such as the incoming call
                    //screen or the camera.
                    delegate.currentAppLayer = if (appIndex != -1) windows.size - appIndex else appIndex
                    delegate.currentSysUiLayer = if (sysUiIndex != -1) windows.size - sysUiIndex else sysUiIndex
                    delegate.currentSystemLayer = if (systemIndex != -1) windows.size - systemIndex else systemIndex

                    if (isTouchWiz) {
                        val systemRoot = nonAppSystemWindow?.safeRoot

                        delegate.isOnEdgePanel = systemRoot?.packageName == "com.samsung.android.app.cocktailbarservice"
                                && systemIndex == 0

                        systemRoot?.recycle()
                    } else {
                        delegate.isOnEdgePanel = false
                    }

                    //This is mostly a debug value to see which app LSWidg thinks is on top.
                    delegate.currentAppPackage = appWindow?.safeRoot?.run {
                        packageName?.toString().also { recycle() }
                    }

                    //If the user has enabled the option to hide the frame on security (pin, pattern, password)
                    //input, we need to check if they're on the main lock screen. This is more reliable than
                    //checking for the existence of a security input, since there are a lot of different possible
                    //IDs, and OEMs change them. "notification_panel" and "left_button" are largely unchanged,
                    //although this method isn't perfect.
                    if (prefManager.hideOnSecurityPage) {
                        Log.e("LockscreenWidgets", "")
                        delegate.onMainLockscreen = sysUiNodes.any {
                            it.hasVisibleIds(
                                "com.android.systemui:id/notification_panel",
                                "com.android.systemui:id/left_button"
                            )
                        }
                    }

                    if (prefManager.hideOnNotificationShade) {
                        //Used for "Hide When Notification Shade Shown" so we know when it's actually expanded.
                        //Some devices don't even have left shortcuts, so also check for keyguard_indication_area.
                        //Just like the showingSecurityInput check, this is probably unreliable for some devices.
                        delegate.showingNotificationsPanel = sysUiNodes.any {
                            it.hasVisibleIds(
                                "com.android.systemui:id/quick_settings_panel",
                                "com.android.systemui:id/settings_button",
                                "com.android.systemui:id/tile_label"
                            )
                        }
                    }

                    if (prefManager.hideOnFaceWidgets) {
                        delegate.isOnFaceWidgets = windows.any {
                            it.safeRoot.use { node ->
                                node?.packageName == "com.samsung.android.app.aodservice"
                            }
                        }
                    }
                } else {
                    //If we're not on the lock screen, whether or not Screen-Off Memo is showing
                    //doesn't matter.
                    delegate.isOnScreenOffMemo = false
                }

                //If the option to show when the NC is fully expanded is enabled,
                //check if the frame can actually show. This checks to the "more_button"
                //ID, which is unique to One UI (it's the three-dot button), and is only
                //visible when the NC is fully expanded.
                if (prefManager.showInNotificationCenter) {
                    delegate.notificationsPanelFullyExpanded = sysUiNodes.any {
                        it.hasVisibleIds("com.android.systemui:id/more_button")
                    }
                }

                //Check to see if any of the user-specified IDs are present.
                //If any are, the frame will be hidden.
                val presentIds = prefManager.presentIds
                if (presentIds.isNotEmpty()) {
                    delegate.hideForPresentIds = sysUiNodes.any {
                        it.hasVisibleIds(presentIds)
                    }
                }

                //Check to see if any of the user-specified IDs aren't present
                //(or are present but not visible). If any aren't, the frame will be hidden.
                val nonPresentIds = prefManager.nonPresentIds
                if (nonPresentIds.isNotEmpty()) {
                    delegate.hideForNonPresentIds = sysUiNodes.none {
                        nonPresentIds.contains(it.viewIdResourceName) }
                            || sysUiNodes.any { nonPresentIds.contains(it.viewIdResourceName) && !it.isVisibleToUser }
                }

                //Recycle all windows and nodes.
                sysUiNodes.forEach { it.recycle() }
                sysUiWindows.forEach {
                    it.first?.recycle()
                }

                try {
                    appWindow?.recycle()
                } catch (e: IllegalStateException) {}
            }

            //Check whether the frame can show.
            if (delegate.canShow()) {
                delegate.updateAccessibilityPass()
                addOverlay()
            } else {
                removeOverlay()
                delegate.updateAccessibilityPass()
            }

            //Make sure to recycle the copy of the event.
            eventCopy.recycle()
        }
    }

    override fun onInterrupt() {}

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PrefManager.KEY_WIDGET_FRAME_ENABLED -> {
                if (delegate.canShow()) {
                    addOverlay()
                } else {
                    removeOverlay()
                }
            }
            PrefManager.KEY_ACCESSIBILITY_EVENT_DELAY -> {
                serviceInfo = serviceInfo.apply {
                    notificationTimeout = prefManager.accessibilityEventDelay.toLong()
                }
            }
            PrefManager.KEY_DEBUG_LOG -> {
                IDListProvider.sendUpdate(this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
        prefManager.prefs.unregisterOnSharedPreferenceChangeListener(this)
        notificationCountListener.unregister(this)
        unregisterReceiver(screenStateReceiver)
        delegate.onDestroy()
    }

    /**
     * Add the widget frame to the display.
     */
    private fun addOverlay() {
        mainHandler.postDelayed({
            delegate.addWindow(wm)
        }, 100)
    }

    /**
     * Update the window manager on any params changes
     * that may have occurred.
     */
    private fun updateOverlay() {
        delegate.binding.frame.updateWindow(wm, delegate.params)
    }

    /**
     * Remove the widget frame from the display.
     * Make sure the editing UI is hidden if currently
     * displayed.
     */
    private fun removeOverlay() {
        if (isDebug) {
            Log.e(App.DEBUG_LOG_TAG, "Removing overlay", Exception())
        }
        delegate.adapter.currentEditingInterfacePosition = -1
        delegate.binding.frame.removeWindow(wm)
    }

    /**
     * Find the [AccessibilityWindowInfo] corresponding to System UI
     *
     * @return the System UI window if it exists onscreen
     */
    private fun findSystemUiWindows(windows: List<AccessibilityWindowInfo> = this.windows): List<Pair<AccessibilityWindowInfo?, AccessibilityNodeInfo?>> {
        val list = ArrayList<Pair<AccessibilityWindowInfo?, AccessibilityNodeInfo?>>()

        windows.forEach {
            val safeRoot = it.safeRoot
            if (safeRoot?.packageName == "com.android.systemui") {
                list.add(it to safeRoot)
            } else {
                safeRoot?.recycle()
            }
        }

        return list
    }

    /**
     * Find the [AccessibilityWindowInfo] corresponding to the current topmost app
     * (the most recently-used one)
     *
     * @return the app window if it exists onscreen
     */
    private fun findTopAppWindow(windows: List<AccessibilityWindowInfo> = this.windows): AccessibilityWindowInfo? {
        windows.forEach {
            if (it.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                return it
            }
        }

        return null
    }

    private fun findTopNonSystemUIWindow(windows: List<AccessibilityWindowInfo> = this.windows): AccessibilityWindowInfo? {
        windows.forEach {
            if (it.type != AccessibilityWindowInfo.TYPE_APPLICATION
                && it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                val safeRoot = it.safeRoot
                if (safeRoot?.packageName != "com.android.systemui") {
                    return it.also { safeRoot?.recycle() }
                } else {
                    safeRoot.recycle()
                }
            }
        }

        return null
    }

    /**
     * Recursively hide_for_ids all [AccessibilityNodeInfo]s contained in a parent to a list.
     * We use this instead of [AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId]
     * for performance and reliability reasons.
     * @param parentNode the root [AccessibilityNodeInfo] whose children we want to hide_for_ids to
     * a list. List will include this node as well.
     * @param list the list that will contain the child nodes.
     */
    private fun addAllNodesToList(
        parentNode: AccessibilityNodeInfo,
        list: ArrayList<AccessibilityNodeInfo>
    ) {
        list.add(parentNode)
        for (i in 0 until parentNode.childCount) {
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
                    addAllNodesToList(child, list)
                } else {
                    list.add(child)
                }
            }
        }
    }
}