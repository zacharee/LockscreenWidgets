package tk.zwander.lockscreenwidgets

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.BugsnagExitInfoPlugin
import com.bugsnag.android.Configuration
import com.bugsnag.android.ExitInfoPluginConfiguration
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.getkeepsafe.relinker.ReLinker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import tk.zwander.common.activities.add.BaseBindWidgetActivity
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.GlobalExceptionHandler
import tk.zwander.common.util.LogUtils
import tk.zwander.common.util.ShizukuService
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.isOrHasDeadObject
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.migrationManager
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.safeApplicationContext
import tk.zwander.lockscreenwidgets.activities.add.AddFrameWidgetActivity
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.widgetdrawer.activities.add.AddDrawerWidgetActivity
import kotlin.coroutines.CoroutineContext

val Context.app: App
    get() = safeApplicationContext as App

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
class App : Application(), CoroutineScope by MainScope(), EventObserver {
    //Listen for the screen turning on and off.
    //This shouldn't really be necessary, but there are some quirks in how
    //Android works that makes it helpful.
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    logUtils.debugLog("Received screen off: ${power.isInteractive}", null)

                    if (!power.isInteractive) {
                        eventManager.sendEvent(Event.ScreenOff)

                        logUtils.debugLog("Sending screen off", null)
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    logUtils.debugLog("Received screen on: ${power.isInteractive}", null)

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

    private val queuedCommands = ArrayList<Pair<CoroutineContext, IShizukuService.() -> Unit>>()
    private var userService: IShizukuService? = null
        set(value) {
            field = value

            if (value != null) {
                queuedCommands.forEach { (context, command) ->
                    launch(context) {
                        value.command()
                    }
                }
                queuedCommands.clear()
            }
        }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            userService = IShizukuService.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ShizukuService::class.java.canonicalName!!))
            .version(BuildConfig.VERSION_CODE + (if (BuildConfig.DEBUG) 101 else 0))
            .processNameSuffix("granter")
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)
            .tag("${packageName}_granter")
    }

    init {
        BugsnagPerformance.reportApplicationClassLoaded()
    }

    private external fun setUpAborter()

    override fun onCreate() {
        super.onCreate()

        ReLinker.loadLibrary(this, "bugsnag-ndk")
        ReLinker.loadLibrary(this, "bugsnag-plugin-android-anr")
        ReLinker.loadLibrary(this, "lockscreenwidgets")

        setUpAborter()

        Bugsnag.start(this, Configuration.load(this).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                addPlugin(BugsnagExitInfoPlugin(ExitInfoPluginConfiguration().apply {
                    includeLogcat = true
                }))
            }
            maxBreadcrumbs = 500
            projectPackages = setOf("tk.zwander.lockscreenwidgets", "tk.zwander.widgetdrawer", "tk.zwander.common")
        })
        BugsnagPerformance.start(PerformanceConfiguration.load(this).apply {
            autoInstrumentRendering = true
        })

        Bugsnag.addOnError {
            val error = it.originalError

            if (error is ClassCastException && error.stackTrace.firstOrNull()?.className?.contains("PmsHookApplication") == true) {
                return@addOnError false
            }

            if (error?.isOrHasDeadObject == true) {
                return@addOnError false
            }

            try {
                it.addMetadata(
                    "widget_data",
                    hashMapOf(
                        "currentWidgets" to try {
                            prefManager.gson.toJson(
                                prefManager.currentWidgets.map { widget ->
                                    widget.copy(icon = null, iconRes = null)
                                }
                            )
                        } catch (e: OutOfMemoryError) {
                            "Too large to parse."
                        },
                        "drawerWidgets" to try {
                            prefManager.gson.toJson(
                                prefManager.drawerWidgets.map { widget ->
                                    widget.copy(icon = null, iconRes = null)
                                }
                            )
                        } catch (e: OutOfMemoryError) {
                            "Too large to parse."
                        },
                    ).apply {
                        prefManager.currentSecondaryFrames.forEach { frameId ->
                            put(
                                "secondaryFrame${frameId}Widgets",
                                try {
                                    prefManager.gson.toJson(
                                        FramePrefs.getWidgetsForFrame(this@App, frameId).map { widget ->
                                            widget.copy(icon = null, iconRes = null)
                                        },
                                    )
                                } catch (e: OutOfMemoryError) {
                                    "Too large to parse."
                                },
                            )
                        }
                    },
                )
            } catch (e: OutOfMemoryError) {
                it.addMetadata(
                    "widget_data",
                    hashMapOf("OOM" to "OOM thrown when trying to add current widget data."),
                )
            }

            it.addMetadata(
                "settings",
                mapOf(
                    "drawer_enabled" to prefManager.drawerEnabled,
                    "frame_enabled" to prefManager.widgetFrameEnabled,
                ),
            )

            true
        }

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

        eventManager.addObserver(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Shizuku.addBinderReceivedListenerSticky {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    addUserService()
                } else {
                    Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                                addUserService()
                                Shizuku.removeRequestPermissionResultListener(this)
                            }
                        }
                    })
                }
            }
        }
    }

    override fun onEvent(event: Event) {
        when (event) {
            is Event.RemoveFrameConfirmed -> {
                if (event.confirmed && event.frameId != null) {
                    FramePrefs.removeFrame(this, event.frameId)
                }
            }
            is Event.LaunchAddDrawerWidget -> {
                AddDrawerWidgetActivity.launch(this, event.fromDrawer)
            }
            is Event.LaunchAddWidget -> {
                val intent = Intent(this, AddFrameWidgetActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra(BaseBindWidgetActivity.EXTRA_HOLDER_ID, event.frameId)

                startActivity(intent)
            }
            else -> {}
        }
    }

    fun postShizukuCommand(context: CoroutineContext, command: IShizukuService.() -> Unit) {
        if (userService != null) {
            launch(context) {
                command(userService!!)
            }
        } else {
            queuedCommands.add(context to command)
        }
    }

    private fun addUserService() {
        Shizuku.unbindUserService(
            serviceArgs,
            userServiceConnection,
            true
        )

        Shizuku.bindUserService(
            serviceArgs,
            userServiceConnection,
        )
    }
}