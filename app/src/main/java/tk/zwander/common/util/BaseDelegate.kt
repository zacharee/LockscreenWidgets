package tk.zwander.common.util

import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Surface
import android.view.View
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tk.zwander.common.adapters.BaseAdapter
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.host.WidgetHostCompat
import tk.zwander.common.host.widgetHostCompat

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseDelegate<State : Any>(context: Context) : ContextWrapper(context),
    EventObserver, WidgetHostCompat.OnClickCallback, SavedStateRegistryOwner {
    protected val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    protected val power by lazy { getSystemService(POWER_SERVICE) as PowerManager }
    protected val kgm by lazy { keyguardManager }
    protected val wallpaper by lazy { getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager }
    protected val widgetHost by lazy { widgetHostCompat }
    protected val displayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    open var commonState: BaseState = BaseState()
        protected set

    abstract var state: State
        protected set

    protected abstract val prefsHandler: HandlerRegistry
    protected abstract val blurManager: BlurManager
    protected abstract val adapter: BaseAdapter
    protected abstract val gridLayoutManager: LayoutManager
    protected abstract val params: WindowManager.LayoutParams
    protected abstract val rootView: View
    protected abstract val recyclerView: RecyclerView
    protected abstract var currentWidgets: List<WidgetData>

    protected val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    protected val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry by lazy { savedStateRegistryController.savedStateRegistry }

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

    protected var scope: CoroutineScope = MainScope()
        private set

    val isAttached: Boolean
        get() = rootView.isAttachedToWindow

    @OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
    @CallSuper
    open fun onCreate() {
        scope = MainScope()

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
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
        rootView.setViewTreeLifecycleOwner(this)
        rootView.setViewTreeSavedStateRegistryOwner(this)
        rootView.compositionContext = rootView.createLifecycleAwareWindowRecomposer()

        updateCommonState {
            it.copy(
                wasOnKeyguard = kgm.isKeyguardLocked,
                isScreenOn = power.isInteractive,
            )
        }
    }

    @CallSuper
    open fun onDestroy() {
        eventManager.removeObserver(this)
        prefsHandler.unregister(this)
        blurManager.onDestroy()
        widgetHost.removeOnClickCallback(this)
        itemTouchHelper.attachToRecyclerView(null)

        currentWidgets = ArrayList(adapter.widgets)
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }

        scope.cancel()
    }

    @CallSuper
    override fun onEvent(event: Event) {
        when (event) {
            is Event.RemoveWidgetConfirmed -> {
                val position = currentWidgets.indexOf(event.item)

                if (event.remove && currentWidgets.contains(event.item)) {
                    currentWidgets = currentWidgets.toMutableList().apply {
                        remove(event.item)
                        when (event.item?.safeType) {
                            WidgetType.WIDGET -> widgetHost.deleteAppWidgetId(event.item.id)
                            WidgetType.SHORTCUT -> shortcutIdManager.removeShortcutId(event.item.id)
                            else -> {}
                        }
                    }

                    adapter.currentEditingInterfacePosition = -1
                    adapter.updateWidgets(currentWidgets.toList())
                }

                widgetRemovalConfirmed(event, position)
            }
            Event.ScreenOff -> {
                updateCommonState { it.copy(isScreenOn = false) }
            }
            Event.ScreenOn -> {
                updateCommonState { it.copy(isScreenOn = true) }
            }
            else -> {}
        }
    }

    override fun hasWidgetId(id: Int): Boolean {
        return currentWidgets.any { it.id == id }
    }

    open fun updateState(transform: (State) -> State) {
        val newState = transform(state)
        logUtils.debugLog("Updating state from\n$state\nto\n$newState")
        state = newState
    }

    fun updateCommonState(transform: (BaseState) -> BaseState) {
        val newState = transform(commonState)
        logUtils.debugLog("Updating common state from\n$commonState\nto\n$newState")
        commonState = newState
    }

    @CallSuper
    protected open fun onWidgetMoved(moved: Boolean) {
        if (moved) {
            currentWidgets = adapter.widgets
            adapter.currentEditingInterfacePosition = -1
            updateCommonState { it.copy(updatedForMove = true) }
        }
    }

    /**
     * Make sure the number of rows/columns in the frame/drawer reflects the user-selected value.
     */
    protected fun updateCounts() {
        val counts = retrieveCounts()

        gridLayoutManager.apply {
            counts.first?.let { rowCount = it }
            counts.second?.let { columnCount = it }
        }
    }

    protected open fun onItemSelected(selected: Boolean) {
        updateCommonState { it.copy(isHoldingItem = selected) }
    }
    protected abstract fun isLocked(): Boolean
    protected abstract fun retrieveCounts(): Pair<Int?, Int?>

    protected open fun widgetRemovalConfirmed(event: Event.RemoveWidgetConfirmed, position: Int) {}

    data class BaseState(
        val isHoldingItem: Boolean = false,
        val updatedForMove: Boolean = false,
        val handlingClick: Boolean = false,
        val wasOnKeyguard: Boolean = false,
        val isScreenOn: Boolean = false,
        val screenOrientation: Int = Surface.ROTATION_0,
    )

    abstract class LayoutManager(
        private val context: Context,
        orientation: Int,
        rowCount: Int,
        colCount: Int
    ) : SpannedGridLayoutManager(
        context,
        orientation,
        rowCount,
        colCount
    ) {
        override fun makeAndAddView(
            position: Int,
            direction: Direction,
            recycler: RecyclerView.Recycler
        ): View {
            return try {
                super.makeAndAddView(position, direction, recycler)
            } catch (e: Throwable) {
                context.logUtils.normalLog("Error laying out widget view at $position.", e)
                context.createWidgetErrorView()
            }
        }
    }
}
