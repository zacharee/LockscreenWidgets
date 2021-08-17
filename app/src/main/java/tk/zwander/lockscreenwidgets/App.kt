package tk.zwander.lockscreenwidgets

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass
import tk.zwander.lockscreenwidgets.tiles.NCTile
import tk.zwander.lockscreenwidgets.tiles.widget.*
import tk.zwander.lockscreenwidgets.util.GlobalExceptionHandler
import tk.zwander.lockscreenwidgets.util.isOneUI
import tk.zwander.lockscreenwidgets.util.migrationManager

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
        @SuppressLint("StaticFieldLeak")
        var globalContext: Context? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()

        migrationManager.runMigrations()

        globalContext = this

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this, previousHandler))

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

            val components = arrayOf(
                WidgetTileOne::class.java,
                WidgetTileTwo::class.java,
                WidgetTileThree::class.java,
                WidgetTileFour::class.java,
                WidgetTileFive::class.java
            )

            components.forEach {
                packageManager.setComponentEnabledSetting(
                    ComponentName(this, it),
                    if (isOneUI) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    0
                )
            }
        }
    }
}