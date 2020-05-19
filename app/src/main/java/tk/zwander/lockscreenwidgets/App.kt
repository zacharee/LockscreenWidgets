package tk.zwander.lockscreenwidgets

import android.app.Application
import tk.zwander.unblacklister.disableApiBlacklist

class App : Application() {
    companion object {
        const val DEBUG = false
    }

    override fun onCreate() {
        super.onCreate()
        disableApiBlacklist()
    }
}