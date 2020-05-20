package tk.zwander.lockscreenwidgets.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.appwidget.AppWidgetManager
import android.content.*
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.widget_frame.view.*
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.RequestUnlockActivity
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.host.WidgetHost
import tk.zwander.lockscreenwidgets.interfaces.OnSnapPositionChangeListener
import tk.zwander.lockscreenwidgets.util.*
import kotlin.math.roundToInt
import kotlin.math.sign

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

    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }
    private val widgetHost by lazy {
        WidgetHost(this, 1003) {
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, RequestUnlockActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }, 100)
        }
    }

    private val view by lazy {
        LayoutInflater.from(ContextThemeWrapper(this, R.style.AppTheme))
            .inflate(R.layout.widget_frame, null)
    }

    private val adapter by lazy {
        WidgetFrameAdapter(widgetManager, widgetHost)
    }

    private val params by lazy {
        WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            width = dpAsPx(prefManager.frameWidthDp)
            height = dpAsPx(prefManager.frameHeightDp)

            x = prefManager.posX
            y = prefManager.posY

            gravity = Gravity.CENTER
            flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            format = PixelFormat.RGBA_8888
        }
    }

    private val pagerSnapHelper by lazy { PagerSnapHelper() }

    private val touchHelperCallback by lazy {
        object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return adapter.onMove(viewHolder.adapterPosition, target.adapterPosition).also {
                    if (it) {
                        updatedForMove = true
                        prefManager.currentWidgets = adapter.widgets.toHashSet()
                        updatedForMove = false
                    }
                }
            }

            override fun getDragDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (viewHolder is WidgetFrameAdapter.AddWidgetVH) 0
                else super.getDragDirs(recyclerView, viewHolder)
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                    viewHolder?.itemView?.alpha = 0.5f
                    pagerSnapHelper.attachToRecyclerView(null)
                }

                super.onSelectedChanged(viewHolder, actionState)
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)

                viewHolder.itemView.alpha = 1.0f
                pagerSnapHelper.attachToRecyclerView(view.widgets_pager)
            }

            override fun interpolateOutOfBoundsScroll(
                recyclerView: RecyclerView,
                viewSize: Int,
                viewSizeOutOfBounds: Int,
                totalSize: Int,
                msSinceStartScroll: Long
            ): Int {
                val direction = sign(viewSizeOutOfBounds.toFloat()).toInt()
                return (viewSize * 0.01f * direction).roundToInt()
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        }
    }

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

    private val nightModeListener = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            when (uri) {
                Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE) -> {
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    //If the device has some sort of AOD or ambient display, by the time we receive
                    //an accessibility event and see that the display is off, it's usually too late
                    //and the current screen content has "frozen," causing the widget frame to show
                    //where it shouldn't. ACTION_SCREEN_OFF is called early enough that we can remove
                    //the frame before it's frozen in place.
                    removeOverlay()
                }
            }
        }
    }

    private var updatedForMove = false
    private var notificationCount = 0
    private var showingSecurityInput = false
    private var onMainLockscreen = true
    private var wasOnKeyguard = true
    private var isScreenOn = true

    private var currentSysUiLayer = 1
    private var currentAppLayer = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        view.widgets_pager.apply {
            adapter = this@Accessibility.adapter
            setHasFixedSize(true)
            pagerSnapHelper.attachToRecyclerView(this)
            ItemTouchHelper(touchHelperCallback).attachToRecyclerView(this)
        }

        adapter.updateWidgets(prefManager.currentWidgets.toList())
        prefManager.prefs.registerOnSharedPreferenceChangeListener(this)

        view.frame.onMoveListener = { velX, velY ->
            params.x += velX.toInt()
            params.y += velY.toInt()

            prefManager.posX = params.x
            prefManager.posY = params.y

            updateOverlay()
        }
        view.frame.onLeftDragListener = { velX ->
            params.width -= velX.toInt()
            params.x += (velX / 2f).toInt()

            prefManager.frameWidthDp = pxAsDp(params.width)

            updateOverlay()
        }
        view.frame.onRightDragListener = { velX ->
            params.width += velX.toInt()
            params.x += (velX / 2f).toInt()

            prefManager.frameWidthDp = pxAsDp(params.width)

            updateOverlay()
        }
        view.frame.onTopDragListener = { velY ->
            params.height -= velY.toInt()
            params.y += (velY / 2f).toInt()

            prefManager.frameHeightDp = pxAsDp(params.height)

            updateOverlay()
        }
        view.frame.onBottomDragListener = { velY ->
            params.height += velY.toInt()
            params.y += (velY / 2f).toInt()

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
        view.frame.onRemoveListener = {
            (view.widgets_pager.layoutManager as LinearLayoutManager).apply {
                val index = findFirstVisibleItemPosition()
                val item = try {
                    adapter.widgets[index]
                } catch (e: IndexOutOfBoundsException) {
                    null
                }
                prefManager.currentWidgets = prefManager.currentWidgets.apply {
                    if (item != null) {
                        remove(item)
                        widgetHost.deleteAppWidgetId(item.id)
                    }
                }
            }
        }
        view.frame.attachmentStateListener = {
            if (it) {
                widgetHost.startListening()
            } else {
                widgetHost.stopListening()
            }
        }

        view.widgets_pager.addOnScrollListener(
            SnapScrollListener(
                pagerSnapHelper,
                object : OnSnapPositionChangeListener {
                    override fun onSnapPositionChange(position: Int) {
                        view.frame.shouldShowRemove = position < adapter.widgets.size
                        view.remove.isVisible =
                            view.frame.isInEditingMode && view.frame.shouldShowRemove
                    }
                })
        )
        view.frame.shouldShowRemove =
            pagerSnapHelper.getSnapPosition(view.widgets_pager) < adapter.widgets.size

        notificationCountListener.register(this)
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE),
            true,
            nightModeListener
        )
        registerReceiver(screenStateReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        wasOnKeyguard = kgm.isKeyguardLocked
        isScreenOn = power.isInteractive
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        //This block here runs even when unlocked, but it only takes a millisecond at most,
        //so it shouldn't be noticeable to the user. We use this to check the current keyguard
        //state and, if applicable, send the keyguard dismissal broadcast.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            isScreenOn = power.isInteractive
            val isOnKeyguard = kgm.isKeyguardLocked
            if (isOnKeyguard != wasOnKeyguard) {
                wasOnKeyguard = isOnKeyguard
                if (!isOnKeyguard) {
                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(Intent(ACTION_LOCKSCREEN_DISMISSED))
                }
            }
        }

        //The below block can (very rarely) take over half a second to execute, so only run it
        //if we actually need to (i.e. on the lock screen and screen is on).
        if (wasOnKeyguard && isScreenOn) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                val sysUiWindows = findSystemUiWindows()
                val appWindow = findTopAppWindow()

                val appIndex = windows.indexOf(appWindow)
                val sysUiIndex = windows.indexOf(sysUiWindows.firstOrNull())

                if (App.DEBUG) {
                    val nodes = ArrayList<AccessibilityNodeInfo>()
                    val roots = sysUiWindows.map { it?.root }

                    if (roots.isNotEmpty()) {
                        roots.forEach {
                            if (it != null) {
                                addAllNodesToList(it, nodes)
                            }
                        }

                        Log.e("LockscreenWidgets", nodes.filter { it.isVisibleToUser }.map { it.viewIdResourceName }.toString())
                    }
                }

                //Generate "layer" values for the System UI window and for the topmost app window, if
                //it exists.
                currentAppLayer = if (appIndex != -1) windows.size - appIndex else appIndex
                currentSysUiLayer = if (sysUiIndex != -1) windows.size - sysUiIndex else sysUiIndex

                if (prefManager.hideOnSecurityPage) {
                    //Used for "Hide On Security Input" so we know when the security input is actually showing.
                    //Some devices probably change these IDs, meaning this option won't work for everyone.
                    showingSecurityInput = sysUiWindows.map { it?.root }.run {
                        any { info ->
                            (info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_pin_view")
                                ?.find { it.isVisibleToUser } != null).also { if (App.DEBUG) Log.e("LockscreenWidgets", "keyguard_pin_view $it") }
                                    || (info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_pattern_view")
                                ?.find { it.isVisibleToUser } != null).also { if (App.DEBUG) Log.e("LockscreenWidgets", "keyguard_pin_view $it") }
                                    || (info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_password_view")
                                ?.find { it.isVisibleToUser } != null).also { if (App.DEBUG) Log.e("LockscreenWidgets", "keyguard_pin_view $it") }
                                    || (info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_sim_puk_view")
                                ?.find { it.isVisibleToUser } != null).also { if (App.DEBUG) Log.e("LockscreenWidgets", "keyguard_pin_view $it") }
                                    || (info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/lockPatternView")
                                ?.find { it.isVisibleToUser } != null).also { if (App.DEBUG) Log.e("LockscreenWidgets", "keyguard_pin_view $it") }
                                    || (info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/passwordEntry")
                                ?.find { it.isVisibleToUser } != null).also { if (App.DEBUG) Log.e("LockscreenWidgets", "keyguard_pin_view $it") }
                                    || (info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/pinEntry")
                                ?.find { it.isVisibleToUser } != null).also { if (App.DEBUG) Log.e("LockscreenWidgets", "keyguard_pin_view $it") }
                        }
                    } == true
                }

                if (prefManager.hideOnNotificationShade) {
                    //Used for "Hide When Notification Shade Shown" so we know when it's actually expanded.
                    //Some devices don't even have left shortcuts, so also check for keyguard_indication_area.
                    //Just like the showingSecurityInput check, this is probably unreliable for some devices.
                    onMainLockscreen = sysUiWindows.map { it?.root }.run {
                        any { info ->
                            (info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/left_button")?.find { it.isVisibleToUser } != null
                                    || info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_indication_area")?.find { it.isVisibleToUser } != null
                                    || info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/emergency_call_button")?.find { it.isVisibleToUser } != null
                                    || info?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/settings_button")?.find { it.isVisibleToUser } == null)
                        }
                    } == true
                }
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
            PrefManager.KEY_CURRENT_WIDGETS -> {
                //Make sure the adapter knows of any changes to the widget list
                if (!updatedForMove) {
                    //Only run the update if it wasn't generated by a reorder event
                    adapter.updateWidgets(prefManager.currentWidgets.toList())
                }
            }
            PrefManager.KEY_OPAQUE_FRAME -> {
                view.frame.updateFrameBackground()
            }
            PrefManager.KEY_WIDGET_FRAME_ENABLED -> {
                if (canShow()) {
                    addOverlay()
                } else {
                    removeOverlay()
                }
            }
            PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR -> {
                view.frame.updatePageIndicatorBehavior()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        prefManager.prefs.unregisterOnSharedPreferenceChangeListener(this)
        notificationCountListener.unregister(this)
        contentResolver.unregisterContentObserver(nightModeListener)
        unregisterReceiver(screenStateReceiver)
    }

    private fun addOverlay() {
        view.frame.addWindow(wm, params)
    }

    private fun updateOverlay() {
        view.frame.updateWindow(wm, params)
    }

    private fun removeOverlay() {
        view.frame.removeWindow(wm)
    }

    /**
     * Check if the widget frame should show onscreen. There are quite a few conditions for this.
     * This method attempts to check those conditions in increasing order of intensiveness (check simple
     * members first, then try SharedPreferences, then use IPC methods).
     *
     * The widget frame can only show if ALL of the following conditions are met:
     * - [wasOnKeyguard] is true
     * - [isScreenOn] is true (i.e. the display is properly on: not in Doze or on the AOD)
     * - [currentSysUiLayer] is greater than [currentAppLayer]
     * - [showingSecurityInput] is false OR [PrefManager.hideOnSecurityPage] is false
     * - [onMainLockscreen] is true OR [PrefManager.hideOnNotificationShade] is false
     * - [notificationCount] is 0 (i.e. no notifications shown with priority > MIN) OR [PrefManager.hideOnNotifications] is false
     * - [PrefManager.widgetFrameEnabled] is true (i.e. the widget frame is actually enabled)
     */
    private fun canShow() =
        (wasOnKeyguard
                && isScreenOn
                && currentSysUiLayer > currentAppLayer
                && (!showingSecurityInput || !prefManager.hideOnSecurityPage)
                && (onMainLockscreen || !prefManager.hideOnNotificationShade)
                && (notificationCount == 0 || !prefManager.hideOnNotifications)
                && prefManager.widgetFrameEnabled).also {
            if (App.DEBUG) {
                Log.e("LockscreenWidgets", "canShow: $it, " +
                        "isScreenOn: ${power.isInteractive}, " +
                        "wasOnKeyguard: $wasOnKeyguard, " +
                        "currentSysUiLayer: $currentSysUiLayer, " +
                        "currentAppLayer: $currentAppLayer, " +
                        "showingSecurityInput: $showingSecurityInput, " +
                        "onMainLockscreen: $onMainLockscreen, " +
                        "notificationCount: $notificationCount, " +
                        "widgetEnabled: ${prefManager.widgetFrameEnabled}")
            }
        }

    /**
     * Find the [AccessibilityWindowInfo] corresponding to System UI
     *
     * @return the System UI window if it exists onscreen
     */
    private fun findSystemUiWindows(): List<AccessibilityWindowInfo?> {
        return windows.filter { it.type == AccessibilityWindowInfo.TYPE_SYSTEM && it.root?.packageName == "com.android.systemui" }
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

    //Debug method
    private fun addAllNodesToList(parentNode: AccessibilityNodeInfo, list: ArrayList<AccessibilityNodeInfo>) {
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