package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

val Context.logUtils: LogUtils
    get() = LogUtils.getInstance(this)

val peekLogUtils: LogUtils?
    get() = LogUtils.peekInstance()

class LogUtils private constructor(private val context: Context) {
    companion object {
        const val NORMAL_LOG_TAG = "LockscreenWidgets"
        const val DEBUG_LOG_TAG = "${NORMAL_LOG_TAG}Debug"

        @SuppressLint("StaticFieldLeak")
        private var instance: LogUtils? = null

        @Synchronized
        fun createInstance(context: Context) {
            if (instance != null) {
                return
            }

            instance = LogUtils(context.safeApplicationContext)
        }

        @Synchronized
        fun getInstance(context: Context): LogUtils {
            return instance ?: LogUtils(context.safeApplicationContext).also {
                instance = it
            }
        }

        @Synchronized
        fun peekInstance(): LogUtils? {
            return instance
        }
    }

    private val logFile = File(context.cacheDir, "log.txt")

    fun debugLog(message: String, throwable: Throwable? = Exception()) {
        if (context.isDebug) {
            val fullMessage = generateFullMessage(message, throwable)

            Log.e(DEBUG_LOG_TAG, fullMessage)

            logFile.appendText("\n\n$fullMessage")
        }
    }

    fun normalLog(message: String, throwable: Throwable? = Exception()) {
        val fullMessage = generateFullMessage(message, throwable)

        Log.e(NORMAL_LOG_TAG, fullMessage)

        if (context.isDebug) {
            logFile.appendText("\n\n$fullMessage")
        }
    }

    fun resetDebugLog() {
        logFile.delete()
    }

    fun exportLog(out: OutputStream) {
        out.use { output ->
            try {
                logFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } catch (e: Exception) {
                normalLog("Failed to export log.", e)
            }
        }
    }

    private fun generateFullMessage(message: String, throwable: Throwable?): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

        return "${formatter.format(Date())}\n${message}${throwable?.let { 
            "\n${Log.getStackTraceString(it)}"
        } ?: ""}"
    }
}
