package tk.zwander.lockscreenwidgets.util

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import tk.zwander.lockscreenwidgets.App

fun WindowManager.safeAddView(view: View, params: ViewGroup.LayoutParams): Boolean {
    return try {
        addView(view, params)
        true
    } catch (e: Exception) {
        App.globalContext?.logUtils?.debugLog("Error adding view", e)
        false
    }
}

fun WindowManager.safeRemoveView(view: View): Boolean {
    return try {
        removeView(view)
        true
    } catch (e: Exception) {
        App.globalContext?.logUtils?.debugLog("Error removing view", e)
        false
    }
}

fun WindowManager.safeRemoveViewImmediate(view: View): Boolean {
    return try {
        removeViewImmediate(view)
        true
    } catch (e: Exception) {
        App.globalContext?.logUtils?.debugLog("Error removing view immediate", e)
        false
    }
}

fun WindowManager.safeUpdateViewLayout(view: View, params: ViewGroup.LayoutParams): Boolean {
    return try {
        updateViewLayout(view, params)
        true
    } catch (e: Exception) {
        App.globalContext?.logUtils?.debugLog("Error updating view", e)
        false
    }
}
