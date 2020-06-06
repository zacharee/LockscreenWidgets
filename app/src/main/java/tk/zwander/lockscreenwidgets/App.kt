package tk.zwander.lockscreenwidgets

import android.app.Application
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.unblacklister.disableApiBlacklist

class App : Application() {
    companion object {
        const val DEBUG_LOG_TAG = "LockscreenWidgetsDebug"
    }

    override fun onCreate() {
        super.onCreate()
        //Make sure we can access hidden APIs
        disableApiBlacklist()
    }
}