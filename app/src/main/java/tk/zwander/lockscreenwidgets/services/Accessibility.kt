package tk.zwander.lockscreenwidgets.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.*
import android.database.ContentObserver
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.*
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
    private val wallpaper by lazy { getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager }

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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
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

    private var updatedForMove = false
    private var notificationCount = 0
    private var onMainLockscreen = true
    private var showingNotificationsPanel = false
    private var wasOnKeyguard = true
    private var isScreenOn = true
    private var isTempHide = false

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

            params.x = params.x
            params.y = params.y

            prefManager.posX = params.x
            prefManager.posY = params.y

            updateOverlay()
            updateWallpaperLayerIfNeeded()
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
                updateWallpaperLayerIfNeeded()
                widgetHost.startListening()
            } else {
                widgetHost.stopListening()
            }
        }
        view.frame.onTempHideListener = {
            isTempHide = true
            removeOverlay()
        }

        view.widgets_pager.addOnScrollListener(
            SnapScrollListener(
                pagerSnapHelper,
                object : OnSnapPositionChangeListener {
                    override fun onSnapPositionChange(position: Int) {
                        view.frame.shouldShowRemove = position < adapter.widgets.size
                        view.remove.isVisible =
                            view.frame.isInEditingMode && view.frame.shouldShowRemove
                        prefManager.currentPage = position
                    }
                })
        )
        view.widgets_pager.layoutManager?.apply {
            try {
                scrollToPosition(prefManager.currentPage)
            } catch (e: Exception) {}
        }
        view.frame.shouldShowRemove =
            pagerSnapHelper.getSnapPosition(view.widgets_pager) < adapter.widgets.size

        notificationCountListener.register(this)
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE),
            true,
            nightModeListener
        )
        registerReceiver(
            screenStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) })

        wasOnKeyguard = kgm.isKeyguardLocked
        isScreenOn = power.isInteractive
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
            Log.e(App.DEBUG_LOG_TAG, "Accessibility event: $event, isScreenOn: ${this.isScreenOn}, wasOnKeyguard: ${wasOnKeyguard}")
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
            PrefManager.KEY_CURRENT_WIDGETS -> {
                //Make sure the adapter knows of any changes to the widget list
                if (!updatedForMove) {
                    //Only run the update if it wasn't generated by a reorder event
                    adapter.updateWidgets(prefManager.currentWidgets.toList())
                }
            }
            PrefManager.KEY_OPACITY_MODE -> {
                view.frame.updateFrameBackground()
                updateWallpaperLayerIfNeeded()
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
        mainHandler.postDelayed({
            view.frame.addWindow(wm, params)
        }, 100)
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
     * - [isTempHide] is false
     * - [currentSysUiLayer] is greater than [currentAppLayer]
     * - [onMainLockscreen] is true OR [showingNotificationsPanel] is true OR [PrefManager.hideOnSecurityPage] is false
     * - [showingNotificationsPanel] is false OR [PrefManager.hideOnNotificationShade] is false
     * - [notificationCount] is 0 (i.e. no notifications shown with priority > MIN) OR [PrefManager.hideOnNotifications] is false
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
                && prefManager.widgetFrameEnabled).also {
            if (isDebug) {
                Log.e(
                    App.DEBUG_LOG_TAG, "canShow: $it, " +
                            "isScreenOn: ${isScreenOn}, " +
                            "isTempHide: ${isTempHide}, " +
                            "wasOnKeyguard: $wasOnKeyguard, " +
                            "currentSysUiLayer: $currentSysUiLayer, " +
                            "currentAppLayer: $currentAppLayer, " +
                            "onMainLockscreen: $onMainLockscreen, " +
                            "showingNotificationsPanel: $showingNotificationsPanel, " +
                            "notificationCount: $notificationCount, " +
                            "widgetEnabled: ${prefManager.widgetFrameEnabled}\n\n", Exception()
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

    @SuppressLint("MissingPermission")
    private fun updateWallpaperLayerIfNeeded() {
        if (prefManager.opacityMode == PrefManager.VALUE_OPACITY_MODE_MASKED) {
            try {
                val fastW = wallpaper.fastDrawable

                fastW?.mutate()?.apply {
                    view.wallpaper_background.setImageDrawable(this)
                    view.wallpaper_background.scaleType = ImageView.ScaleType.MATRIX
                    view.wallpaper_background.imageMatrix = Matrix().apply {
                        val realSize = Point().apply { wm.defaultDisplay.getRealSize(this) }
                        val loc = view.locationOnScreen ?: intArrayOf(0, 0)

                        val dwidth: Int = intrinsicWidth
                        val dheight: Int = intrinsicHeight

                        val wallpaperAdjustmentX = (dwidth - realSize.x) / 2f
                        val wallpaperAdjustmentY = (dheight - realSize.y) / 2f

                        setTranslate(
                            (-loc[0].toFloat() - wallpaperAdjustmentX),
                            //TODO: LGUX 9 doesn't like this Y-translation for some reason
                            (-loc[1].toFloat() - wallpaperAdjustmentY)
                        )
                    }
                } ?: view.wallpaper_background.setImageDrawable(null)
            } catch (e: Exception) {
                view.wallpaper_background.setImageDrawable(null)
            }
        } else {
            view.wallpaper_background.setImageDrawable(null)
        }
    }
}