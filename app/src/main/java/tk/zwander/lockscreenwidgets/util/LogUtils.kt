package tk.zwander.lockscreenwidgets.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import java.io.File
import java.io.OutputStream

class LogUtils private constructor(private val context: Context) {
    companion object {
        const val DEBUG_LOG_TAG = "LockscreenWidgetsDebug"

        @SuppressLint("StaticFieldLeak")
        private var instance: LogUtils? = null

        fun getInstance(context: Context): LogUtils {
            return instance ?: LogUtils(context.safeApplicationContext).also {
                instance = it
            }
        }
    }

    private val logFile = File(context.cacheDir, "log.txt")

    fun debugLog(message: String, throwable: Throwable = Exception()) {
        if (context.isDebug) {
            val fullMessage = "${message}\n${Log.getStackTraceString(throwable)}"

            Log.e(DEBUG_LOG_TAG, fullMessage)

            logFile.appendText("\n\n$fullMessage")
        }
    }

    fun resetDebugLog() {
        logFile.delete()
    }

    fun exportLog(out: OutputStream) {
        out.use { output ->
            logFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }
}
