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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.widget_frame.view.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.RequestUnlockActivity
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.host.WidgetHost
import tk.zwander.lockscreenwidgets.interfaces.OnSnapPositionChangeListener
import tk.zwander.lockscreenwidgets.util.*
import kotlin.math.roundToInt
import kotlin.math.sign

class Accessibility : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        const val ACTION_LOCKSCREEN_DISMISSED = "LOCKSCREEN_DISMISSED"
    }

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

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    removeOverlay()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    if (canShow()) {
                        addOverlay()
                    }
                }
            }
        }
    }

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

    private var updatedForMove = false
    private var notificationCount = 0
    private var isScreenOn = false
    private var currentPackage: String? = "com.android.systemui"
    private var showingSecurityInput = false
    private var showingLeftShortcut = false
    private var wasOnKeyguard = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        isScreenOn = power.isInteractive
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

        registerReceiver(screenStateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        notificationCountListener.register(this)
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE),
            true,
            nightModeListener
        )

        wasOnKeyguard = kgm.isKeyguardLocked
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val window = findFirstNonAccOverlayWindow()
            currentPackage = window?.root?.packageName?.toString()
            showingSecurityInput = window?.root?.run {
                findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_pin_view").find { it.isVisibleToUser } != null
                        || findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_pattern_view").find { it.isVisibleToUser } != null
                        || findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_password_view").find { it.isVisibleToUser } != null
                        || findAccessibilityNodeInfosByViewId("com.android.systemui:id/keyguard_sim_puk_view").find { it.isVisibleToUser } != null
            } == true
            showingLeftShortcut = window?.root?.findAccessibilityNodeInfosByViewId("com.android.systemui:id/left_button")?.find { it.isVisibleToUser } != null
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val isOnKeyguard = kgm.isKeyguardLocked
            if (isOnKeyguard != wasOnKeyguard) {
                wasOnKeyguard = isOnKeyguard
                if (!isOnKeyguard) {
                    LocalBroadcastManager.getInstance(this)
                        .sendBroadcast(Intent(ACTION_LOCKSCREEN_DISMISSED))
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
                if (!updatedForMove) {
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
        unregisterReceiver(screenStateReceiver)
        notificationCountListener.unregister(this)
        contentResolver.unregisterContentObserver(nightModeListener)
    }

    private fun addOverlay() {
        try {
            wm.addView(view, params)
        } catch (e: Exception) {}
    }

    private fun updateOverlay() {
        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {}
    }

    private fun removeOverlay() {
        try {
            wm.removeView(view)
        } catch (e: Exception) {}
    }

    private fun canShow() =
        isScreenOn
                && currentPackage == "com.android.systemui"
                && (!showingSecurityInput || !prefManager.hideOnSecurityPage)
                && (showingLeftShortcut || !prefManager.hideOnNotificationShade)
                && (notificationCount == 0 || !prefManager.hideOnNotifications)
                && prefManager.widgetFrameEnabled
                && kgm.isKeyguardLocked

    private fun findFirstNonAccOverlayWindow(): AccessibilityWindowInfo? {
        windows.forEach {
            if (it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                return it
            }
        }
        return null
    }
}