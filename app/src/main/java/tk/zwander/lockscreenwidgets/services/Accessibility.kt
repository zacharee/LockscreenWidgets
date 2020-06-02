package tk.zwander.lockscreenwidgets.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.*
import android.graphics.PixelFormat
import android.os.PowerManager
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.android.synthetic.main.widget_frame.view.*
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.activities.RequestUnlockActivity
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
class Accessibility : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        const val ACTION_LOCKSCREEN_DISMISSED = "LOCKSCREEN_DISMISSED"
    }

    /**
     * On Android 8.0+, it's pretty easy to dismiss the lock screen with a simple API call.
     * On earlier Android versions, it's not so easy, and we need a way to detect when the
     * lock screen has successfully been dismissed.
     *
     * This is just a simple wrapper class around a BroadcastReceiver for [RequestUnlockActivity]
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
    private var isTempHide = false
    private var hideForPresentIds = false
    private var hideForNonPresentIds = false

    private var currentSysUiLayer = 1
    private var currentAppLayer = 0

    @SuppressLint("ClickableViewAccessibility", "NewApi")
    override fun onCreate() {
        super.onCreate()

        delegate.onCreate()
        prefManager.prefs.registerOnSharedPreferenceChangeListener(this)

        delegate.apply {
            view.frame.onAfterResizeListener = {
                adapter.onResizeObservable.notifyObservers()
            }

            view.frame.onMoveListener = { velX, velY ->
                params.x += velX.toInt()
                params.y += velY.toInt()

                prefManager.posX = params.x
                prefManager.posY = params.y

                updateOverlay()
                delegate.updateWallpaperLayerIfNeeded()
            }

            view.frame.onLeftDragListener = { velX ->
                params.width -= velX
                params.x += (velX / 2)

                prefManager.frameWidthDp = pxAsDp(params.width)

                updateOverlay()
            }

            view.frame.onRightDragListener = { velX ->
                params.width += velX
                params.x += (velX / 2)

                prefManager.frameWidthDp = pxAsDp(params.width)

                updateOverlay()
            }

            view.frame.onTopDragListener = { velY ->
                params.height -= velY
                params.y += (velY / 2)

                prefManager.frameHeightDp = pxAsDp(params.height)

                updateOverlay()
            }

            view.frame.onBottomDragListener = { velY ->
                params.height += velY
                params.y += (velY / 2)

                prefManager.frameHeightDp = pxAsDp(params.height)

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
        //This block here runs even when unlocked, but it only takes a millisecond at most,
        //so it shouldn't be noticeable to the user. We use this to check the current keyguard
        //state and, if applicable, send the keyguard dismissal broadcast.
        val isScreenOn = power.isInteractive
        if (this.isScreenOn != isScreenOn) {
            isTempHide = false
            this.isScreenOn = isScreenOn
        }
        val isOnKeyguard = kgm.isKeyguardLocked
        if (isOnKeyguard != wasOnKeyguard) {
            wasOnKeyguard = isOnKeyguard
            if (!isOnKeyguard) {
                LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(Intent(ACTION_LOCKSCREEN_DISMISSED))
            }
        }

        if (isDebug) {
            Log.e(App.DEBUG_LOG_TAG, "Accessibility event: $event, isScreenOn: ${this.isScreenOn}, wasOnKeyguard: $wasOnKeyguard")
        }

        //The below block can (very rarely) take over half a second to execute, so only run it
        //if we actually need to (i.e. on the lock screen and screen is on).
        if (wasOnKeyguard && isScreenOn) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                val sysUiWindows = findSystemUiWindows()
                val appWindow = findTopAppWindow()

                val appIndex = windows.indexOf(appWindow)
                val sysUiIndex = windows.indexOf(sysUiWindows.firstOrNull())

                val sysUiNodes = ArrayList<AccessibilityNodeInfo>()
                sysUiWindows.map { it?.root }.forEach {
                    if (it != null) {
                        addAllNodesToList(it, sysUiNodes)
                    }
                }

                if (isDebug) {
                    Log.e(
                        App.DEBUG_LOG_TAG,
                        sysUiNodes.filter { it.isVisibleToUser }.map { it.viewIdResourceName }
                            .toString()
                    )

                    delegate.view.frame.setNewDebugIdItems(sysUiNodes.filter { it.isVisibleToUser }.mapNotNull { it.viewIdResourceName })
                }

                //Generate "layer" values for the System UI window and for the topmost app window, if
                //it exists.
                currentAppLayer = if (appIndex != -1) windows.size - appIndex else appIndex
                currentSysUiLayer = if (sysUiIndex != -1) windows.size - sysUiIndex else sysUiIndex

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

                val presentIds = prefManager.presentIds
                if (presentIds.isNotEmpty()) {
                    hideForPresentIds = sysUiNodes.any { presentIds.contains(it.viewIdResourceName) && it.isVisibleToUser }
                }

                val nonPresentIds = prefManager.nonPresentIds
                if (nonPresentIds.isNotEmpty()) {
                    hideForNonPresentIds = sysUiNodes.none { nonPresentIds.contains(it.viewIdResourceName) } || sysUiNodes.any { nonPresentIds.contains(it.viewIdResourceName) && !it.isVisibleToUser }
                }

                sysUiNodes.forEach { it.recycle() }
            }
        }

        if (canShow()) {
            addOverlay()
        } else {
            removeOverlay()
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        prefManager.prefs.unregisterOnSharedPreferenceChangeListener(this)
        notificationCountListener.unregister(this)
        unregisterReceiver(screenStateReceiver)
        delegate.onDestroy()
    }

    private fun addOverlay() {
        mainHandler.postDelayed({
            delegate.view.frame.addWindow(wm, delegate.params)
        }, 100)
    }

    private fun updateOverlay() {
        delegate.view.frame.updateWindow(wm, delegate.params)
    }

    private fun removeOverlay() {
        if (isDebug) {
            Log.e("LockscreenWidgetsDebug", "Removing overlay", Exception())
        }
        delegate.view.frame.removeWindow(wm)
    }

    /**
     * Check if the widget frame should show onscreen. There are quite a few conditions for this.
     * This method attempts to check those conditions in increasing order of intensiveness (check simple
     * members first, then try SharedPreferences, then use IPC methods).
     *
     * The widget frame can only show if ALL of the following conditions are met:
     * - [wasOnKeyguard] is true
     * - [isScreenOn] is true (i.e. the display is properly on: not in Doze or on the AOD)
     * - [isTempHide] is false
     * - [currentSysUiLayer] is greater than [currentAppLayer]
     * - [onMainLockscreen] is true OR [showingNotificationsPanel] is true OR [PrefManager.hideOnSecurityPage] is false
     * - [showingNotificationsPanel] is false OR [PrefManager.hideOnNotificationShade] is false
     * - [notificationCount] is 0 (i.e. no notifications shown with priority > MIN) OR [PrefManager.hideOnNotifications] is false
     * - [hideForPresentIds] is false OR [PrefManager.presentIds] is empty
     * - [hideForNonPresentIds] is false OR [PrefManager.nonPresentIds] is empty
     * - [PrefManager.widgetFrameEnabled] is true (i.e. the widget frame is actually enabled)
     */
    private fun canShow() =
        (wasOnKeyguard
                && isScreenOn
                && !isTempHide
                && currentSysUiLayer > currentAppLayer
                && (onMainLockscreen || showingNotificationsPanel || !prefManager.hideOnSecurityPage)
                && (!showingNotificationsPanel || !prefManager.hideOnNotificationShade)
                && (notificationCount == 0 || !prefManager.hideOnNotifications)
                && (!hideForPresentIds || prefManager.presentIds.isEmpty())
                && (!hideForNonPresentIds || prefManager.nonPresentIds.isEmpty())
                && prefManager.widgetFrameEnabled).also {
            if (isDebug) {
                Log.e(
                    App.DEBUG_LOG_TAG,
                    "canShow: $it, " +
                    "isScreenOn: ${isScreenOn}, " +
                    "isTempHide: ${isTempHide}, " +
                    "wasOnKeyguard: $wasOnKeyguard, " +
                    "currentSysUiLayer: $currentSysUiLayer, " +
                    "currentAppLayer: $currentAppLayer, " +
                    "onMainLockscreen: $onMainLockscreen, " +
                    "showingNotificationsPanel: $showingNotificationsPanel, " +
                    "notificationCount: $notificationCount, " +
                    "hideForPresentIds: $hideForPresentIds, " +
                    "hideForNonPresentIds: $hideForNonPresentIds, " +
                    "widgetEnabled: ${prefManager.widgetFrameEnabled}\n\n",
                    Exception()
                )
            }
        }

    /**
     * Find the [AccessibilityWindowInfo] corresponding to System UI
     *
     * @return the System UI window if it exists onscreen
     */
    private fun findSystemUiWindows(): List<AccessibilityWindowInfo?> {
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
    private fun findTopAppWindow(): AccessibilityWindowInfo? {
        windows.forEach {
            if (it.type == AccessibilityWindowInfo.TYPE_APPLICATION)
                return it
        }

        return null
    }

    /**
     * Recursively add all [AccessibilityNodeInfo]s contained in a parent to a list.
     * We use this instead of [AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId]
     * for performance and reliability reasons.
     * @param parentNode the root [AccessibilityNodeInfo] whose children we want to add to
     * a list. List will include this node as well.
     * @param list the list that will contain the child nodes.
     */
    private fun addAllNodesToList(
        parentNode: AccessibilityNodeInfo,
        list: ArrayList<AccessibilityNodeInfo>
    ) {
        list.add(parentNode)
        for (i in 0 until parentNode.childCount) {
            val child = parentNode.getChild(i)
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