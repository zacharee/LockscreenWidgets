package tk.zwander.common.util

import android.content.Context
import android.os.Build
import tk.zwander.lockscreenwidgets.BuildConfig

val Context.isTouchWiz: Boolean
    get() = packageManager.hasSystemFeature("com.samsung.feature.samsung_experience_mobile") || BuildConfig.DEBUG

val Context.isOneUI: Boolean
    get() = isTouchWiz && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

val Context.isPixelUI: Boolean
    get() = packageManager.hasSystemFeature("com.google.android.feature.PIXEL_EXPERIENCE") || BuildConfig.DEBUG

val Context.isLikelyRazr: Boolean
    get() = packageManager.hasSystemFeature("com.motorola.hardware.cli") || BuildConfig.DEBUG
