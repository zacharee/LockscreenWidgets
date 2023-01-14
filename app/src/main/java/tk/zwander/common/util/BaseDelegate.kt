package tk.zwander.common.util

import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.ItemTouchHelper
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import tk.zwander.common.host.WidgetHostCompat
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter

abstract class BaseDelegate<State : BaseDelegate.BaseState>(context: Context) : ContextWrapper(context),
    EventObserver, WidgetHostCompat.OnClickCallback {
    protected val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    protected val power by lazy { getSystemService(POWER_SERVICE) as PowerManager }
    protected val kgm by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    protected val wallpaper by lazy { getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager }
    protected val widgetHost by lazy { widgetHostCompat }

    abstract var state: State
        protected set

    protected abstract val prefsHandler: HandlerRegistry
    protected abstract val blurManager: BlurManager
    protected abstract val adapter: WidgetFrameAdapter
    protected abstract val gridLayoutManager: LayoutManager
    protected abstract val touchHelperCallback: ItemTouchHelper.Callback
    protected abstract val params: WindowManager.LayoutParams
    protected abstract val rootView: View

    @CallSuper
    open fun onCreate() {
        prefsHandler.register(this)
        eventManager.addObserver(this)
        blurManager.onCreate()
        widgetHost.addOnClickCallback(this)
    }

    @CallSuper
    open fun onDestroy() {
        eventManager.removeObserver(this)
        prefsHandler.unregister(this)
        blurManager.onDestroy()
        widgetHost.removeOnClickCallback(this)
    }

    open fun updateState(transform: (State) -> State) {
        val newState = transform(state)
        logUtils.debugLog("Updating state from\n$state\nto\n$newState")
        state = newState
    }

    abstract class BaseState

    abstract class LayoutManager(
        context: Context,
        orientation: Int,
        rowCount: Int,
        colCount: Int
    ) : SpannedGridLayoutManager(
        context,
        orientation,
        rowCount,
        colCount
    )
}
