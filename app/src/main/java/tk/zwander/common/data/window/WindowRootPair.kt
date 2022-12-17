package tk.zwander.common.data.window

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Represents a pair of an Accessibility window and its
 * root, if available.
 */
data class WindowRootPair(
    val window: AccessibilityWindowInfo,
    val root: AccessibilityNodeInfo?
)