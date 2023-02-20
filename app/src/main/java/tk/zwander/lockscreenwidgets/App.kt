package tk.zwander.lockscreenwidgets

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.bugsnag.android.Bugsnag
import org.lsposed.hiddenapibypass.HiddenApiBypass
import tk.zwander.common.util.Event
import tk.zwander.common.util.GlobalExceptionHandler
import tk.zwander.common.util.eventManager
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
    companion object {
        @SuppressLint("StaticFieldLeak")
        var globalContext: Context? = null
            private set
    }

    //Listen for the screen turning on and off.
    //This shouldn't really be necessary, but there are some quirks in how
    //Android works that makes it helpful.
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> eventManager.sendEvent(Event.ScreenOff)
                Intent.ACTION_SCREEN_ON -> eventManager.sendEvent(Event.ScreenOn)
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

    override fun onCreate() {
        super.onCreate()

        Bugsnag.start(this)

        globalContext = this

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this, previousHandler))

        //Make sure we can access hidden APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }

        registerReceiver(
            screenStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_SCREEN_ON) }
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