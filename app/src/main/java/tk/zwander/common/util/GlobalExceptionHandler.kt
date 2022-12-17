package tk.zwander.common.util

import android.content.Context
import android.os.Build
import android.os.DeadObjectException
import android.os.DeadSystemException
import kotlin.system.exitProcess

class GlobalExceptionHandler(private val context: Context, private val previousHandler: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        if (e is DeadObjectException || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && e is DeadSystemException)) {
            exitProcess(100)
        } else {
            context.logUtils.normalLog("Uncaught Exception!", e)

            previousHandler?.uncaughtException(t, e)
        }
    }
}