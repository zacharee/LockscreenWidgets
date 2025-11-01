package tk.zwander.common.util

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.view.View
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import tk.zwander.common.adapters.BaseAdapter
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.host.WidgetHostCompat
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.mitigations.SafeContextWrapper
import java.util.concurrent.ConcurrentLinkedDeque

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseDelegate<State : Any>(
    context: Context,
    protected val wm: WindowManager,
    protected val targetDisplayId: Int,
) : SafeContextWrapper(context),
    EventObserver, WidgetHostCompat.OnClickCallback, SavedStateRegistryOwner {
    protected val kgm by lazy { keyguardManager }
    protected val wallpaper by lazy { getSystemService(WALLPAPER_SERVICE) as WallpaperManager }
    protected val widgetHost by lazy { widgetHostCompat }
    protected val displayManager by lazy {
        getSystemService(DISPLAY_SERVICE) as DisplayManager
    }
    val display: LSDisplay
        get() = requireLsDisplayManager.requireDisplay(targetDisplayId)

    open var commonState: BaseState = BaseState()
        protected set

    abstract val viewModel: BaseViewModel<out State, out BaseDelegate<State>>

    abstract var state: State
        protected set

    protected abstract val prefsHandler: HandlerRegistry
    protected abstract val adapter: BaseAdapter
    protected abstract val gridLayoutManager: LayoutManager
    protected abstract val params: WindowManager.LayoutParams
    protected abstract val rootView: View
    protected abstract val recyclerView: RecyclerView
    protected abstract var currentWidgets: List<WidgetData>

    protected  val displayListener = object : DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}

        override fun onDisplayChanged(displayId: Int) {
            updateWindow()
        }
    }

    protected val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    protected val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry by lazy { savedStateRegistryController.savedStateRegistry }

    private val touchHelperCallback by lazy {
        createTouchHelperCallback(
            adapter = adapter,
            widgetMoved = this::onWidgetMoved,
            onItemSelected = this::onItemSelected,
            frameLocked = this::isLocked,
            onItemActive = {
                globalState.itemIsActive.value = it
            },
            viewModel = viewModel,
        )
    }
    protected val itemTouchHelper by lazy {
        ItemTouchHelper(touchHelperCallback)
    }

    protected var scope: CoroutineScope = MainScope()
        private set

    val isAttached: Boolean
        get() = rootView.isAttachedToWindow

    @CallSuper
    open fun onCreate() {
        scope = MainScope()
        rootView.setViewTreeLifecycleOwner(this)
        rootView.setViewTreeSavedStateRegistryOwner(this)

        prefsHandler.register(this)
        eventManager.addObserver(this)
        widgetHost.addOnClickCallback(this)
        gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = gridLayoutManager
        itemTouchHelper.attachToRecyclerView(recyclerView)
        adapter.updateWidgets(currentWidgets)
        displayManager.registerDisplayListener(displayListener, null)

        updateCounts()
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }
    }

    @CallSuper
    open fun onDestroy() {
        eventManager.removeObserver(this)
        prefsHandler.unregister(this)
        widgetHost.removeOnClickCallback(this)
        itemTouchHelper.attachToRecyclerView(null)
        displayManager.unregisterDisplayListener(displayListener)

        currentWidgets = ArrayList(adapter.widgets)
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }

        scope.cancel()
    }

    @SuppressLint("NotifyDataSetChanged")
    @CallSuper
    override fun onEvent(event: Event) {
        when (event) {
            is Event.RemoveWidgetConfirmed -> {
                val position = currentWidgets.indexOf(event.item)

                if (event.remove && currentWidgets.contains(event.item)) {
                    updateCommonState { it.copy(updatedForMoveOrRemove = true) }

                    val newWidgets = currentWidgets.toMutableList().apply {
                        remove(event.item)
                        when (event.item?.safeType) {
                            WidgetType.WIDGET -> widgetHost.deleteAppWidgetId(event.item.id)
                            WidgetType.SHORTCUT,
                            WidgetType.LAUNCHER_SHORTCUT,
                            WidgetType.LAUNCHER_ITEM -> shortcutIdManager.removeShortcutId(event.item.id)

                            else -> {}
                        }
                    }

                    viewModel.currentEditingInterfacePosition.value = -1
                    adapter.updateWidgets(newWidgets)
                    gridLayoutManager.doOnLayoutCompleted {
                        if (!recyclerView.isComputingLayout) {
                            adapter.notifyDataSetChanged()
                        }
                    }
                    currentWidgets = newWidgets
                }

                widgetRemovalConfirmed(event, position)
            }

            else -> {}
        }
    }

    override fun hasWidgetId(id: Int): Boolean {
        return currentWidgets.any { it.id == id }
    }

    open fun updateState(transform: (State) -> State) {
        val newState = transform(state)

        if (newState != state) {
            logUtils.debugLog("Updating state from\n$state\nto\n$newState", null)
        }

        state = newState
    }

    fun updateCommonState(transform: (BaseState) -> BaseState) {
        val newState = transform(commonState)

        if (newState != commonState) {
            logUtils.debugLog("Updating common state from\n$commonState\nto\n$newState", null)
        }

        commonState = newState
    }

    @CallSuper
    protected open fun onWidgetMoved(moved: Boolean) {
        if (moved) {
            updateCommonState { it.copy(updatedForMoveOrRemove = true) }
            currentWidgets = adapter.widgets
            viewModel.currentEditingInterfacePosition.value = -1
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

    protected open fun onItemSelected(selected: Boolean, highlighted: Boolean) {
        updateCommonState { it.copy(isHoldingItem = selected, isItemHighlighted = highlighted) }
    }

    protected abstract fun isLocked(): Boolean
    protected abstract fun retrieveCounts(): Pair<Int?, Int?>

    protected open fun widgetRemovalConfirmed(event: Event.RemoveWidgetConfirmed, position: Int) {}

    protected abstract fun updateWindow()

    data class BaseState(
        val isHoldingItem: Boolean = false,
        val isItemHighlighted: Boolean = false,
        val updatedForMoveOrRemove: Boolean = false,
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
        private val onLayoutCompletedCallbacks = ConcurrentLinkedDeque<() -> Unit>()

        override fun makeAndAddView(
            position: Int,
            direction: Direction,
            recycler: RecyclerView.Recycler,
        ): View {
            return try {
                super.makeAndAddView(position, direction, recycler)
            } catch (e: Throwable) {
                context.logUtils.normalLog("Error laying out widget view at $position.", e)
                context.createWidgetErrorView()
            }
        }

        override fun onLayoutCompleted(state: RecyclerView.State?) {
            super.onLayoutCompleted(state)

            onLayoutCompletedCallbacks.removeAll {
                mainHandler.post {
                    it()
                }
                true
            }
        }

        fun doOnLayoutCompleted(callback: () -> Unit) {
            onLayoutCompletedCallbacks.add(callback)
        }
    }

    @SuppressLint("StaticFieldLeak")
    abstract class BaseViewModel<State : Any, Delegate : BaseDelegate<State>>(
        protected val delegate: Delegate,
    ) : ViewModel() {
        val itemToRemove = MutableStateFlow<WidgetData?>(null)
        val isResizingItem = MutableStateFlow(false)
        val currentEditingInterfacePosition = MutableStateFlow(-1)

        val params: WindowManager.LayoutParams
            get() = delegate.params

        val wm: WindowManager
            get() = delegate.wm

        val itemTouchHelper: ItemTouchHelper
            get() = delegate.itemTouchHelper

        val state: State
            get() = delegate.state

        abstract val widgetCornerRadiusKey: String
        abstract val containerCornerRadiusKey: String?

        fun updateWindow() {
            delegate.updateWindow()
        }
    }
}
