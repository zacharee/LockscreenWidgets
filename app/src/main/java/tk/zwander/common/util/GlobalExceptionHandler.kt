package tk.zwander.common.util

import android.content.Context
import android.os.DeadObjectException
import kotlin.system.exitProcess

class GlobalExceptionHandler(private val context: Context, private val previousHandler: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        if (e is DeadObjectException || e.hasDeadObjectExceptionCause()) {
            exitProcess(100)
        } else {
            context.logUtils.normalLog("Uncaught Exception!", e, false)

            previousHandler?.uncaughtException(t, e)
        }
    }

    private fun Throwable.hasDeadObjectExceptionCause(): Boolean {
        if (cause == null) {
            return false
        }

        if (cause is DeadObjectException) {
            return true
        }

        return cause?.hasDeadObjectExceptionCause() == true
    }
}