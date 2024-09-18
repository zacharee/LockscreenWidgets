package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.bugsnag.android.Bugsnag
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private var _logFileHandle: BufferedWriter? = null
    private val logFileHandle: BufferedWriter
        get() {
            synchronized(logFile) {
                if (!logFile.exists()) {
                    logFile.createNewFile()

                    try {
                        _logFileHandle?.close()
                    } catch (_: Throwable) {}

                    _logFileHandle = createLogFileWriter()
                }

                return _logFileHandle ?: createLogFileWriter().also {
                    _logFileHandle = it
                }
            }
        }

    private fun createLogFileWriter(): BufferedWriter = FileOutputStream(logFile, true).bufferedWriter()

    fun debugLog(message: String, throwable: Throwable? = Exception()) {
        Bugsnag.leaveBreadcrumb(message)

        if (context.isDebug) {
            val fullMessage = generateFullMessage(message, throwable)

            Log.e(DEBUG_LOG_TAG, fullMessage)

            synchronized(logFile) {
                logFileHandle.write("\n\n$fullMessage")
            }
        }
    }

    fun normalLog(message: String, throwable: Throwable? = Exception()) {
        val fullMessage = generateFullMessage(message, throwable)

        Log.e(NORMAL_LOG_TAG, fullMessage)
        Bugsnag.leaveBreadcrumb(message)

        if (context.isDebug) {
            synchronized(logFile) {
                logFileHandle.write("\n\n$fullMessage")
            }
        }
    }

    fun resetDebugLog() {
        synchronized(logFile) {
            logFile.delete()
        }
    }

    fun exportLog(out: OutputStream) {
        synchronized(logFile) {
            out.use { output ->
                try {
                    logFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } catch (e: Exception) {
                    Log.e(NORMAL_LOG_TAG, "Failed to export log.", e)
                }
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
