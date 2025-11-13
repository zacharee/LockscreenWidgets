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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

val Context.requireLsDisplayManager: LSDisplayManager
    get() = LSDisplayManager.getInstance(this)

val lsDisplayManager: LSDisplayManager?
    get() = LSDisplayManager.peekInstance()

class LSDisplayManager private constructor(context: Context) : ContextWrapper(context), CoroutineScope by MainScope() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: LSDisplayManager? = null

        fun peekInstance(): LSDisplayManager? {
            return instance
        }

        @Synchronized
        fun getInstance(context: Context): LSDisplayManager {
            return instance ?: run {
                LSDisplayManager(context.safeApplicationContext).also {
                    instance = it
                }
            }
        }
    }

    val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            logUtils.debugLog("Display $displayId added", null)

            processDisplay(displayId)
        }
        override fun onDisplayRemoved(displayId: Int) {
            logUtils.debugLog("Display $displayId removed", null)
            availableDisplays.value = availableDisplays.value.toMutableMap().apply {
                remove(displayId)
            }
        }
        override fun onDisplayChanged(displayId: Int) {
            logUtils.debugLog("Display $displayId changed", null)

            processDisplay(displayId)
        }
    }

    val availableDisplays = MutableStateFlow(mapOf<Int, LSDisplay>())

    val displayPowerStates = availableDisplays.map { currentDisplays ->
        currentDisplays.entries.associate { (_, display) -> display.uniqueIdCompat to display.isOn }
    }.stateIn(this, SharingStarted.Eagerly, initialValue = mapOf())

    val isAnyDisplayOn = displayPowerStates.map { displayPowerStates ->
        displayPowerStates.any { (_, value) -> value }
    }.stateIn(this, SharingStarted.Eagerly, initialValue = false)

    fun onCreate() {
        val builtInDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_BUILT_IN_DISPLAYS)
        val allIncludingDisabled = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED)
            .filter { it.type == Display.TYPE_INTERNAL }
        // Samsung has extra filtering on DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED but not this.
        val samsung = displayManager.getDisplays("com.samsung.android.hardware.display.category.BUILTIN")
            .filter { it.type == Display.TYPE_INTERNAL }
        val allDisplays = displayManager.displays.filter { it.type == Display.TYPE_INTERNAL }
        val concatenatedDisplays = (builtInDisplays + allIncludingDisabled + allDisplays + samsung).toSet()

        availableDisplays.value = concatenatedDisplays.map {
                    LSDisplay(
                        display = it,
                        fontScale = createDisplayContext(it).resources.configuration.fontScale,
                    )
                }.associateBy { it.displayId }

        logUtils.debugLog("Got displays ${availableDisplays.value.values.map { it.loggingId }}", null)

        displayManager.registerDisplayListener(displayListener, null)
    }

    fun onDestroy() {
        logUtils.debugLog("Destroying LSDisplayManager", null)

        displayManager.unregisterDisplayListener(displayListener)
        instance = null
    }

    fun findDisplayByStringId(displayId: String): LSDisplay? {
        logUtils.debugLog("Looking for display with string ID $displayId", null)

        return availableDisplays.value.values.firstOrNull { display ->
            display.uniqueIdCompat == displayId ||
                    display.displayId.toString() == displayId
        }
    }

    fun requireDisplayByStringId(displayId: String): LSDisplay {
        return findDisplayByStringId(displayId)
            ?: throw NullPointerException("Display for $displayId not found! Available displays ${availableDisplays.value.values.map { it.loggingId }}")
    }

    fun collectDisplay(displayId: String): Flow<LSDisplay?> {
        logUtils.debugLog("Collecting display with ID $displayId")

        return availableDisplays.map {
            it.values.firstOrNull { display ->
                display.uniqueIdCompat == displayId
            }
        }
    }

    private fun processDisplay(displayId: Int) {
        val display = displayManager.getDisplay(displayId)

        if (display == null) {
            logUtils.debugLog("Unable to retrieve display $displayId for add", null)
            return
        }

        if (display.type != Display.TYPE_INTERNAL) {
            logUtils.debugLog("Display isn't internal, removing if exists and skipping processing", null)
            availableDisplays.value = availableDisplays.value.toMutableMap().apply {
                remove(displayId)
            }
            return
        }

        val newMap = availableDisplays.value.toMutableMap()
        newMap[displayId] = LSDisplay(
            display = display,
            fontScale = createDisplayContext(display).resources.configuration.fontScale,
        )

        availableDisplays.value = newMap
    }
}

class LSDisplay(val display: Display, val fontScale: Float) {
    val displayId: Int = display.displayId

    val density: Density by lazy {
        Density(density = realMetrics.density, fontScale = fontScale)
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
