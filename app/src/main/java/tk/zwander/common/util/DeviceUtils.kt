package tk.zwander.common.util

import android.content.Context
import android.os.Build

val Context.isTouchWiz: Boolean
    get() = packageManager.hasSystemFeature("com.samsung.feature.samsung_experience_mobile")

val Context.isOneUI: Boolean
    get() = isTouchWiz && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P