package tk.zwander.lockscreenwidgets

import android.app.Application
import tk.zwander.unblacklister.disableApiBlacklist

class App : Application() {
    companion object {
        val DEBUG = BuildConfig.DEBUG
    }

    override fun onCreate() {
        super.onCreate()
        disableApiBlacklist()
    }
}