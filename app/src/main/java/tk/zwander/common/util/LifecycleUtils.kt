package tk.zwander.common.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry

var LifecycleRegistry.safeCurrentState: Lifecycle.State
    get() = currentState
    set(value) {
        try {
            currentState = value
        } catch (e: IllegalStateException) {
            peekLogUtils?.debugLog("Error changing lifecycle state", e)
        }
    }
