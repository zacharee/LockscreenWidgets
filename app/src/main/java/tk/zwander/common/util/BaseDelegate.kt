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
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import tk.zwander.common.data.WidgetData
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
    protected abstract val params: WindowManager.LayoutParams
    protected abstract val rootView: View
    protected abstract val recyclerView: RecyclerView
    protected abstract var currentWidgets: List<WidgetData>

    private val touchHelperCallback by lazy {
        createTouchHelperCallback(
            adapter = adapter,
            widgetMoved = this::onWidgetMoved,
            onItemSelected = this::onItemSelected,
            frameLocked = this::isLocked
        )
    }
    protected val itemTouchHelper by lazy {
        ItemTouchHelper(touchHelperCallback)
    }

    @CallSuper
    open fun onCreate() {
        prefsHandler.register(this)
        eventManager.addObserver(this)
        blurManager.onCreate()
        widgetHost.addOnClickCallback(this)
        gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = gridLayoutManager
        itemTouchHelper.attachToRecyclerView(recyclerView)
        adapter.updateWidgets(currentWidgets)

        updateCounts()
    }

    @CallSuper
    open fun onDestroy() {
        eventManager.removeObserver(this)
        prefsHandler.unregister(this)
        blurManager.onDestroy()
        widgetHost.removeOnClickCallback(this)
        itemTouchHelper.attachToRecyclerView(null)

        currentWidgets = ArrayList(adapter.widgets)
    }

    open fun updateState(transform: (State) -> State) {
        val newState = transform(state)
        logUtils.debugLog("Updating state from\n$state\nto\n$newState")
        state = newState
    }

    protected abstract fun onWidgetMoved(moved: Boolean)
    protected abstract fun onItemSelected(selected: Boolean)
    protected abstract fun isLocked(): Boolean
    protected abstract fun updateCounts()

    abstract class BaseState {
        abstract val isHoldingItem: Boolean
        abstract val updatedForMove: Boolean
        abstract val handlingClick: Boolean
    }

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
