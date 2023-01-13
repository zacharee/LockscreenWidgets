package tk.zwander.common.data.window

import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Hold the info for retrieving windows in each Accessibility pass.
 */
data class WindowInfo(
    val windows: List<WindowRootPair>,
    val topAppWindowIndex: Int,
    val topNonSysUiWindowIndex: Int,
    val minSysUiWindowIndex: Int,
    val hasScreenOffMemoWindow: Boolean,
    val hasFaceWidgetsWindow: Boolean,
    val hasEdgePanelWindow: Boolean,
    val sysUiWindowViewIds: ConcurrentLinkedQueue<String>,
    val sysUiWindowNodes: ConcurrentLinkedQueue<AccessibilityNodeInfo>,
    val topAppWindowPackageName: String?,
)