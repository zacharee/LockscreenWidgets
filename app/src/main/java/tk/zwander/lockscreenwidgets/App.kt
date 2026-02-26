package tk.zwander.lockscreenwidgets

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.BugsnagExitInfoPlugin
import com.bugsnag.android.Configuration
import com.bugsnag.android.ExitInfoPluginConfiguration
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.getkeepsafe.relinker.ReLinker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import tk.zwander.common.activities.add.BaseBindWidgetActivity
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.GlobalExceptionHandler
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.LSDisplayManager
import tk.zwander.common.util.LogUtils
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.globalState
import tk.zwander.common.util.handler
import tk.zwander.common.util.isOrHasDeadObject
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.migrationManager
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.shizuku.shizukuManager
import tk.zwander.lockscreenwidgets.activities.add.AddFrameWidgetActivity
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider
import tk.zwander.lockscreenwidgets.util.FramePrefs
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
class App : Application(), CoroutineScope by MainScope(), EventObserver {
    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var instance: App
    }

    //Listen for the screen turning on and off.
    //This shouldn't really be necessary, but there are some quirks in how
    //Android works that makes it helpful.
    @Suppress("DEPRECATION")
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    eventManager.sendEvent(Event.CloseSystemDialogs)
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

    private val proximityListener = object : SensorEventListener2 {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onFlushCompleted(sensor: Sensor?) {}

        override fun onSensorChanged(event: SensorEvent) {
            val dist = event.values[0]

            globalState.proxTooClose.value = dist < event.sensor.maximumRange
        }
    }

    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private val alarmManager by lazy { getSystemService(ALARM_SERVICE) as AlarmManager }
    private val lsDisplayManager by lazy { LSDisplayManager.getInstance(this) }

    private val prefsHandler by lazy {
        HandlerRegistry {
            handler(PrefManager.KEY_TOUCH_PROTECTION) {
                updateProxListener()
            }
        }
    }

    init {
        BugsnagPerformance.reportApplicationClassLoaded()
    }

    private external fun setUpAborter()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        eventManager.sendEvent(Event.TrimMemory(level))
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        if (prefManager.enableBugsnag) {
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
                enabledMetrics.rendering = true
                enabledMetrics.cpu = true
                enabledMetrics.memory = true
            })

            Bugsnag.addOnError {
                val error = it.originalError

                if (error is ClassCastException && error.stackTrace.firstOrNull()?.className?.contains("PmsHookApplication") == true) {
                    return@addOnError false
                }

                if (error?.stackTraceToString()?.contains("ììììï") == true) {
                    // Someone's doing some sort of stability or penetration testing, spoofing
                    // a bunch of device models, and causing constant crashes. This is a constant
                    // in the stacktrace among them.
                    // java.lang.IllegalArgumentException: View=DecorView@9b8761f[MainActivity] not attached to window manager
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
                            } catch (_: OutOfMemoryError) {
                                "Too large to parse."
                            },
                            "drawerWidgets" to try {
                                prefManager.gson.toJson(
                                    prefManager.drawerWidgets.map { widget ->
                                        widget.copy(icon = null, iconRes = null)
                                    }
                                )
                            } catch (_: OutOfMemoryError) {
                                "Too large to parse."
                            },
                        ).apply {
                            prefManager.currentSecondaryFramesWithStringDisplay.forEach { (frameId, frameDisplay) ->
                                put(
                                    "secondaryFrame${frameId},${frameDisplay}Widgets",
                                    try {
                                        prefManager.gson.toJson(
                                            FramePrefs.getWidgetsForFrame(this@App, frameId).map { widget ->
                                                widget.copy(icon = null, iconRes = null)
                                            },
                                        )
                                    } catch (_: OutOfMemoryError) {
                                        "Too large to parse."
                                    },
                                )
                            }
                        },
                    )
                } catch (_: OutOfMemoryError) {
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
                        "id_list_enabled" to prefManager.showDebugIdView,
                    ),
                )

                true
            }

            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this, previousHandler))
        }

        //Make sure we can access hidden APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.setHiddenApiExemptions("L")
        }

        LogUtils.createInstance(this)

        globalState.onCreate(this)

        @Suppress("DEPRECATION")
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            ContextCompat.RECEIVER_EXPORTED,
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE),
            true,
            nightModeListener,
        )

        updateProxListener()

        widgetHostCompat.startListening(this)

        val widgetsToDelete = mutableListOf<Int>()
        prefManager.widgetStackWidgets = HashMap(
            prefManager.widgetStackWidgets.mapNotNull { (stackId, widgets) ->
                val hasStackInfo = appWidgetManager.getAppWidgetInfo(stackId) != null

                if (!hasStackInfo) {
                    widgetsToDelete.add(stackId)
                    null
                } else {
                    widgets.removeAll { widget ->
                        (appWidgetManager.getAppWidgetInfo(widget.id) == null).also {
                            if (it) {
                                widgetsToDelete.add(widget.id)
                            }
                        }
                    }

                    stackId to widgets
                }
            }.toMap(),
        )

        widgetsToDelete.forEach {
            widgetHostCompat.deleteAppWidgetId(it)
        }

        prefManager.widgetStackWidgets.forEach { (stackId) ->
            WidgetStackProvider.update(this, intArrayOf(stackId))
        }

        prefsHandler.register(this)
        migrationManager.runMigrations()
        eventManager.addObserver(this)
        shizukuManager.onCreate()
        lsDisplayManager.onCreate()

        launch {
            lsDisplayManager.availableDisplays.collect {
                logUtils.debugLog("Updated displays ${it.keys.toList()}", null)
            }
        }

        launch(Dispatchers.Main) {
            globalState.wasOnKeyguard.collect { wasOnKeyguard ->
                if (!wasOnKeyguard) {
                    //Update the keyguard dismissal Activity that the lock screen
                    //has been dismissed.
                    eventManager.sendEvent(Event.LockscreenDismissed)
                }
            }
        }

        launch(Dispatchers.Main) {
            lsDisplayManager.displayPowerStates.collect {
                if (!it.anyOn) {
                    logUtils.debugLog("Received screen off", null)
                } else {
                    logUtils.debugLog("Received screen on", null)
                }

                updateProxListener()
            }
        }
    }

    override suspend fun onEvent(event: Event) {
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

    private fun updateProxListener() {
        if (prefManager.touchProtection) {
            registerProxListener()
        } else {
            unregisterProxListener()
        }
    }

    fun updateAutoChangeForStack(stackId: Int) {
        val changeInfo = prefManager.widgetStackAutoChange[stackId]

        val pi = PendingIntentCompat.getBroadcast(
            this,
            stackId + 50000,
            WidgetStackProvider.createSwapIntent(
                context = this,
                ids = intArrayOf(stackId),
                backward = false,
                autoSwap = true,
            ),
            0,
            true,
        )!!

        if (changeInfo?.first == true) {
            alarmManager.set(
                AlarmManager.RTC,
                System.currentTimeMillis() + changeInfo.second,
                pi,
            )
        } else {
            alarmManager.cancel(pi)
        }
    }

    private fun registerProxListener() {
        try {
            if (!sensorManager.getSensorList(Sensor.TYPE_PROXIMITY).isNullOrEmpty()) {
                sensorManager.registerListener(
                    proximityListener,
                    sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
                    1 * 200 * 1000, /* 200ms */
                    1 * 50 * 1000, /* 50ms */
                )
            }
        } catch (_: Throwable) {}
    }

    private fun unregisterProxListener() {
        try {
            sensorManager.unregisterListener(proximityListener)
        } catch (_: Throwable) {}
    }
}