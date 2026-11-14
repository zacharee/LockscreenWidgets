package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.ServiceManager
import android.view.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.zwander.lswinterconnect.peekLogUtils
import dev.zwander.lswinterconnect.safeApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.services.Accessibility
import java.util.*
import kotlin.math.roundToInt

val Context.lsDisplayManager: LSDisplayManager
    get() = LSDisplayManager.getInstance(this)

fun LSDisplay?.orDefault(context: Context): LSDisplay {
    return this ?: context.lsDisplayManager.defaultDisplay
}

class LSDisplayManager private constructor(context: Context) : ContextWrapper(context), CoroutineScope by App.scope {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: LSDisplayManager? = null

        @Synchronized
        fun getInstance(context: Context): LSDisplayManager {
            return instance ?: run {
                LSDisplayManager(context.safeApplicationContext).also {
                    instance = it
                }
            }
        }
    }

    val multiDisplaySupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
    val windowManager = IWindowManager.Stub.asInterface(ServiceManager.getService(WINDOW_SERVICE))

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            logUtils.debugLog("Display $displayId added", null)

            processDisplay(displayId)
        }

        override fun onDisplayRemoved(displayId: Int) {
            logUtils.debugLog("Display $displayId removed", null)
            removeDisplay(displayId)
        }

        override fun onDisplayChanged(displayId: Int) {
            logUtils.debugLog("Display $displayId changed", null)

            processDisplay(displayId)
        }
    }

    val availableDisplays = MutableStateFlow(mapOf<Int, LSDisplay>())
    val isLikelyRazr = context.isLikelyRazr
    val displayAndWmCache = MutableStateFlow<Map<String, DisplayAndWindowManager>>(mapOf())

    val _displayPowerStates = MutableStateFlow(DisplayPowerStates())
    val displayPowerStates: StateFlow<DisplayPowerStates> = _displayPowerStates.asStateFlow()

    val isAnyDisplayOn: Boolean
        get() = displayPowerStates.value.anyOn

    private val fetchLock = Any()
    private var fetchJob: Job? = null

    @Volatile
    private var fallbackDisplay: LSDisplay? = null

    /**
     * Returns the default display, even before [fetchDisplays] has completed.
     */
    val defaultDisplay: LSDisplay
        get() {
            availableDisplays.value.values
                .firstOrNull { it.display.displayId == Display.DEFAULT_DISPLAY }
                ?.let { return it }

            availableDisplays.value.values.firstOrNull()?.let { return it }

            return fallbackDisplay ?: run {
                val display = checkNotNull(displayManager.getDisplay(Display.DEFAULT_DISPLAY)) {
                    "Unable to find the default display"
                }

                LSDisplay(
                    display = display,
                    density = Density(createDisplayContextCompat(display)),
                ).also {
                    fallbackDisplay = it
                }
            }
        }

    fun onCreate() {
        displayManager.registerDisplayListener(displayListener, null)

        fetchDisplays()
    }

    fun findDisplayByStringId(displayId: String): LSDisplay? {
        logUtils.debugLog("Looking for display with string ID $displayId", null)

        return availableDisplays.value.values.firstOrNull { display ->
            display.uniqueIdCompat == displayId ||
                    display.displayId.toString() == displayId
        }
    }

    fun collectDisplay(displayId: String): Flow<LSDisplay?> {
        logUtils.debugLog("Collecting display with ID $displayId")

        return availableDisplays.map {
            it.values.firstOrNull { display ->
                display.uniqueIdCompat == displayId ||
                        (display.displayId == Display.DEFAULT_DISPLAY &&
                                displayId.toIntOrNull() == Display.DEFAULT_DISPLAY)
            }
        }
    }

    private fun processDisplay(displayId: Int) {
        val display = displayManager.getDisplay(displayId)

        if (display == null) {
            logUtils.debugLog("Unable to retrieve display $displayId for add", null)
            return
        }

        if (!display.isBuiltIn(isLikelyRazr)) {
            logUtils.debugLog("Display isn't internal, removing if exists and skipping processing", null)
            removeDisplay(displayId)
            return
        }

        if (!multiDisplaySupported && displayId != Display.DEFAULT_DISPLAY) {
            logUtils.debugLog("Multi-display isn't supported and display $displayId isn't default display", null)
            removeDisplay(displayId)
            return
        }

        setDisplay(
            LSDisplay(
                display = display,
                density = Density(createDisplayContextCompat(display)),
            ).also {
                logUtils.debugLog("Processed display ${it.loggingId}", null)
            },
        )
    }

    private fun setDisplay(display: LSDisplay) {
        val existingDisplay = availableDisplays[display.displayId]

        if (existingDisplay != display) {
            existingDisplay?.onDestroy(windowManager)
        }

        availableDisplays[display.displayId] = display
        display.onCreate(windowManager)

        updateDisplayStates()
    }

    private fun removeDisplay(id: Int) {
        val display = availableDisplays.remove(id)
        display?.onDestroy(windowManager)

        updateDisplayStates()
    }

    private fun updateDisplayStates() {
        val newStateMap = availableDisplays.value.values.associate { display ->
            display.uniqueIdCompat to display.isOn
        }
        _displayPowerStates.value = _displayPowerStates.value.copy(
            displayStates = newStateMap,
            anyOn = newStateMap.any { it.value },
        )
    }

    /**
     * Refresh the list of displays.
     *
     * The binder work happens on [Dispatchers.IO], but everything that mutates
     * [availableDisplays] happens back on the main thread.
     *
     * If a fetch is already running, its job is returned instead of starting another one.
     * Job is returned so that callers can `join()` on it if needed.
     */
    @SuppressLint("InlinedApi")
    fun fetchDisplays(): Job {
        synchronized(fetchLock) {
            fetchJob?.takeIf { it.isActive }?.let { return it }

            val job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    val builtInDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_BUILT_IN_DISPLAYS)
                    val allIncludingDisabled = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)
                    // Samsung has extra filtering on DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED but not this.
                    val samsung = displayManager.getDisplays("com.samsung.android.hardware.display.category.BUILTIN")
                    val allDisplays = displayManager.displays

                    val displays = (builtInDisplays + allIncludingDisabled + allDisplays + samsung)
                        .associateBy { it.displayId }
                        .values
                        .filter { display ->
                            display.isBuiltIn(isLikelyRazr) &&
                                    (multiDisplaySupported || display.displayId == Display.DEFAULT_DISPLAY)
                        }
                        .map { display ->
                            LSDisplay(
                                display = display,
                                density = Density(createDisplayContextCompat(display)),
                            )
                        }

                    withContext(Dispatchers.Main.immediate) {
                        displays.forEach(::setDisplay)

                        logUtils.debugLog(
                            "Got displays ${availableDisplays.value.values.map { it.loggingId }}",
                            null,
                        )
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) {
                        ensureActive()
                    }
                    logUtils.normalLog("Error fetching displays", e)
                }
            }

            fetchJob = job
            job.start()

            return job
        }
    }

    suspend fun handleDisplayUpdates(
        accessibility: Accessibility,
    ) {
        availableDisplays.collect { displays ->
            // Not super efficient since any time the available displays change
            // every cached display will be wiped and recreated, but there
            // isn't a great way to check for stale Display objects.
            displayAndWmCache.value = displays.values.associate { display ->
                val displayContext = accessibility.createDisplayContextCompat(display.display)
                val windowManager =
                    displayContext.getSystemService(WINDOW_SERVICE) as WindowManager

                display.uniqueIdCompat to DisplayAndWindowManager(display, windowManager)
            }
        }
    }
}

data class LSDisplay(
    val display: Display,
    val density: Density,
) {
    val displayId: Int = this@LSDisplay.display.displayId

    // `Display` objects are updated in place when the display changes, so a live rotation
    // getter can't be used to detect rotation changes. Snapshot it at construction instead,
    // so equals()/hashCode() change when a new LSDisplay is created for a rotated display.
    // This makes availableDisplays (and therefore displayFlow) emit on rotation.
    private val rotationAtCreation: Int = this@LSDisplay.display.rotation

    val realSize: Point by lazy {
        val size = rotatedRealSize
        val currentRotation = this@LSDisplay.display.rotation

        if (currentRotation == Surface.ROTATION_270 || currentRotation == Surface.ROTATION_90) {
            Point(size.y, size.x)
        } else {
            size
        }
    }

    val rotatedRealSize: Point
        get() = Point().apply {
            @Suppress("DEPRECATION")
            this@LSDisplay.display.getRealSize(this)
        }

    val rotatedRealSizeState: MutableStateFlow<Point> by lazy { MutableStateFlow(rotatedRealSize) }

    val screenOrientation: Int
        get() = this@LSDisplay.display.rotation

    val uniqueIdCompat: String
        get() = this@LSDisplay.display.uniqueIdCompat

    val loggingId: String
        get() = "$uniqueIdCompat,$displayId"

    val isOn: Boolean
        get() = this@LSDisplay.display.state == Display.STATE_ON

    private val rotationWatcher = object : IRotationWatcher.Stub() {
        override fun onRotationChanged(rotation: Int) {
            rotatedRealSizeState.value = rotatedRealSize
        }
    }

    fun dpToPx(dpValue: Number): Int {
        return with(density) {
            dpValue.toDouble().dp.roundToPx()
        }
    }

    fun pxToDp(pxValue: Number): Float {
        return with(density) {
            pxValue.toDouble().roundToInt().toDp().value
        }
    }

    fun onCreate(windowManager: IWindowManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                windowManager.watchRotation(rotationWatcher, displayId)
            } else {
                IWindowManager::class.java.getMethod(
                    "watchRotation",
                    IRotationWatcher::class.java,
                ).invoke(windowManager, rotationWatcher)
            }
        } catch (e: Exception) {
            peekLogUtils?.debugLog(
                message = "Unable to register rotation watcher",
                throwable = e,
            )
        }
    }

    fun onDestroy(windowManager: IWindowManager) {
        try {
            windowManager.removeRotationWatcher(rotationWatcher)
        } catch (e: Exception) {
            peekLogUtils?.debugLog(
                message = "Unable to unregister rotation watcher",
                throwable = e,
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is LSDisplay &&
                other.displayId == displayId &&
                other.uniqueIdCompat == uniqueIdCompat &&
                other.density.density == density.density &&
                other.realSize == realSize &&
                other.rotationAtCreation == rotationAtCreation
    }

    override fun hashCode(): Int {
        return Objects.hash(
            displayId,
            uniqueIdCompat,
            density.density,
            realSize,
            rotationAtCreation,
        )
    }
}

val Display.uniqueIdCompat: String
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uniqueId ?: displayId.toString()
        } else {
            displayId.toString()
        }
    }

data class DisplayPowerStates(
    val displayStates: Map<String, Boolean> = mapOf(),
    val anyOn: Boolean = false,
)

private fun Display.isBuiltIn(isLikelyRazr: Boolean): Boolean {
    return type == Display.TYPE_INTERNAL || (isLikelyRazr && displayId == 1)
}

data class DisplayAndWindowManager(
    val display: LSDisplay? = null,
    val windowManager: WindowManager? = null,
)
