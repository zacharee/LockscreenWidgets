package tk.zwander.common.util

import android.os.Build
import android.os.Process
import android.os.UserHandle

object UserHandleCompat {
    val SYSTEM: UserHandle
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            UserHandle.SYSTEM
        } else {
            @Suppress("DEPRECATION")
            UserHandle.OWNER
        }

    val myUserId: Int
        get() = Process.myUid() / 100_000
}
