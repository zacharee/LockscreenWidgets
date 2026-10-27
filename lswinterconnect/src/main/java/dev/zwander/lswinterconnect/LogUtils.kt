package dev.zwander.lswinterconnect

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.bugsnag.android.BreadcrumbType
import com.bugsnag.android.Severity
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

val peekLogUtils: LogUtils?
    get() = LogUtils.peekInstance()

// Using MainScope here should be fine since LogUtils is a process-scoped class.
class LogUtils private constructor(
    private val context: Context,
    private val isDebug: () -> Boolean,
    private val writeToFile: Boolean,
) : CoroutineScope by MainScope() {
    companion object {
        const val NORMAL_LOG_TAG = "LockscreenWidgets"
        const val DEBUG_LOG_TAG = "${NORMAL_LOG_TAG}Debug"

        private const val EXIT_INFO_PREFS = "log_utils_exit_info"
        private const val KEY_LAST_EXIT_TIMESTAMP = "last_exit_timestamp"
        private const val MAX_EXIT_REASONS = 10
        private const val MAX_ANR_TRACE_CHARS = 64 * 1024

        @SuppressLint("StaticFieldLeak")
        private var instance: LogUtils? = null

        @Synchronized
        fun createInstance(
            context: Context,
            isDebug: () -> Boolean,
            writeToFile: Boolean,
        ) {
            if (instance != null) {
                return
            }

            instance = LogUtils(
                context.safeApplicationContext,
                isDebug = isDebug,
                writeToFile = writeToFile,
            )
        }

        @Synchronized
        fun getInstance(
            context: Context,
            isDebug: () -> Boolean,
            writeToFile: Boolean,
        ): LogUtils {
            return instance ?: LogUtils(
                context.safeApplicationContext,
                isDebug = isDebug,
                writeToFile = writeToFile,
            ).also {
                instance = it
            }
        }

        @Synchronized
        fun peekInstance(): LogUtils? {
            return instance
        }
    }

    private val logFile = File(context.cacheDir, "log.txt")
    private val loggingDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val gson = Gson()

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

    fun debugLog(message: String, throwable: Throwable? = DefaultException(), leaveBreadcrumb: Boolean = true, extras: Map<String, Any?> = mapOf()) {
        launch(loggingDispatcher) {
            if (leaveBreadcrumb) {
                BugsnagUtils.leaveBreadcrumb(
                    message,
                    (throwable?.takeIf { it !is DefaultException }?.let { mapOf("error" to throwable.stringify()) } ?: mapOf()) + extras,
                    BreadcrumbType.LOG,
                )
            }

            if (isDebug()) {
                val fullMessage = generateFullMessage(
                    message,
                    throwable,
                    extras,
                )

                Log.e(DEBUG_LOG_TAG, fullMessage)

                if (writeToFile) {
                    synchronized(logFile) {
                        logFileHandle.writeSafely("\n\n$fullMessage")
                    }
                }
            }
        }
    }

    fun normalLog(message: String, throwable: Throwable? = DefaultException(), leaveBreadcrumb: Boolean = true, logToFile: Boolean = false, extras: Map<String, Any?> = mapOf()) {
        launch(loggingDispatcher) {
            val fullMessage = generateFullMessage(message, throwable, extras)

            Log.e(NORMAL_LOG_TAG, fullMessage)

            if (leaveBreadcrumb) {
                BugsnagUtils.leaveBreadcrumb(
                    message,
                    (throwable?.takeIf { it !is DefaultException }?.let { mapOf("error" to throwable.stringify()) } ?: mapOf()) + extras,
                    BreadcrumbType.ERROR,
                )
            }

            if (isDebug() || logToFile) {
                if (writeToFile) {
                    synchronized(logFile) {
                        logFileHandle.writeSafely("\n\n$fullMessage")
                    }
                }
            }
        }
    }

    /**
     * Log why this app's previous processes exited (API 30+). Bugsnag's ANR reports don't say what
     * the ANR was actually waiting on (input, broadcast, service, etc.), but [ApplicationExitInfo]
     * does. Each exit is only handled once. ANRs are additionally reported as handled events, with
     * the system's description and (truncated) trace attached.
     */
    fun logPreviousExitReasons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        launch(loggingDispatcher) {
            try {
                val prefs = context.getSharedPreferences(EXIT_INFO_PREFS, Context.MODE_PRIVATE)
                val lastSeen = prefs.getLong(KEY_LAST_EXIT_TIMESTAMP, 0L)
                val am = context.getSystemService(ActivityManager::class.java) ?: return@launch

                val newExits = am.getHistoricalProcessExitReasons(null, 0, MAX_EXIT_REASONS)
                    .filter { it.timestamp > lastSeen }
                    .sortedBy { it.timestamp }

                newExits.forEach { info ->
                    val reasonName = exitReasonName(info.reason)

                    debugLog(
                        message = "Previous process exit: $reasonName",
                        throwable = null,
                        extras = mapOf(
                            "reason" to reasonName,
                            "description" to info.description,
                            "importance" to info.importance,
                            "status" to info.status,
                            "timestamp" to info.timestamp,
                            "processName" to info.processName,
                        ),
                    )

                    if (info.reason == ApplicationExitInfo.REASON_ANR) {
                        val trace = try {
                            info.traceInputStream?.bufferedReader()?.use { reader ->
                                val buffer = CharArray(MAX_ANR_TRACE_CHARS)
                                val read = reader.read(buffer)

                                if (read > 0) String(buffer, 0, read) else null
                            }
                        } catch (e: Exception) {
                            "Unable to read trace: ${e.stringify()}"
                        }

                        BugsnagUtils.notify(PreviousAnrException(info.description)) {
                            it.severity = Severity.WARNING
                            it.addMetadata(
                                "previous_anr",
                                mapOf(
                                    "description" to info.description,
                                    "importance" to info.importance,
                                    "timestamp" to info.timestamp,
                                    "processName" to info.processName,
                                    "trace" to trace,
                                ),
                            )
                            true
                        }
                    }
                }

                newExits.lastOrNull()?.let {
                    prefs.edit { putLong(KEY_LAST_EXIT_TIMESTAMP, it.timestamp) }
                }
            } catch (e: Exception) {
                BugsnagUtils.leaveBreadcrumb(
                    "Unable to log previous exit reasons",
                    mapOf("error" to e.stringify()),
                    BreadcrumbType.ERROR,
                )
            }
        }
    }

    private fun exitReasonName(reason: Int): String {
        return when (reason) {
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            else -> "REASON_$reason"
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
                    BugsnagUtils.leaveBreadcrumb("Unable to export log.", mapOf("error" to e.stringify()), BreadcrumbType.ERROR)
                }
            }
        }
    }

    private fun generateFullMessage(message: String, throwable: Throwable?, extras: Map<String, Any?>): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

        return "${formatter.format(Date())}\n${message}\n${gson.toJson(extras)}\n${throwable?.let { 
            "\n${Log.getStackTraceString(it)}"
        } ?: ""}"
    }

    private fun BufferedWriter.writeSafely(string: String) {
        try {
            write(string)
            flush()
        } catch (e: Exception) {
            BugsnagUtils.leaveBreadcrumb("Unable to write to log file.", mapOf("error" to e.stringify()), BreadcrumbType.ERROR)
        }
    }

    class DefaultException : Exception()

    class PreviousAnrException(description: String?) : Exception("Previous ANR: $description")
}
