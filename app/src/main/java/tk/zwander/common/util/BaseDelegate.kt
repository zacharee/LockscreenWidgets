package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    protected open val targetDisplayId: String,
) : SafeContextWrapper(context),
    EventObserver, WidgetHostCompat.OnClickCallback, SavedStateRegistryOwner {
    protected val kgm by lazy { keyguardManager }
    protected val widgetHost by lazy { widgetHostCompat }
    protected val wm: WindowManager?
        get() = displayAndWindowManagerFlow.value.windowManager
    protected val display: LSDisplay?
        get() = displayAndWindowManagerFlow.value.display

    protected val displayAndWindowManagerFlow: StateFlow<DisplayAndWindowManager> by lazy {
        lsDisplayManager.collectDisplay(targetDisplayId).map { lsDisplay ->
            val displayContext = lsDisplay?.let { createDisplayContextCompat(it.display) }
            val windowManager = displayContext?.getSystemService(WINDOW_SERVICE) as? WindowManager?

            DisplayAndWindowManager(
                display = lsDisplay,
                windowManager = windowManager,
            )
        }.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = DisplayAndWindowManager(),
        )
    }

    val screenOrientation: Int?
        get() = display?.screenOrientation

    open var commonState: BaseState = BaseState()
        protected set

    abstract val viewModel: BaseViewModel<out State, out BaseDelegate<State>>

    abstract var state: State
        protected set

    protected abstract val prefsHandler: HandlerRegistry
    protected abstract val adapter: BaseAdapter<*>
    abstract val gridLayoutManager: LayoutManager
    protected abstract val params: WindowManager.LayoutParams
    protected abstract val rootView: View
    protected abstract val recyclerView: RecyclerView
    abstract var currentWidgets: List<WidgetData>

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

    val isAttached: Boolean
        get() = rootView.isAttachedToWindow

    @CallSuper
    open fun onCreate() {
        logUtils.debugLog("Creating ${this::class.java}", null)

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

        updateCounts()
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.safeCurrentState = Lifecycle.State.CREATED
        }

        lifecycleScope.launch {
            lsDisplayManager.availableDisplays.collect { displays ->
                if (displays.values.any { it.uniqueIdCompat == targetDisplayId }) {
                    updateWindow()
                }
            }
        }
    }

    @CallSuper
    open suspend fun onDestroy() {
        logUtils.debugLog("Destroying ${this::class.java}", null)

        eventManager.removeObserver(this)
        prefsHandler.unregister(this)
        widgetHost.removeOnClickCallback(this)
        itemTouchHelper.attachToRecyclerView(null)

        currentWidgets = ArrayList(adapter.widgets)
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.safeCurrentState = Lifecycle.State.DESTROYED
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @CallSuper
    override suspend fun onEvent(event: Event) {
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

    /**
     * Force the display to remain on, or remove that force.
     *
     * @param wm the WindowManager to use.
     * @param on whether to add or remove the force flag.
     */
    protected suspend fun forceWakelock(on: Boolean, updateOverlay: Boolean = true) {
        if (on) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }

        if (updateOverlay) {
            updateOverlay()
        }
    }

    protected open fun onItemSelected(selected: Boolean, highlighted: Boolean) {
        updateCommonState { it.copy(isHoldingItem = selected, isItemHighlighted = highlighted) }
    }

    protected abstract fun isLocked(): Boolean
    protected abstract fun retrieveCounts(): Pair<Int?, Int?>

    protected open fun widgetRemovalConfirmed(event: Event.RemoveWidgetConfirmed, position: Int) {}

    protected abstract suspend fun updateWindow()

    protected suspend fun updateOverlay() {
        withContext(Dispatchers.Main) {
            wm?.safeUpdateViewLayout(rootView, params)
        }
    }

    data class BaseState(
        val isHoldingItem: Boolean = false,
        val isItemHighlighted: Boolean = false,
        val updatedForMoveOrRemove: Boolean = false,
    )

    data class DisplayAndWindowManager(
        val display: LSDisplay? = null,
        val windowManager: WindowManager? = null,
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

        val wm: WindowManager?
            get() = delegate.wm

        val itemTouchHelper: ItemTouchHelper
            get() = delegate.itemTouchHelper

        val state: State
            get() = delegate.state

        val isLocked: Boolean
            get() = delegate.isLocked()

        val lsDisplay: LSDisplay?
            get() = delegate.display

        abstract val widgetCornerRadiusKey: String
        abstract val containerCornerRadiusKey: String?

        abstract val ignoreWidgetTouchesKey: String?

        fun createLifecycleAwareWindowRecomposer(): Recomposer {
            return delegate.rootView.createLifecycleAwareWindowRecomposer(
                lifecycle = delegate.lifecycle,
            )
        }

        suspend fun updateWindow() {
            delegate.updateWindow()
        }
    }
}
