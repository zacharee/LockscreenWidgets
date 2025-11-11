package tk.zwander.common.compose.util

import android.content.Context
import android.content.ContextWrapper
import tk.zwander.lockscreenwidgets.services.Accessibility

fun Context.findAccessibility(): Accessibility? {
    return when (this) {
        is Accessibility -> this
        is ContextWrapper -> baseContext.findAccessibility()
        else -> null
    }
}
