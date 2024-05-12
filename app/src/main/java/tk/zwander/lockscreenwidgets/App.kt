package tk.zwander.lockscreenwidgets

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.bugsnag.android.Bugsnag
import com.getkeepsafe.relinker.ReLinker
import org.lsposed.hiddenapibypass.HiddenApiBypass
import tk.zwander.common.util.Event
import tk.zwander.common.util.GlobalExceptionHandler
import tk.zwander.common.util.LogUtils
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.migrationManager
import tk.zwander.lockscreenwidgets.activities.add.AddFrameWidgetActivity
import tk.zwander.widgetdrawer.activities.add.AddDrawerWidgetActivity

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
    //Listen for the screen turning on and off.
    //This shouldn't really be necessary, but there are some quirks in how
    //Android works that makes it helpful.
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    logUtils.debugLog("Received screen off: ${power.isInteractive}")

                    if (!power.isInteractive) {
                        eventManager.sendEvent(Event.ScreenOff)

                        logUtils.debugLog("Sending screen off", null)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    logUtils.debugLog("Received screen on: ${power.isInteractive}")

                    if (power.isInteractive) {
                        eventManager.sendEvent(Event.ScreenOn)

                        logUtils.debugLog("Sending screen on", null)
                    }
                }
            }
        }
    }

    //Some widgets display differently depending on the system's dark mode.
    //Make sure the widgets are rebound if there's a change.
    private val nightModeListener = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            when (uri) {
                Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE) -> {
                    eventManager.sendEvent(Event.NightModeUpdate)
                }
            }
        }
    }

    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }

    override fun onCreate() {
        super.onCreate()

        ReLinker.loadLibrary(this, "bugsnag-ndk")
        ReLinker.loadLibrary(this, "bugsnag-plugin-android-anr")

        Bugsnag.start(this)

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this, previousHandler))

        //Make sure we can access hidden APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }

        LogUtils.createInstance(this)

        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) },
            ContextCompat.RECEIVER_EXPORTED,
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE),
            true,
            nightModeListener
        )

        migrationManager.runMigrations()

        eventManager.addListener<Event.LaunchAddWidget> {
            val intent = Intent(this, AddFrameWidgetActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
        }

        eventManager.addListener<Event.LaunchAddDrawerWidget> {
            AddDrawerWidgetActivity.launch(this, it.fromDrawer)
        }
    }
}