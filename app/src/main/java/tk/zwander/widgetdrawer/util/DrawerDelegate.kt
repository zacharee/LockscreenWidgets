package tk.zwander.widgetdrawer.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Toast
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.databinding.DrawerLayoutBinding
import tk.zwander.lockscreenwidgets.services.Accessibility
import tk.zwander.lockscreenwidgets.util.*
import tk.zwander.widgetdrawer.views.Handle

class DrawerDelegate private constructor(private val context: Context) : ContextWrapper(context), EventObserver {
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

    private val drawer by lazy { DrawerLayoutBinding.inflate(LayoutInflater.from(this)) }
    private val handle by lazy { Handle(this) }
    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }

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
            else -> {}
        }
    }

    fun onCreate() {
        drawer.root.onCreate()
        eventManager.addObserver(this)
    }

    fun onDestroy() {
        eventManager.removeObserver(this)
        drawer.root.hideDrawer(false)
        handle.hide(wm)

        invalidateInstance()
    }

    fun hideAll() {
        drawer.root.hideDrawer(false)
        handle.hide(wm)
    }

    fun tryShowHandle() {
        if (prefManager.showDrawerHandle && power.isInteractive && !drawer.root.isAttachedToWindow) {
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
}