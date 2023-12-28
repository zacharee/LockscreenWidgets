package tk.zwander.lockscreenwidgets.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tk.zwander.common.util.AccessibilityUtils.runAccessibilityJob
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.handler
import tk.zwander.common.util.keyguardManager
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.appwidget.IDListProvider
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate
import tk.zwander.widgetdrawer.util.DrawerDelegate

//Check if the Accessibility service is enabled
val Context.isAccessibilityEnabled: Boolean
    get() = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )?.contains(ComponentName(this, Accessibility::class.java).flattenToString()) ?: false

fun Context.openAccessibilitySettings() {
    //Samsung devices have a separate Activity for listing
    //installed Accessibility Services, for some reason.
    //It's exported and permission-free, at least on Android 10,
    //so attempt to launch it. A "dumb" try-catch is simpler
    //than a check for the existence and state of this Activity.
    //If the Installed Services Activity can't be launched,
    //just launch the normal Accessibility Activity.
    try {
        val accIntent = Intent(Intent.ACTION_MAIN)
        accIntent.`package` = "com.android.settings"
        accIntent.component = ComponentName(
            "com.android.settings",
            "com.android.settings.Settings\$AccessibilityInstalledServiceActivity"
        )
        startActivity(accIntent)
    } catch (e: Exception) {
        logUtils.debugLog("Error opening Installed Services:", e)
        val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(accIntent)
    }
}

fun <T> AccessibilityNodeInfo?.use(block: (AccessibilityNodeInfo?) -> T): T {
    val result = block(this)
    @Suppress("DEPRECATION")
    this?.recycle()
    return result
}

fun AccessibilityEvent.copyCompat(): AccessibilityEvent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        AccessibilityEvent(this)
    } else {
        @Suppress("DEPRECATION")
        AccessibilityEvent.obtain(this)
    }
}

fun AccessibilityNodeInfo.hasVisibleIds(vararg ids: String): Boolean {
    return ids.contains(viewIdResourceName) && isVisibleToUser
}

fun AccessibilityNodeInfo.hasVisibleIds(ids: Iterable<String>): Boolean {
    return ids.contains(viewIdResourceName) && isVisibleToUser
}

/**
 * This is where a lot of the magic happens.
 * In Android 5.1+, there's a special overlay type: [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY].
 * Accessibility overlays can show over almost all other windows, including System UI, and therefore the keyguard/lock screen.
 * To actually use this overlay type, we need an AccessibilityService.
 *
 * This service is also used to detect what's onscreen and respond appropriately. For instance, if the user
 * has enabled the "Hide When Notification Shade Shown" option, we use our access to the screen content to
 * check that the left lock screen shortcut is no longer visible, since it hides when the notification shade
 * is shown.
 */
class Accessibility : AccessibilityService(), EventObserver, CoroutineScope by MainScope() {
    private val kgm by lazy { keyguardManager }
    private val wm by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }
    private val imm by lazy { getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    private val frameDelegate: WidgetFrameDelegate
        get() = WidgetFrameDelegate.getInstance(this)
    private val drawerDelegate: DrawerDelegate
        get() = DrawerDelegate.getInstance(this)

    private val sharedPreferencesChangeHandler = HandlerRegistry {
        handler(PrefManager.KEY_ACCESSIBILITY_EVENT_DELAY) {
            serviceInfo = serviceInfo?.apply {
                notificationTimeout = prefManager.accessibilityEventDelay.toLong()
            }
        }
        handler(PrefManager.KEY_DEBUG_LOG) {
            IDListProvider.sendUpdate(this@Accessibility)
        }
    }

    private var state = State()

    @SuppressLint("ClickableViewAccessibility", "NewApi")
    override fun onCreate() {
        super.onCreate()

        sharedPreferencesChangeHandler.register(this)
        eventManager.addObserver(this)
    }

    override fun onServiceConnected() {
        eventManager.sendEvent(Event.RequestNotificationCount)
        serviceInfo = serviceInfo.apply {
            notificationTimeout = prefManager.accessibilityEventDelay.toLong()
        }

        frameDelegate.onCreate()
        drawerDelegate.onCreate()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        //Since we're launching our logic on the main Thread, it's possible
        //that [event] will be reused by Android, causing some crash issues.
        //Make a copy that is recycled later.
        val eventCopy = event.copyCompat()

        if (System.currentTimeMillis() - state.lastScreenOnTime < 10)
            return

        state.accessibilityJob?.cancel()
        updateState {
            it.copy(
                accessibilityJob = launch {
                    runAccessibilityJob(
                        event = eventCopy,
                        frameDelegate = frameDelegate,
                        drawerDelegate = drawerDelegate,
                        power = power,
                        kgm = kgm,
                        wm = wm,
                        imm = imm,
                        getWindows = { ArrayList(windows) }
                    )
                }
            )
        }
    }

    override fun onInterrupt() {}

    override fun onEvent(event: Event) {
        when (event) {
            Event.ScreenOff -> {
                //Sometimes ACTION_SCREEN_OFF gets received *after* the display turns on,
                //so this check is here to make sure the screen is actually off when this
                //action is received.
                if (!power.isInteractive) {
                    logUtils.debugLog("Screen off")

                    state.accessibilityJob?.cancel()
                }
            }

            Event.ScreenOn -> {
                updateState {
                    it.copy(
                        lastScreenOnTime = System.currentTimeMillis()
                    )
                }

                logUtils.debugLog("Screen on")

//                state.accessibilityJob?.cancel()
            }

            else -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        cancel()
        sharedPreferencesChangeHandler.unregister(this)
        frameDelegate.onDestroy()
        drawerDelegate.onDestroy()

        eventManager.removeObserver(this)
    }

    private fun updateState(transform: (State) -> State) {
        val newState = transform(state)
        logUtils.debugLog("Updating accessibility state from $state to $newState")
        state = newState
    }

    private data class State(
        val lastScreenOnTime: Long = 0L,
        val accessibilityJob: Job? = null,
    )
}