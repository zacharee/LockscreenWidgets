package tk.zwander.lockscreenwidgets

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import tk.zwander.lockscreenwidgets.tiles.NCTile
import tk.zwander.lockscreenwidgets.util.isOneUI

/**
 * The main application.
 * Not much is happening here, but it's still important.
 * As soon as the app starts, we disable Android's
 * hidden API blacklist, since Lockscreen Widgets
 * uses quite a few of them. We also need to
 * set the component state of the "show in NC"
 * QS tile depending on whether the user is
 * running One UI or not.
 */
class App : Application() {
    companion object {
        const val DEBUG_LOG_TAG = "LockscreenWidgetsDebug"
    }

    override fun onCreate() {
        super.onCreate()
        //Make sure we can access hidden APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }

        //This should only run on Nougat and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //We don't want the NC tile to show on non-One UI devices.
            packageManager.setComponentEnabledSetting(
                ComponentName(this, NCTile::class.java),
                if (isOneUI) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0
            )
        }
    }
}