package tk.zwander.lockscreenwidgets.util

import android.graphics.drawable.Drawable
import android.os.Build
import android.view.ViewRootImpl
import tk.zwander.lockscreenwidgets.drawable.BackgroundBlurDrawableCompatDelegate

fun ViewRootImpl.createBackgroundBlurDrawableCompat(): BackgroundBlurDrawableCompatDelegate {
    return BackgroundBlurDrawableCompatDelegate.getInstance(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            createBackgroundBlurDrawable() as Drawable
        } else {
            null
        }
    )
}