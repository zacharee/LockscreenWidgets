package dev.zwander.lswwallpaper

import android.app.Application
import android.content.Context
import android.os.Build
import com.bugsnag.android.Bugsnag
import dev.zwander.lswinterconnect.LogUtils
import dev.zwander.lswinterconnect.safeApplicationContext
import org.lsposed.hiddenapibypass.HiddenApiBypass

val Context.logUtils: LogUtils
    get() = LogUtils.getInstance(
        context = safeApplicationContext,
        isDebug = { false },
        writeToFile = false,
    )

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }

        Bugsnag.start(this)

        logUtils.debugLog("Starting wallpaper server app", null)
    }
}
