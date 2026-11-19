package tk.zwander.common.data.window

import android.view.accessibility.AccessibilityNodeInfo
import tk.zwander.common.util.AccessibilityUtils

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
    val sysUiWindowViewIds: Set<String>,
    val sysUiWindowNodes: List<AccessibilityNodeInfo>,
    val topAppWindowPackageName: String?,
    val hasHideForPresentApp: Boolean,
    val nodeState: AccessibilityUtils.NodeState,
)
