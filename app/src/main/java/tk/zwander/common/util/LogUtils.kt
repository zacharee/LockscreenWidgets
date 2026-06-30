package tk.zwander.common.util

import android.content.Context
import dev.zwander.lswinterconnect.LogUtils

val Context.logUtils: LogUtils
    get() = LogUtils.getInstance(
        context = this,
        isDebug = { isDebug },
        writeToFile = true,
    )
