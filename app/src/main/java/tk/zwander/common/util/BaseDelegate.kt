package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Looper
import android.view.*
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.compositionContext
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.zwander.lswinterconnect.safeApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tk.zwander.common.compose.util.widgetViewCacheRegistry
import tk.zwander.common.data.WidgetData
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
    protected open val secondaryViews: List<ViewGroup> = listOf()

    protected open val ownsLifecycle: Boolean
        get() = lifecycle == lifecycleRegistry

    protected val allViews: List<ViewGroup>
        get() = [rootView] + secondaryViews

    protected val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    protected val savedStateRegistryController by lazy { SavedStateRegistryController.create(this) }
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry by lazy { savedStateRegistryController.savedStateRegistry }

    val isAttached: Boolean
        get() = allViews.any { it.isAttachedToWindow }

    private val viewAttachmentStateListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            onViewAttached(v)
        }

        override fun onViewDetachedFromWindow(v: View) {
            onViewDetached(v)
        }
    }

    private var _recomposer: Recomposer? = null

    private val lifecycleObserver = LifecycleEventObserver { source, event ->
        logUtils.debugLog("Lifecycle event in ${this::class.java.name}: $event from $source", null)
    }

    var created = false
        protected set
    private var baseCreated = false
    private var destroyed = false

    private val reconcileLifecycle = Runnable {
        if (!baseCreated || destroyed) return@Runnable
        if (!ownsLifecycle) return@Runnable
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return@Runnable

        val shouldBeStarted = allViews.any { it.isAttachedToWindow }
        val state = lifecycleRegistry.currentState

        if (shouldBeStarted && state < Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } else if (!shouldBeStarted && state >= Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    @CallSuper
    open fun onCreate() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Must be called on the main thread" }
        check(!destroyed) { "BaseDelegate was already destroyed; create a new instance" }

        if (baseCreated) return

        logUtils.debugLog("Creating ${this@BaseDelegate::class.java}", null)

        if (ownsLifecycle) {
            _recomposer = rootView.createAlwaysOnComposer(lifecycle = lifecycle)

            savedStateRegistryController.performAttach()
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

            lifecycleRegistry.addObserver(lifecycleObserver)

            allViews.forEach {
                it.setViewTreeLifecycleOwner(this@BaseDelegate)
                it.setViewTreeSavedStateRegistryOwner(this@BaseDelegate)
                it.compositionContext = _recomposer
                (it as? AbstractComposeView)
                    ?.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                it.addOnAttachStateChangeListener(viewAttachmentStateListener)
            }
        }

        // Will listen for frame prefs with ID -2 because of the drawer delegate.
        prefsHandler.register(this@BaseDelegate, holderId)
        eventManager.addObserver(this@BaseDelegate)
        widgetHost.addOnClickCallback(this@BaseDelegate)

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
        viewModel.viewModelScope.launch {
            displayFlow.collect {
                withContext(Dispatchers.Main) {
                    onDisplayChanged()
                }
            }
        }

        val gestureDetector = GestureDetector(
            this@BaseDelegate,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    logUtils.debugLog("Got double tap on Delegate ${this@BaseDelegate::class.java}", null)
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

        scheduleLifecycleReconcile()
        baseCreated = true
        created = true
    }

    @CallSuper
    open fun onDestroy() {
        if (!baseCreated || destroyed) {
            return
        }

        logUtils.debugLog("Destroying ${this@BaseDelegate::class.java}", null)
        mainHandler.removeCallbacks(reconcileLifecycle)

        eventManager.removeObserver(this@BaseDelegate)
        prefsHandler.unregister(this@BaseDelegate, holderId)
        widgetHost.removeOnClickCallback(this@BaseDelegate)

        lifecycleRegistry.removeObserver(lifecycleObserver)

        if (ownsLifecycle) {
            allViews.forEach {
                (it as? AbstractComposeView)?.disposeComposition()
                wm?.safeRemoveViewImmediate(it)
            }

            if (lifecycle.currentState > Lifecycle.State.INITIALIZED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }

            _recomposer?.cancel()
            _recomposer = null

            allViews.forEach {
                it.removeOnAttachStateChangeListener(viewAttachmentStateListener)
                it.compositionContext = null
                it.setViewTreeLifecycleOwner(null)
                it.setViewTreeSavedStateRegistryOwner(null)
            }
        }

        viewModel.viewModelScope.cancel()

        destroyed = true
    }

    override suspend fun onEvent(event: Event) {}

    protected fun removeWidget(remove: Boolean, id: Int?) {
        if (remove && id != null && currentWidgets.any { it.id == id }) {
            val newWidgets = currentWidgets.toMutableSet().apply {
                removeIf { it.id == id }
                idManager.removeId(id)
            }

            viewModel.currentEditingInterfaceId.value = null
            currentWidgets = newWidgets
        }
    }

    override fun hasWidgetId(id: Int): Boolean {
        return currentWidgets.any { it.id == id }
    }

    override fun holderId(): Int {
        return holderId
    }

    open fun updateState(transform: (State) -> State) {
        val newState = transform(state.value)

        if (newState != state) {
            logUtils.debugLog("Updating state from\n${state.value}\n to \n$newState", null)
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

    protected open suspend fun onDisplayChanged() {}

    protected suspend fun updateOverlay() {
        withContext(Dispatchers.Main) {
            wm?.safeUpdateViewLayout(rootView, params)
        }
    }

    @CallSuper
    protected open fun onViewAttached(view: View) {
        scheduleLifecycleReconcile()
    }

    @CallSuper
    protected open fun onViewDetached(view: View) {
        scheduleLifecycleReconcile()
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

    private fun scheduleLifecycleReconcile() {
        mainHandler.removeCallbacks(reconcileLifecycle)
        mainHandler.post(reconcileLifecycle)
    }

    @SuppressLint("StaticFieldLeak")
    abstract class BaseViewModel<State : Any, Delegate : BaseDelegate<State>>(
        protected val delegate: Delegate,
    ) : ViewModel(), IRowColumProvider, ICurrentWidgetsProvider {
        val itemToRemove = MutableStateFlow<WidgetData?>(null)
        val isResizingItem = MutableStateFlow(false)
        val currentEditingInterfaceId = MutableStateFlow<String?>(null)

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

        fun updateState(transform: (State) -> State) {
            delegate.updateState(transform)
        }

        @Composable
        abstract fun GridWrapper(modifier: Modifier = Modifier)
    }
}
