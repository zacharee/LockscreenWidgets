package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.annotation.CallSuper
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.data.provider.ICurrentWidgetsProvider
import tk.zwander.common.data.provider.IRowColumProvider
import tk.zwander.common.host.WidgetHostCompat
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.mitigations.SafeContextWrapper

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseDelegate<State : Any>(
    context: Context,
    open val targetDisplayId: String,
) : SafeContextWrapper(context = context),
    EventObserver, WidgetHostCompat.OnClickCallback, SavedStateRegistryOwner,
    ICurrentWidgetsProvider, IRowColumProvider {
    protected val kgm by lazy { keyguardManager }
    protected val widgetHost by lazy { widgetHostCompat }
    protected val wm: WindowManager?
        get() = lsDisplayManager.displayAndWmCache.value[this@BaseDelegate.display?.uniqueIdCompat]?.windowManager
    override val display: LSDisplay?
        get() = displayFlow.value

    override val context: Context
        get() = this

    protected val displayFlow: StateFlow<LSDisplay?> by lazy {
        lsDisplayManager.collectDisplay(targetDisplayId).stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = lsDisplayManager.findDisplayByStringId(targetDisplayId),
        )
    }

    val screenOrientation: Int?
        get() = this@BaseDelegate.display?.screenOrientation

    open var commonState: BaseState = BaseState()
        protected set

    abstract val viewModel: BaseViewModel<out State, out BaseDelegate<State>>

    abstract var state: State
        protected set

    protected abstract val prefsHandler: HandlerRegistry
    protected abstract val params: WindowManager.LayoutParams
    protected abstract val rootView: View

    protected val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    protected val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry by lazy { savedStateRegistryController.savedStateRegistry }

    val isAttached: Boolean
        get() = rootView.isAttachedToWindow

    protected open val rootViewAttachmentStateListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        override fun onViewDetachedFromWindow(v: View) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
    }

    protected open val recomposer by lazy {
        rootView.createAlwaysOnComposer(lifecycle = lifecycle)
    }

    var created = false
        protected set

    @CallSuper
    open fun onCreate() {
        if (created) {
            return
        }

        logUtils.debugLog("Creating ${this::class.java}", null)

        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        rootView.setViewTreeLifecycleOwner(this)
        rootView.setViewTreeSavedStateRegistryOwner(this)
        rootView.compositionContext = recomposer

        prefsHandler.register(this)
        eventManager.addObserver(this)
        widgetHost.addOnClickCallback(this)
        rootView.addOnAttachStateChangeListener(rootViewAttachmentStateListener)

        viewModel.viewModelScope.launch {
            displayFlow.collect {
                if (it != null) {
                    updateWindow()
                }
            }
        }

        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    logUtils.debugLog("Got double tap on RecyclerView ${this::class.java}", null)
                    viewModel.doubleTapTurnOffDisplayKey?.let {
                        if (prefManager.getBoolean(it, false)) {
                            eventManager.sendEvent(Event.TurnOffDisplay)
                        }
                    }
                    return false
                }
            },
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @SuppressLint("ClickableViewAccessibility")
            rootView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }
    }

    @CallSuper
    open suspend fun onDestroy() {
        logUtils.debugLog("Destroying ${this::class.java}", null)

        eventManager.removeObserver(this)
        prefsHandler.unregister(this)
        widgetHost.removeOnClickCallback(this)

        rootView.removeOnAttachStateChangeListener(rootViewAttachmentStateListener)
        recomposer.cancel()

        if (lifecycle.currentState > Lifecycle.State.INITIALIZED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }

        viewModel.viewModelScope.cancel()

        created = false
    }

    @SuppressLint("NotifyDataSetChanged")
    @CallSuper
    override suspend fun onEvent(event: Event) {
        when (event) {
            is Event.RemoveWidgetConfirmed -> {
                if (event.remove && currentWidgets.contains(event.item)) {
                    val newWidgets = currentWidgets.toMutableSet().apply {
                        remove(event.item)
                        when (event.item?.safeType) {
                            WidgetType.WIDGET -> widgetHost.deleteAppWidgetId(event.item.id)
                            WidgetType.SHORTCUT,
                            WidgetType.LAUNCHER_SHORTCUT,
                            WidgetType.LAUNCHER_ITEM -> shortcutIdManager.removeShortcutId(event.item.id)

                            else -> {}
                        }
                    }

                    viewModel.currentEditingInterfaceId.value = RecyclerView.NO_POSITION
                    currentWidgets = newWidgets
                }
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

    /**
     * Force the display to remain on, or remove that force.
     *
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

    protected abstract fun isLocked(): Boolean

    protected abstract suspend fun updateWindow()

    protected suspend fun updateOverlay() {
        withContext(Dispatchers.Main) {
            wm?.safeUpdateViewLayout(rootView, params)
        }
    }

    class BaseState

    @SuppressLint("StaticFieldLeak")
    abstract class BaseViewModel<State : Any, Delegate : BaseDelegate<State>>(
        protected val delegate: Delegate,
    ) : ViewModel(), IRowColumProvider, ICurrentWidgetsProvider {
        val itemToRemove = MutableStateFlow<WidgetData?>(null)
        val isResizingItem = MutableStateFlow(false)
        val currentEditingInterfaceId = MutableStateFlow(RecyclerView.NO_POSITION)

        val params: WindowManager.LayoutParams
            get() = delegate.params

        val wm: WindowManager?
            get() = delegate.wm

        val state: State
            get() = delegate.state

        override val holderId: Int
            get() = delegate.holderId

        val isLocked: Boolean
            get() = delegate.isLocked()

        override val display: LSDisplay?
            get() = delegate.display

        override val context: Context
            get() = delegate

        override val rowCount: Int
            get() = delegate.rowCount
        override val colCount: Int
            get() = delegate.colCount

        override var currentWidgets: Set<WidgetData>
            get() = delegate.currentWidgets
            set(value) {
                delegate.currentWidgets = value
            }

        abstract val widgetCornerRadiusKey: String
        abstract val containerCornerRadiusKey: String?

        abstract val ignoreWidgetTouchesKey: String?
        abstract val doubleTapTurnOffDisplayKey: String?

        suspend fun updateWindow() {
            delegate.updateWindow()
        }
    }
}
