@file:Suppress("unused")

package tk.zwander.common.util

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

fun WindowManager.safeAddView(view: View, params: ViewGroup.LayoutParams): Boolean {
    return try {
        addView(view, params)
        true
    } catch (e: Exception) {
        view.context.logUtils.debugLog("Error adding view", e)
        false
    }
}

fun WindowManager.safeRemoveView(view: View, logError: Boolean = true): Boolean {
    return try {
        removeView(view)
        true
    } catch (e: Exception) {
        if (logError) {
            view.context.logUtils.debugLog("Error removing view", e)
        }
        false
    }
}

fun WindowManager.safeRemoveViewImmediate(view: View, logError: Boolean = true): Boolean {
    return try {
        removeViewImmediate(view)
        true
    } catch (e: Exception) {
        if (logError) {
            view.context.logUtils.debugLog("Error removing view immediate", e)
        }
        false
    }
}

fun WindowManager.safeUpdateViewLayout(view: View, params: ViewGroup.LayoutParams): Boolean {
    return try {
        updateViewLayout(view, params)
        true
    } catch (e: Exception) {
        view.context.logUtils.debugLog("Error updating view", e)
        false
    }
}
