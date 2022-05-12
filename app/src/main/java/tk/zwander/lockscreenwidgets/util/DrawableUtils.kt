package tk.zwander.lockscreenwidgets.util

import android.os.Build
import android.view.ViewRootImpl
import tk.zwander.lockscreenwidgets.drawable.BackgroundBlurDrawableCompatDelegate

fun ViewRootImpl.createBackgroundBlurDrawableCompat(): BackgroundBlurDrawableCompatDelegate {
    return BackgroundBlurDrawableCompatDelegate(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createBackgroundBlurDrawable()
        } else {
            null
        }
    )
}