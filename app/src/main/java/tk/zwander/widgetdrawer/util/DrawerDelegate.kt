package tk.zwander.widgetdrawer.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import tk.zwander.common.util.BlurManager
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.handler
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.databinding.DrawerLayoutBinding
import tk.zwander.lockscreenwidgets.services.Accessibility
import tk.zwander.lockscreenwidgets.util.*
import tk.zwander.widgetdrawer.views.Drawer
import tk.zwander.widgetdrawer.views.Handle

class DrawerDelegate private constructor(private val context: Context) : ContextWrapper(context),
    EventObserver, View.OnAttachStateChangeListener {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: DrawerDelegate? = null

        private val hasInstance: Boolean
            get() = instance != null

        fun peekInstance(context: Context): DrawerDelegate? {
            if (!hasInstance) {
                context.logUtils.debugLog("Accessibility isn't running yet")

                return null
            }

            return getInstance(context)
        }

        fun retrieveInstance(context: Context): DrawerDelegate? {
            return peekInstance(context).also {
                if (it == null) {
                    Toast.makeText(context, R.string.accessibility_not_started, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

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

    val state: Drawer.State
        get() = drawer.root.state

    private val drawer by lazy { DrawerLayoutBinding.inflate(LayoutInflater.from(this)) }
    private val handle by lazy { Handle(this) }
    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }

    private val prefsHandler = HandlerRegistry {
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
    }

    private val blurManager = BlurManager(
        context = context,
        params = drawer.root.params,
        targetView = drawer.blurBackground,
        listenKeys = arrayOf(PrefManager.KEY_BLUR_DRAWER_BACKGROUND, PrefManager.KEY_BLUR_DRAWER_BACKGROUND_AMOUNT),
        shouldBlur = { context.prefManager.blurDrawerBackground },
        blurAmount = { context.prefManager.drawerBackgroundBlurAmount }
    ) { drawer.root.updateDrawer() }

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
                updateState { it.copy(handlingDrawerClick = true) }
            }
            else -> {}
        }
    }

    override fun onViewAttachedToWindow(v: View?) {}

    override fun onViewDetachedFromWindow(v: View?) {}

    fun onCreate() {
        blurManager.onCreate()
        drawer.root.addOnAttachStateChangeListener(this)
        drawer.root.onCreate()
        tryShowHandle()
        eventManager.addObserver(this)
        prefsHandler.register(this)
    }

    fun onDestroy() {
        eventManager.removeObserver(this)
        drawer.root.hideDrawer(false)
        handle.hide(wm)
        prefsHandler.unregister(this)
        drawer.root.removeOnAttachStateChangeListener(this)

        invalidateInstance()
        blurManager.onDestroy()
    }

    fun hideAll() {
        drawer.root.hideDrawer(false)
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

    fun showDrawer() {
        drawer.root.showDrawer(wm)
        handle.hide(wm)
    }

    fun hideDrawer() {
        drawer.root.hideDrawer()
        tryShowHandle()
    }

    fun updateState(transform: (Drawer.State) -> Drawer.State) {
        drawer.root.updateState(transform)
    }
}