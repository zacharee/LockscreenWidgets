package tk.zwander.common.util

import com.bugsnag.android.BreadcrumbType
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.OnErrorCallback

object BugsnagUtils {
    fun notify(e: Throwable, callback: OnErrorCallback? = null) {
        if (Bugsnag.isStarted()) {
            Bugsnag.notify(e, callback)
        }
    }

    fun leaveBreadcrumb(
        message: String,
        metadata: Map<String, Any?> = mapOf(),
        type: BreadcrumbType = BreadcrumbType.MANUAL,
    ) {
        if (Bugsnag.isStarted()) {
            Bugsnag.leaveBreadcrumb(message, metadata, type)
        }
    }
}
