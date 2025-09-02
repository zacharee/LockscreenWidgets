package tk.zwander.common.util

import android.app.KeyguardManager
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.os.PowerManager
import android.view.WindowManager

val Context.windowManager: WindowManager
    get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager

val Context.keyguardManager: KeyguardManager
    get() = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

val Context.powerManager: PowerManager
    get() = getSystemService(POWER_SERVICE) as PowerManager
