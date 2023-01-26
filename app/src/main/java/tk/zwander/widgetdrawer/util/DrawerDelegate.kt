package tk.zwander.widgetdrawer.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.core.animation.doOnEnd
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.util.BaseDelegate
import tk.zwander.common.util.BlurManager
import tk.zwander.common.util.Event
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.dpAsPx
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.handler
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.screenSize
import tk.zwander.common.util.shortcutIdManager
import tk.zwander.common.util.statusBarHeight
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.databinding.DrawerLayoutBinding
import tk.zwander.lockscreenwidgets.services.Accessibility
import tk.zwander.lockscreenwidgets.util.*
import tk.zwander.widgetdrawer.adapters.DrawerAdapter
import tk.zwander.widgetdrawer.views.Handle

class DrawerDelegate private constructor(context: Context) : BaseDelegate<DrawerDelegate.State>(context) {
    companion object {
        const val ANIM_DURATION = 200L

        @SuppressLint("StaticFieldLeak")
        private var instance: DrawerDelegate? = null

        private val hasInstance: Boolean
            get() = instance != null

        @Synchronized
        fun peekInstance(context: Context): DrawerDelegate? {
            if (!hasInstance) {
                context.logUtils.debugLog("Accessibility isn't running yet")

                return null
            }

            return getInstance(context)
        }

        @Synchronized
        fun retrieveInstance(context: Context): DrawerDelegate? {
            return peekInstance(context).also {
                if (it == null) {
                    Toast.makeText(context, R.string.accessibility_not_started, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        @Synchronized
        fun getInstance(context: Context): DrawerDelegate {
            return instance ?: run {
                if (context !is Accessibility) {
                    throw IllegalStateException("Delegate can only be initialized by Accessibility Service!")
                } else {
                    DrawerDelegate(context).also {
                        instance = it
                    }
                }
            }
        }

        fun invalidateInstance() {
            instance = null
        }
    }

    override var state = State()

    override val params = WindowManager.LayoutParams().apply {
        val displaySize = screenSize
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        width = displaySize.x
        height = WindowManager.LayoutParams.MATCH_PARENT
        format = PixelFormat.RGBA_8888
        gravity = Gravity.TOP
        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    override val rootView: View
        get() = drawer.root
    override val recyclerView: RecyclerView
        get() = drawer.widgetGrid
    override var currentWidgets: List<WidgetData>
        get() = prefManager.drawerWidgets.toList()
        set(value) {
            prefManager.drawerWidgets = LinkedHashSet(value)
        }

    private val drawer by lazy { DrawerLayoutBinding.inflate(LayoutInflater.from(this)) }
    private val handle by lazy { Handle(this) }

    override val adapter by lazy {
        DrawerAdapter(appWidgetManager, widgetHost) { widget, _ ->
            removeWidget(widget)
        }
    }

    override val prefsHandler = HandlerRegistry {
        handler(PrefManager.KEY_DRAWER_ENABLED) {
            if (prefManager.drawerEnabled) {
                tryShowHandle()
            } else {
                hideAll()
            }
        }
        handler(PrefManager.KEY_SHOW_DRAWER_HANDLE) {
            if (prefManager.showDrawerHandle) {
                tryShowHandle()
            } else {
                hideHandle()
            }
        }
        handler(PrefManager.KEY_DRAWER_WIDGETS) {
            if (!state.updatedForMove) {
                //Only run the update if it wasn't generated by a reorder event
                adapter.updateWidgets(currentWidgets.toList())
            } else {
                updateState { it.copy(updatedForMove = false) }
            }
        }
        handler(PrefManager.KEY_DRAWER_BACKGROUND_COLOR) {
            drawer.root.setBackgroundColor(prefManager.drawerBackgroundColor)
        }
        handler(PrefManager.KEY_DRAWER_COL_COUNT) {
            updateCounts()
        }
        handler(PrefManager.KEY_DRAWER_WIDGET_CORNER_RADIUS) {
            if (drawer.root.isAttachedToWindow) {
                adapter.updateViews()
            }
        }
        handler(PrefManager.KEY_LOCK_WIDGET_DRAWER) {
            adapter.currentEditingInterfacePosition = -1
        }
        handler(PrefManager.KEY_DRAWER_SIDE_PADDING) {
            updateSidePadding()
        }
    }

    override val blurManager = BlurManager(
        context = context,
        params = params,
        targetView = drawer.blurBackground,
        listenKeys = arrayOf(PrefManager.KEY_BLUR_DRAWER_BACKGROUND, PrefManager.KEY_BLUR_DRAWER_BACKGROUND_AMOUNT),
        shouldBlur = { prefManager.blurDrawerBackground },
        blurAmount = { prefManager.drawerBackgroundBlurAmount }
    ) { updateDrawer() }

    override val gridLayoutManager = SpannedLayoutManager()
    @Suppress("DEPRECATION")
    private val globalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    hideDrawer()
                }
            }
        }
    }

    override fun onEvent(event: Event) {
        when (event) {
            Event.ShowDrawer -> {
                showDrawer()
            }
            Event.CloseDrawer -> {
                hideDrawer()
            }
            Event.ShowHandle -> {
                tryShowHandle()
            }
            Event.DrawerShown -> {
                hideHandle()
            }
            Event.DrawerHidden -> {
                tryShowHandle()
            }
            Event.ScreenOn -> {
                if (power.isInteractive) {
                    tryShowHandle()
                }
            }
            Event.ScreenOff -> {
                if (!power.isInteractive) {
                    hideAll()
                }
            }
            Event.DrawerWidgetClick -> {
                updateState { it.copy(handlingClick = true) }
            }
            is Event.DrawerAttachmentState -> {
                if (event.attached) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        adapter.notifyItemRangeChanged(0, adapter.itemCount)
                    }, 50)

                    widgetHost.startListening()
                    
                    drawer.root.setPadding(
                        drawer.root.paddingLeft, 
                        statusBarHeight, 
                        drawer.root.paddingRight,
                        drawer.root.paddingBottom
                    )

                    drawer.root.handler?.postDelayed({
                        val anim = ValueAnimator.ofFloat(0f, 1f)
                        anim.interpolator = DecelerateInterpolator()
                        anim.duration = ANIM_DURATION
                        anim.addUpdateListener {
                            drawer.root.alpha = it.animatedValue.toString().toFloat()
                        }
                        anim.doOnEnd {
                            eventManager.sendEvent(Event.DrawerShown)
                        }
                        anim.start()
                    }, 10)

                    drawer.root.setBackgroundColor(prefManager.drawerBackgroundColor)
                } else {
                    try {
                        widgetHost.stopListening()
                    } catch (e: NullPointerException) {
                        //AppWidgetServiceImpl$ProviderId NPE
                    }
                }
            }
            is Event.RemoveWidgetConfirmed -> {
                if (event.remove && currentWidgets.contains(event.item)) {
                    currentWidgets = currentWidgets.toMutableList().apply {
                        remove(event.item)
                        when (event.item?.safeType) {
                            WidgetType.WIDGET -> widgetHost.deleteAppWidgetId(event.item.id)
                            WidgetType.SHORTCUT -> shortcutIdManager.removeShortcutId(event.item.id)
                            else -> {}
                        }
                    }
                }

                if (event.remove) {
                    adapter.currentEditingInterfacePosition = -1
                    adapter.updateWidgets(currentWidgets.toList())
                }
            }
            Event.DrawerBackButtonClick -> {
                hideDrawer()
            }
            else -> {}
        }
    }

    override fun onWidgetClick(trigger: Boolean) {
        if (trigger && prefManager.requestUnlockDrawer) {
            DismissOrUnlockActivity.launch(this)
            eventManager.sendEvent(Event.CloseDrawer)
        } else {
            eventManager.sendEvent(Event.DrawerWidgetClick)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()

        registerReceiver(globalReceiver, IntentFilter().apply {
            @Suppress("DEPRECATION")
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        })

        drawer.removeWidgetConfirmation.root.updateLayoutParams<ViewGroup.LayoutParams> {
            height = (screenSize.y / 2f).toInt()
        }
        dpAsPx(16).apply {
            drawer.removeWidgetConfirmation.root.setContentPadding(this, this, this, this)
        }
        drawer.removeWidgetConfirmation.confirmDeleteText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)

        drawer.addWidget.setOnClickListener { pickWidget() }
        drawer.closeDrawer.setOnClickListener { hideDrawer() }
        drawer.widgetGrid.nestedScrollingListener = {
            itemTouchHelper.attachToRecyclerView(
                if (it) {
                    null
                } else {
                    drawer.widgetGrid
                }
            )
        }

        updateSidePadding()
        tryShowHandle()
    }

    override fun onDestroy() {
        super.onDestroy()

        hideDrawer(false)
        handle.hide(wm)

        try {
            unregisterReceiver(globalReceiver)
        } catch (ignored: IllegalArgumentException) {}
        invalidateInstance()
    }

    override fun onWidgetMoved(moved: Boolean) {
        if (moved) {
            updateState { it.copy(updatedForMove = true) }
            currentWidgets = adapter.widgets
            adapter.currentEditingInterfacePosition = -1
        }
    }

    override fun onItemSelected(selected: Boolean) {
        updateState { it.copy(isHoldingItem = selected) }
        drawer.widgetGrid.selectedItem = selected
    }

    override fun isLocked(): Boolean {
        return prefManager.lockWidgetDrawer
    }

    fun hideAll() {
        hideDrawer(false)
        handle.hide(wm)
    }

    fun tryShowHandle() {
        if (prefManager.drawerEnabled && prefManager.showDrawerHandle && power.isInteractive) {
            handle.show(wm)
        }
    }

    fun hideHandle() {
        handle.hide(wm)
    }

    fun showDrawer(wm: WindowManager = this.wm) {
        try {
            wm.addView(drawer.root, params)
        } catch (_: Exception) {}
        handle.hide(wm)
    }

    fun updateDrawer(wm: WindowManager = this.wm) {
        try {
            wm.updateViewLayout(drawer.root, params)
        } catch (_: Exception) {}
    }

    fun hideDrawer(callListener: Boolean = true) {
        updateState { it.copy(handlingClick = false) }
        adapter.currentEditingInterfacePosition = -1

        val anim = ValueAnimator.ofFloat(1f, 0f)
        anim.interpolator = AccelerateInterpolator()
        anim.duration = ANIM_DURATION
        anim.addUpdateListener {
            drawer.root.alpha = it.animatedValue.toString().toFloat()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                drawer.root.handler?.postDelayed({
                    try {
                        wm.removeView(drawer.root)
                        if (callListener) eventManager.sendEvent(Event.DrawerHidden)
                    } catch (_: Exception) {
                    }
                }, 10)
            }
        })
        anim.start()
        tryShowHandle()
    }

    override fun updateCounts() {
        gridLayoutManager.columnCount = prefManager.drawerColCount
        gridLayoutManager.customHeight = resources.getDimensionPixelSize(R.dimen.drawer_row_height)
    }

    private fun pickWidget() {
        hideDrawer()
        eventManager.sendEvent(Event.LaunchAddDrawerWidget(true))
    }

    private fun removeWidget(info: WidgetData) {
        drawer.removeWidgetConfirmation.root.show(info)
    }

    private fun updateSidePadding() {
        val padding = dpAsPx(prefManager.drawerSidePadding)

        drawer.widgetGrid.updatePaddingRelative(
            start = padding,
            end = padding
        )
    }

    inner class SpannedLayoutManager : LayoutManager(
        this@DrawerDelegate,
        RecyclerView.VERTICAL,
        1,
        prefManager.drawerColCount
    ) {
        override fun canScrollVertically(): Boolean {
            return (adapter.currentEditingInterfacePosition == -1 || state.isHoldingItem) && super.canScrollVertically()
        }
    }

    data class State(
        override val isHoldingItem: Boolean = false,
        override val updatedForMove: Boolean = false,
        override val handlingClick: Boolean = false
    ) : BaseState()
}