package tk.zwander.common.util

import android.view.View
import android.view.ViewGroup

fun <T : View> T.andRemoveFromParent(): T {
    (parent as? ViewGroup)?.removeView(this)
    return this
}
