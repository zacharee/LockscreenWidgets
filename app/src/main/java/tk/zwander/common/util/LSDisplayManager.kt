package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import tk.zwander.lockscreenwidgets.services.Accessibility
import kotlin.math.roundToInt

val Context.requireLsDisplayManager: LSDisplayManager
    get() = LSDisplayManager.getInstance(this)

val lsDisplayManager: LSDisplayManager?
    get() = LSDisplayManager.peekInstance()

class LSDisplayManager private constructor(context: Context) : ContextWrapper(context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: LSDisplayManager? = null

        fun peekInstance(): LSDisplayManager? {
            return instance
        }

        @Synchronized
        fun getInstance(context: Context): LSDisplayManager {
            if (instance == null && context !is Accessibility) {
                throw IllegalStateException("Accessibility service isn't running!")
            }

            return instance ?: LSDisplayManager(context).also {
                instance = it
            }
        }
    }

    val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
    val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            logUtils.debugLog("Display $displayId added", null)
            availableDisplays.value = availableDisplays.value.toMutableMap().apply {
                val display = displayManager.getDisplay(displayId) ?: run {
                    logUtils.debugLog("Unable to retrieve display $displayId", null)
                    return@apply
                }
                this[displayId] = LSDisplay(
                    display = display,
                    fontScale = createDisplayContext(display).resources.configuration.fontScale,
                )
            }
        }
        override fun onDisplayRemoved(displayId: Int) {
            logUtils.debugLog("Display $displayId removed", null)
            availableDisplays.value = availableDisplays.value.toMutableMap().apply {
                remove(displayId)
            }
        }
        override fun onDisplayChanged(displayId: Int) {
            logUtils.debugLog("Display $displayId changed", null)
            availableDisplays.value = availableDisplays.value.toMutableMap().apply {
                val display = displayManager.getDisplay(displayId)
                this[displayId] = LSDisplay(
                    display = display,
                    fontScale = createDisplayContext(display).resources.configuration.fontScale,
                )
            }
        }
    }

    private val availableDisplays = MutableStateFlow(mapOf<Int, LSDisplay>())

    fun onCreate() {
        availableDisplays.value = displayManager.displays.map {
            LSDisplay(
                display = it,
                fontScale = createDisplayContext(it).resources.configuration.fontScale,
            )
        }.associateBy { it.displayId }

        displayManager.registerDisplayListener(displayListener, null)
    }

    fun onDestroy() {
        displayManager.unregisterDisplayListener(displayListener)
        instance = null
    }

    fun getDisplay(displayId: Int): LSDisplay? {
        return availableDisplays.value[displayId]
    }

    fun requireDisplay(displayId: Int): LSDisplay {
        return getDisplay(displayId)
            ?: throw NullPointerException("Display for $displayId not found!")
    }

    fun collectDisplay(displayId: Int): Flow<LSDisplay?> {
        return availableDisplays.map { it[displayId] }
    }
}

class LSDisplay(val display: Display, val fontScale: Float) {
    val displayId: Int = display.displayId

    val density: Density by lazy {
        Density(density = realMetrics.density, fontScale = fontScale)
    }

    val realSize: Point by lazy {
        Point().apply {
            @Suppress("DEPRECATION")
            display.getRealSize(this)
        }
    }

    val realMetrics: DisplayMetrics by lazy {
        DisplayMetrics().apply {
            @Suppress("DEPRECATION")
            display.getRealMetrics(this)
        }
    }

    val screenOrientation: Int
        get() = display.rotation

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
