package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.services.Accessibility
import kotlin.math.roundToInt

val Context.lsDisplayManager: LSDisplayManager
    get() = LSDisplayManager.getInstance(this)

fun LSDisplay?.orDefault(context: Context): LSDisplay {
    return this ?: context.lsDisplayManager.availableDisplays
        .value.values.find { it.display.displayId == Display.DEFAULT_DISPLAY } ?:
        context.lsDisplayManager.availableDisplays.value.values.first()
}

class LSDisplayManager private constructor(context: Context) : ContextWrapper(context), CoroutineScope by App.instance {
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

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            logUtils.normalLog("Display $displayId added", null)

            processDisplay(displayId)
        }
        override fun onDisplayRemoved(displayId: Int) {
            logUtils.debugLog("Display $displayId removed", null)
            availableDisplays.remove(displayId)
        }
        override fun onDisplayChanged(displayId: Int) {
            logUtils.debugLog("Display $displayId changed", null)

            processDisplay(displayId)
        }
    }

    val availableDisplays = MutableStateFlow(mapOf<Int, LSDisplay>())
    val isLikelyRazr = context.isLikelyRazr

    val displayPowerStates: StateFlow<DisplayPowerStates> = availableDisplays.map { currentDisplays ->
        val states = currentDisplays.entries.associate { (_, display) -> display.uniqueIdCompat to display.isOn }
        val anyOn = states.any { (_, value) -> value }

        DisplayPowerStates(
            displayStates = states,
            anyOn = anyOn,
        )
    }.stateIn(scope = this, started = SharingStarted.Eagerly, initialValue = DisplayPowerStates())

    val isAnyDisplayOn: Boolean
        get() = displayPowerStates.value.anyOn

    fun onCreate() {
        fetchDisplays()

        displayManager.registerDisplayListener(displayListener, null)
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
            availableDisplays.remove(displayId)
            return
        }

        if (!multiDisplaySupported && displayId != Display.DEFAULT_DISPLAY) {
            logUtils.debugLog("Multi-display isn't supported and display $displayId isn't default display", null)
            availableDisplays.remove(displayId)
            return
        }

        availableDisplays[displayId] = LSDisplay(
            display = display,
            fontScale = createDisplayContext(display).resources.configuration.fontScale,
            isLikelyRazr = isLikelyRazr,
        ).also {
            logUtils.debugLog("Processed display ${it.loggingId}", null)
        }
    }

    fun fetchDisplays() {
        val builtInDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_BUILT_IN_DISPLAYS)
        val allIncludingDisabled = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)
            .filter { it.isBuiltIn(isLikelyRazr) }
        // Samsung has extra filtering on DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED but not this.
        val samsung = displayManager.getDisplays("com.samsung.android.hardware.display.category.BUILTIN")
            .filter { it.isBuiltIn(isLikelyRazr) }
        val allDisplays = displayManager.displays.filter {
            it.isBuiltIn(isLikelyRazr)
        }
        val concatenatedDisplays = (builtInDisplays + allIncludingDisabled + allDisplays + samsung)
            .filter {
                (multiDisplaySupported || it.displayId == Display.DEFAULT_DISPLAY)
            }
            .toSet()

        availableDisplays.value = concatenatedDisplays.map {
            LSDisplay(
                display = it,
                fontScale = createDisplayContext(it).resources.configuration.fontScale,
                isLikelyRazr = isLikelyRazr,
            )
        }.associateBy { it.displayId }

        logUtils.debugLog("Got displays ${availableDisplays.value.values.map { it.loggingId }}", null)
    }
}

class LSDisplay(
    val display: Display,
    val fontScale: Float,
    private val isLikelyRazr: Boolean,
) {
    val displayId: Int = display.displayId

    val density: Density by lazy {
        Density(
            density = realMetrics.density,
            fontScale = fontScale,
        )
    }

    val realSize: Point by lazy {
        val size = rotatedRealSize
        val currentRotation = display.rotation

        if (currentRotation == Surface.ROTATION_270 || currentRotation == Surface.ROTATION_90) {
            Point(size.y, size.x)
        } else {
            size
        }
    }

    val rotatedRealSize: Point
        get() = Point().apply {
            @Suppress("DEPRECATION")
            display.getRealSize(this)
        }

    val realMetrics: DisplayMetrics by lazy {
        DisplayMetrics().apply {
            @Suppress("DEPRECATION")
            display.getRealMetrics(this)
        }
    }

    val screenOrientation: Int
        get() = display.rotation

    val uniqueIdCompat: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            display.uniqueId ?: display.displayId.toString()
        } else {
            display.displayId.toString()
        }

    val loggingId: String
        get() = "$uniqueIdCompat,$displayId"

    val isOn: Boolean
        get() = display.state == Display.STATE_ON

    val isBuiltIn: Boolean
        get() = display.isBuiltIn(isLikelyRazr)

    fun dpToPx(dpValue: Number): Int {
        return with (density) {
            dpValue.toDouble().dp.roundToPx()
        }
    }

    fun pxToDp(pxValue: Number): Float {
        return with (density) {
            pxValue.toDouble().roundToInt().toDp().value
        }
    }
}

data class DisplayPowerStates(
    val displayStates: Map<String, Boolean> = mapOf(),
    val anyOn: Boolean = false,
)

private fun Display.isBuiltIn(isLikelyRazr: Boolean): Boolean {
    return type == Display.TYPE_INTERNAL || (isLikelyRazr && displayId == 1)
}

object DisplayCache {
    val displayAndWmCache = MutableStateFlow<Map<String, DisplayAndWindowManager>>(mapOf())

    suspend fun handleDisplayUpdates(
        accessibility: Accessibility,
    ) {
        accessibility.lsDisplayManager.availableDisplays.collect { displays ->
            // Not super efficient since any time the available displays change
            // every cached display will be wiped and recreated, but there
            // isn't a great way to check for stale Display objects.
            displayAndWmCache.value = displays.values.associate { display ->
                val displayContext = accessibility.createDisplayContextCompat(display.display)
                val windowManager =
                    displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                display.uniqueIdCompat to DisplayAndWindowManager(display, windowManager)
            }
        }
    }

    data class DisplayAndWindowManager(
        val display: LSDisplay? = null,
        val windowManager: WindowManager? = null,
    )
}
