package tk.zwander.lockscreenwidgets.util

import android.app.KeyguardManager
import android.content.Context
import android.view.WindowManager

val Context.windowManager: WindowManager
    get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

val Context.keyguardManager: KeyguardManager
    get() = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager