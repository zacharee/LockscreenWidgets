package tk.zwander.lockscreenwidgets

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import tk.zwander.lockscreenwidgets.tiles.NCTile
import tk.zwander.lockscreenwidgets.util.isTouchWiz
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

        //We don't want the NC tile to show on non-One UI devices.
        packageManager.setComponentEnabledSetting(
            ComponentName(this, NCTile::class.java),
            if (isTouchWiz && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            0
        )
    }
}