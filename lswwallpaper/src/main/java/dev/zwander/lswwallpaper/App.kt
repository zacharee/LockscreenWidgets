package dev.zwander.lswwallpaper

import android.app.Application
import android.os.Build
import com.bugsnag.android.Bugsnag
import dev.zwander.lswinterconnect.LogUtils
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }

        Bugsnag.start(this)

        LogUtils.getInstance(
            context = this,
            isDebug = { false },
            writeToFile = false,
        )
    }
}
