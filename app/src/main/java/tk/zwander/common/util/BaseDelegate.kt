package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.*
import androidx.annotation.CallSuper
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.*
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.zwander.lswinterconnect.safeApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tk.zwander.common.compose.util.widgetViewCacheRegistry
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.data.provider.ICurrentWidgetsProvider
import tk.zwander.common.data.provider.IRowColumProvider
import tk.zwander.common.host.WidgetHostCompat
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.mitigations.SafeContextWrapper
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
abstract class BaseDelegate<State : Any>(
    context: Context,
    open val targetDisplayId: StateFlow<String>,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    protected val displayFlow: StateFlow<LSDisplay?> by lazy {
        targetDisplayId.flatMapLatest {
            lsDisplayManager.collectDisplay(it)
        }.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = lsDisplayManager.findDisplayByStringId(targetDisplayId.value),
        )
    }

    val screenOrientation: Int?
        get() = this@BaseDelegate.display?.screenOrientation

    abstract val viewModel: BaseViewModel<out State, out BaseDelegate<State>>

    abstract val state: MutableStateFlow<State>

    protected abstract val prefsHandler: HandlerRegistry
    protected abstract val params: WindowManager.LayoutParams
    protected abstract val rootView: ViewGroup

    protected val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    protected val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry by lazy { savedStateRegistryController.savedStateRegistry }

    val isAttached: Boolean
        get() = rootView.isAttachedToWindow

    private val rootViewAttachmentStateListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            onRootViewAttached()
        }

        override fun onViewDetachedFromWindow(v: View) {
            onRootViewDetached()
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
        (rootView as? AbstractComposeView)
            ?.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        // Will listen for frame prefs with ID -2 because of the drawer delegate.
        prefsHandler.register(this, holderId)
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
        viewModel.viewModelScope.launch {
            preloadViews()
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
        prefsHandler.unregister(this, holderId)
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
                if (event.remove && currentWidgets.any { it.id == event.item?.id }) {
                    val newWidgets = currentWidgets.toMutableSet().apply {
                        removeIf { it.id == event.item?.id }
                        when (event.item?.safeType) {
                            WidgetType.WIDGET -> widgetHost.deleteAppWidgetId(event.item.id)
                            WidgetType.SHORTCUT,
                            WidgetType.LAUNCHER_SHORTCUT,
                            WidgetType.LAUNCHER_ITEM -> idManager.removeShortcutId(event.item.id)

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
        val newState = transform(state.value)

        if (newState != state) {
            logUtils.debugLog("Updating state from\n$state\nto\n$newState", null)
        }

        state.value = newState
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

    protected abstract suspend fun updateWindow()

    protected suspend fun updateOverlay() {
        withContext(Dispatchers.Main) {
            wm?.safeUpdateViewLayout(rootView, params)
        }
    }

    @CallSuper
    protected open fun onRootViewAttached() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    @CallSuper
    protected open fun onRootViewDetached() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    protected suspend fun preloadViews() = coroutineScope {
        launch(Dispatchers.IO) {
            val currentWidgets = currentWidgets.toList()
            val viewCacheRegistry = context.widgetViewCacheRegistry
            val widgetManager = appWidgetManager

            val maxPerPage = rowCount * colCount
            var countDown = maxPerPage
            var perPage = 0

            for (i in 0 until min(currentWidgets.size, maxPerPage)) {
                val size = currentWidgets[i].safeSize
                val takesUp = size.safeWidgetHeightSpan * size.safeWidgetWidthSpan

                countDown -= takesUp
                perPage++

                if (countDown < 0) {
                    break
                }
            }

            currentWidgets.take(perPage).forEach { widget ->
                val providerInfo = try {
                    widgetManager.getAppWidgetInfo(widget.id)
                } catch (_: Throwable) {
                    null
                }

                providerInfo?.let {
                    launch(Dispatchers.Main) {
                        viewCacheRegistry.getOrCreateView(
                            context = SafeContextWrapper(context = safeApplicationContext),
                            appWidgetId = widget.id,
                            appWidget = it,
                        )
                    }
                }
            }
        }
    }

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

        val state: MutableStateFlow<State>
            get() = delegate.state

        override val holderId: Int
            get() = delegate.holderId

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

        abstract val ignoreWidgetTouchesKey: Pair<String, SharedPreferences>?
        abstract val doubleTapTurnOffDisplayKey: String?

        suspend fun updateWindow() {
            delegate.updateWindow()
        }
    }
}
