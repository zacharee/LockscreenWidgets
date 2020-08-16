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
import kotlinx.android.synthetic.main.widget_frame.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
                notificationCount = count
                if (prefManager.hideOnNotifications && count > 0) {
                    removeOverlay()
                } else if (canShow()) {
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
                    if (isDebug) {
                        Log.e(App.DEBUG_LOG_TAG, "Screen off")
                    }

                    //If the device has some sort of AOD or ambient display, by the time we receive
                    //an accessibility event and see that the display is off, it's usually too late
                    //and the current screen content has "frozen," causing the widget frame to show
                    //where it shouldn't. ACTION_SCREEN_OFF is called early enough that we can remove
                    //the frame before it's frozen in place.
                    removeOverlay()
                    isTempHide = false
                    isScreenOn = false
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (isDebug) {
                        Log.e(App.DEBUG_LOG_TAG, "Screen on")
                    }

                    isScreenOn = true
                    if (canShow())
                        addOverlay()
                }
            }
        }
    }

    private var notificationCount = 0
    private var onMainLockscreen = true
    private var showingNotificationsPanel = false
    private var wasOnKeyguard = true
    private var isScreenOn = true
        set(value) {
            field = value

            if (!value) {
                notificationsPanelFullyExpanded = false
            }
        }
    private var isTempHide = false
    private var hideForPresentIds = false
    private var hideForNonPresentIds = false
    private var isOnScreenOffMemo = false

    private var notificationsPanelFullyExpanded: Boolean
        get() = delegate.notificationsPanelFullyExpanded
        set(value) {
            delegate.notificationsPanelFullyExpanded = value
        }

    private var currentSysUiLayer = 1
    private var currentAppLayer = 0
    private var currentAppPackage: String? = null

    @SuppressLint("ClickableViewAccessibility", "NewApi")
    override fun onCreate() {
        super.onCreate()

        delegate.onCreate()
        prefManager.prefs.registerOnSharedPreferenceChangeListener(this)

        delegate.apply {
            view.frame.onAfterResizeListener = {
                adapter.onResizeObservable.notifyObservers()
                delegate.updateWallpaperLayerIfNeeded()
            }

            view.frame.onMoveListener = { velX, velY ->
                params.x += velX.toInt()
                params.y += velY.toInt()

                prefManager.setCorrectFrameX(saveForNC, params.x)
                prefManager.setCorrectFrameY(saveForNC, params.y)

                updateOverlay()
                delegate.updateWallpaperLayerIfNeeded()
            }

            view.frame.onLeftDragListener = { velX ->
                params.width -= velX
                params.x += (velX / 2)

                prefManager.setCorrectFrameWidth(saveForNC, pxAsDp(params.width))

                updateOverlay()
            }

            view.frame.onRightDragListener = { velX ->
                params.width += velX
                params.x += (velX / 2)

                prefManager.setCorrectFrameWidth(saveForNC, pxAsDp(params.width))

                updateOverlay()
            }

            view.frame.onTopDragListener = { velY ->
                params.height -= velY
                params.y += (velY / 2)

                prefManager.setCorrectFrameHeight(saveForNC, pxAsDp(params.height))

                updateOverlay()
            }

            view.frame.onBottomDragListener = { velY ->
                params.height += velY
                params.y += (velY / 2)

                prefManager.setCorrectFrameHeight(saveForNC, pxAsDp(params.height))

                updateOverlay()
            }

            view.frame.onInterceptListener = { down ->
                if (down) {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                } else {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
                }

                updateOverlay()
            }

            view.frame.onTempHideListener = {
                isTempHide = true
                removeOverlay()
            }
        }

        notificationCountListener.register(this)
        registerReceiver(
            screenStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })

        wasOnKeyguard = kgm.isKeyguardLocked
        isScreenOn = power.isInteractive
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

        launch {
            //This block here runs even when unlocked, but it only takes a millisecond at most,
            //so it shouldn't be noticeable to the user. We use this to check the current keyguard
            //state and, if applicable, send the keyguard dismissal broadcast.

            //Check if the screen is on.
            val isScreenOn = power.isInteractive
            if (this@Accessibility.isScreenOn != isScreenOn) {
                //Make sure to turn off temp hide if it was on.
                isTempHide = false
                this@Accessibility.isScreenOn = isScreenOn
            }

            //Check if the lock screen is shown.
            val isOnKeyguard = kgm.isKeyguardLocked
            if (isOnKeyguard != wasOnKeyguard) {
                wasOnKeyguard = isOnKeyguard
                //Update the keyguard dismissal Activity that the lock screen
                //has been dismissed.
                if (!isOnKeyguard) {
                    LocalBroadcastManager.getInstance(this@Accessibility)
                        .sendBroadcast(Intent(ACTION_LOCKSCREEN_DISMISSED))
                }
            }

            if (isDebug) {
                Log.e(App.DEBUG_LOG_TAG, "Accessibility event: $eventCopy, isScreenOn: ${isScreenOn}, wasOnKeyguard: $wasOnKeyguard")
            }

            //The below block can (very rarely) take over half a second to execute, so only run it
            //if we actually need to (i.e. on the lock screen and screen is on).
            if ((wasOnKeyguard || prefManager.showInNotificationCenter) && isScreenOn) {
                //Retrieve the current window set.
                val windows = windows
                //Get all windows with the System UI package name.
                val sysUiWindows = findSystemUiWindows(windows)
                //Get the topmost application window.
                val appWindow = findTopAppWindow(windows)

                //Flatted the nodes/Views in the System UI windows into
                //a single list for easier handling.
                val sysUiNodes = ArrayList<AccessibilityNodeInfo>()
                sysUiWindows.mapNotNull { it?.safeRoot }.forEach {
                    addAllNodesToList(it, sysUiNodes)
                }

                //Get all View IDs from successfully-flattened System UI nodes.
                val items = sysUiNodes.filter { it.isVisibleToUser }.mapNotNull { it.viewIdResourceName }

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

                    delegate.view.frame.setNewDebugIdItems(items)
                }

                //The logic in this block only needs to run when on the lock screen.
                //Put it in an if-check to help performance.
                if (isOnKeyguard) {
                    //Find index of the topmost application window in the set of all windows.
                    val appIndex = windows.indexOf(appWindow)
                    //Find the *least* index of the System UI windows in the set of all windows.
                    val sysUiIndex = sysUiWindows.map { windows.indexOf(it) }.filter { it > -1 }.min() ?: -1

                    //Samsung's Screen-Off Memo is really just a normal Activity that shows over the lock screen.
                    //However, it's not an Application-type window for some reason, so it won't hide with the
                    //currentAppLayer check. Explicitly check for its existence here.
                    isOnScreenOffMemo = windows.any { win ->
                        win.safeRoot.run {
                            packageName == "com.samsung.android.app.notes".also { this?.recycle() }
                        }
                    }

                    //Generate "layer" values for the System UI window and for the topmost app window, if
                    //it exists.
                    //currentAppLayer *should* be -1 even if there's an app open in the background,
                    //since getWindows() is only meant to return windows that can actually be
                    //interacted with. The only time it should be anything else (usually 1) is
                    //if an app is displaying above the keyguard, such as the incoming call
                    //screen or the camera.
                    currentAppLayer = if (appIndex != -1) windows.size - appIndex else appIndex
                    currentSysUiLayer = if (sysUiIndex != -1) windows.size - sysUiIndex else sysUiIndex

                    //This is mostly a debug value to see which app LSWidg thinks is on top.
                    currentAppPackage = appWindow?.safeRoot?.run {
                        packageName?.toString().also { recycle() }
                    }

                    //If the user has enabled the option to hide the frame on security (pin, pattern, password)
                    //input, we need to check if they're on the main lock screen. This is more reliable than
                    //checking for the existence of a security input, since there are a lot of different possible
                    //IDs, and OEMs change them. "notification_panel" and "left_button" are largely unchanged,
                    //although this method isn't perfect.
                    if (prefManager.hideOnSecurityPage) {
                        onMainLockscreen = sysUiNodes.find {
                            (it.viewIdResourceName == "com.android.systemui:id/notification_panel" && it.isVisibleToUser)
                                    || (it.viewIdResourceName == "com.android.systemui:id/left_button" && it.isVisibleToUser)
                        } != null
                    }

                    if (prefManager.hideOnNotificationShade) {
                        //Used for "Hide When Notification Shade Shown" so we know when it's actually expanded.
                        //Some devices don't even have left shortcuts, so also check for keyguard_indication_area.
                        //Just like the showingSecurityInput check, this is probably unreliable for some devices.
                        showingNotificationsPanel = sysUiNodes.find {
                            (it.viewIdResourceName == "com.android.systemui:id/quick_settings_panel" && it.isVisibleToUser)
                                    || (it.viewIdResourceName == "com.android.systemui:id/settings_button" && it.isVisibleToUser)
                                    || (it.viewIdResourceName == "com.android.systemui:id/tile_label" && it.isVisibleToUser)
                        } != null
                    }
                } else {
                    //If we're not on the lock screen, whether or not Screen-Off Memo is showing
                    //doesn't matter.
                    isOnScreenOffMemo = false
                }

                //If the option to show when the NC is fully expanded is enabled,
                //check if the frame can actually show. This checks to the "more_button"
                //ID, which is unique to One UI (it's the three-dot button), and is only
                //visible when the NC is fully expanded.
                if (prefManager.showInNotificationCenter) {
                    notificationsPanelFullyExpanded = sysUiNodes.find {
                        it.viewIdResourceName == "com.android.systemui:id/more_button" && it.isVisibleToUser
                    } != null
                }

                //Check to see if any of the user-specified IDs are present.
                //If any are, the frame will be hidden.
                val presentIds = prefManager.presentIds
                if (presentIds.isNotEmpty()) {
                    hideForPresentIds = sysUiNodes.any {
                        presentIds.contains(it.viewIdResourceName) && it.isVisibleToUser }
                }

                //Check to see if any of the user-specified IDs aren't present.
                //If any aren't, the frame will be hidden.
                val nonPresentIds = prefManager.nonPresentIds
                if (nonPresentIds.isNotEmpty()) {
                    hideForNonPresentIds = sysUiNodes.none {
                        nonPresentIds.contains(it.viewIdResourceName) }
                            || sysUiNodes.any { nonPresentIds.contains(it.viewIdResourceName) && !it.isVisibleToUser }
                }

                sysUiNodes.forEach { it.recycle() }
                sysUiWindows.forEach { it?.recycle() }
                appWindow?.recycle()
            }

            //Check whether the frame can show.
            if (canShow()) {
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
                if (canShow()) {
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
            delegate.view.frame.addWindow(wm, delegate.params)
        }, 100)
    }

    /**
     * Update the window manager on any params changes
     * that may have occurred.
     */
    private fun updateOverlay() {
        delegate.view.frame.updateWindow(wm, delegate.params)
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
        delegate.view.frame.removeWindow(wm)
    }

    /**
     * Check if the widget frame should show onscreen. There are quite a few conditions for this.
     * This method attempts to check those conditions in increasing order of intensiveness (check simple
     * members first, then try SharedPreferences, then use IPC methods).
     *
     * The widget frame can only show if ALL of the following conditions are met:
     *
     * =======
     * - [isScreenOn] is true
     * - [isTempHide] is false
     * - [notificationsPanelFullyExpanded] is true AND [PrefManager.showInNotificationCenter] is true
     * - [PrefManager.widgetFrameEnabled] is true
     * =======
     * OR
     * =======
     * - [wasOnKeyguard] is true
     * - [isScreenOn] is true (i.e. the display is properly on: not in Doze or on the AOD)
     * - [isTempHide] is false
     * - [PrefManager.showOnMainLockScreen] is true OR [PrefManager.showInNotificationCenter] is false
     * - [currentAppLayer] is less than 0 (i.e. doesn't exist)
     * - [isOnScreenOffMemo] is false
     * - [onMainLockscreen] is true OR [showingNotificationsPanel] is true OR [PrefManager.hideOnSecurityPage] is false
     * - [showingNotificationsPanel] is false OR [PrefManager.hideOnNotificationShade] is false (OR [notificationsPanelFullyExpanded] is true AND [PrefManager.showInNotificationCenter] is true
     * - [notificationCount] is 0 (i.e. no notifications shown on lock screen, not necessarily no notifications at all) OR [PrefManager.hideOnNotifications] is false
     * - [hideForPresentIds] is false OR [PrefManager.presentIds] is empty
     * - [hideForNonPresentIds] is false OR [PrefManager.nonPresentIds] is empty
     * - [PrefManager.widgetFrameEnabled] is true (i.e. the widget frame is actually enabled)
     * =======
     */
    private fun canShow(): Boolean {
        return (
                (isScreenOn
                        && !isTempHide
                        && (notificationsPanelFullyExpanded && prefManager.showInNotificationCenter)
                        && prefManager.widgetFrameEnabled
                ) || (wasOnKeyguard
                        && isScreenOn
                        && !isTempHide
                        && (prefManager.showOnMainLockScreen || !prefManager.showInNotificationCenter)
                        && currentAppLayer < 0
                        && !isOnScreenOffMemo
                        && (onMainLockscreen || showingNotificationsPanel || !prefManager.hideOnSecurityPage)
                        && (!showingNotificationsPanel || !prefManager.hideOnNotificationShade)
                        && (notificationCount == 0 || !prefManager.hideOnNotifications)
                        && (!hideForPresentIds || prefManager.presentIds.isEmpty())
                        && (!hideForNonPresentIds || prefManager.nonPresentIds.isEmpty())
                        && prefManager.widgetFrameEnabled
                        )
                ).also {
                if (isDebug) {
                    Log.e(
                        App.DEBUG_LOG_TAG,
                        "canShow: $it, " +
                                "isScreenOn: ${isScreenOn}, " +
                                "isTempHide: ${isTempHide}, " +
                                "wasOnKeyguard: $wasOnKeyguard, " +
                                "currentSysUiLayer: $currentSysUiLayer, " +
                                "currentAppLayer: $currentAppLayer, " +
                                "currentAppPackage: $currentAppPackage, " +
                                "onMainLockscreen: $onMainLockscreen, " +
                                "showingNotificationsPanel: $showingNotificationsPanel, " +
                                "notificationsPanelFullyExpanded: $notificationsPanelFullyExpanded, " +
                                "showOnMainLockScreen: ${prefManager.showOnMainLockScreen}" +
                                "notificationCount: $notificationCount, " +
                                "hideForPresentIds: $hideForPresentIds, " +
                                "hideForNonPresentIds: $hideForNonPresentIds, " +
                                "widgetEnabled: ${prefManager.widgetFrameEnabled}\n\n",
                        Exception()
                    )
                }
            }
    }

    /**
     * Find the [AccessibilityWindowInfo] corresponding to System UI
     *
     * @return the System UI window if it exists onscreen
     */
    private fun findSystemUiWindows(windows: List<AccessibilityWindowInfo> = this.windows): List<AccessibilityWindowInfo?> {
        return windows.filter { it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.safeRoot.run {
                this?.packageName == "com.android.systemui".also { this?.recycle() }
            }
        }
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