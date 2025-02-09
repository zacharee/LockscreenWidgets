package tk.zwander.lockscreenwidgets.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import com.bugsnag.android.Bugsnag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tk.zwander.common.util.AccessibilityUtils.runAccessibilityJob
import tk.zwander.common.util.AccessibilityUtils.runWindowOperation
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.copyCompat
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.handler
import tk.zwander.common.util.keyguardManager
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.appwidget.IDListProvider
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate
import tk.zwander.lockscreenwidgets.util.SecondaryWidgetFrameDelegate
import tk.zwander.widgetdrawer.util.DrawerDelegate

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
    private val frameDelegate: MainWidgetFrameDelegate
        get() = MainWidgetFrameDelegate.getInstance(this)
    private val drawerDelegate: DrawerDelegate
        get() = DrawerDelegate.getInstance(this)
    private val secondaryFrameDelegates = hashMapOf<Int, SecondaryWidgetFrameDelegate>()

    private val sharedPreferencesChangeHandler = HandlerRegistry {
        handler(PrefManager.KEY_ACCESSIBILITY_EVENT_DELAY) {
            serviceInfo?.let {
                it.notificationTimeout = prefManager.accessibilityEventDelay.toLong()
                serviceInfo = it
            }
        }
        handler(PrefManager.KEY_DEBUG_LOG) {
            IDListProvider.sendUpdate(this@Accessibility)
        }
        handler(PrefManager.KEY_CURRENT_FRAMES) {
            val newFrameIds = prefManager.currentSecondaryFrames
            val currentFrames = secondaryFrameDelegates

            val removedFrames = currentFrames.filter { (id, _) -> !newFrameIds.contains(id) }
            val addedFrameIds = newFrameIds.filter { !currentFrames.containsKey(it) }

            removedFrames.forEach { (id, frame) ->
                frame.onDestroy()
                FramePrefs.removeFrame(this@Accessibility, id)
                currentFrames.remove(id)
            }

            addedFrameIds.forEach { id ->
                val newFrame = SecondaryWidgetFrameDelegate(this@Accessibility, id)
                newFrame.onCreate()
                currentFrames.values.firstOrNull()?.let { referenceFrame ->
                    newFrame.updateState { referenceFrame.state }
                    newFrame.updateCommonState { referenceFrame.commonState }
                }
                currentFrames[id] = newFrame
            }
        }
    }

    private var state = State()

    @SuppressLint("ClickableViewAccessibility", "NewApi")
    override fun onCreate() {
        super.onCreate()

        sharedPreferencesChangeHandler.register(this)
        eventManager.addObserver(this)
        Bugsnag.leaveBreadcrumb("Accessibility service created.")
    }

    override fun onServiceConnected() {
        serviceInfo = serviceInfo.apply {
            notificationTimeout = prefManager.accessibilityEventDelay.toLong()
        }

        frameDelegate.onCreate()
        drawerDelegate.onCreate()

        prefManager.currentSecondaryFrames.forEach { secondaryId ->
            secondaryFrameDelegates[secondaryId] = SecondaryWidgetFrameDelegate(this, secondaryId).also {
                it.onCreate()
            }
        }

        eventManager.sendEvent(Event.RequestNotificationCount)
        Bugsnag.leaveBreadcrumb("Accessibility service connected.")

        launch(Dispatchers.Main) {
            runWindowOperation(
                frameDelegates = secondaryFrameDelegates + (-1 to frameDelegate),
                drawerDelegate = drawerDelegate,
                isScreenOn = power.isInteractive,
                isOnKeyguard = kgm.isKeyguardLocked,
                getWindows = ::getWindowsSafely,
                initialRun = true,
                wm = wm,
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        //Since we're launching our logic on the main Thread, it's possible
        //that [event] will be reused by Android, causing some crash issues.
        //Make a copy that is recycled later.
        val eventCopy = event.copyCompat()

        state.accessibilityJob?.cancel()
        updateState {
            it.copy(
                accessibilityJob = runAccessibilityJob(
                    context = this@Accessibility,
                    event = eventCopy,
                    frameDelegates = secondaryFrameDelegates + (-1 to frameDelegate),
                    drawerDelegate = drawerDelegate,
                    power = power,
                    kgm = kgm,
                    wm = wm,
                    imm = imm,
                    getWindows = ::getWindowsSafely,
                ),
            )
        }
    }

    override fun onInterrupt() {
        Bugsnag.leaveBreadcrumb("Accessibility service interrupted.")
    }

    override fun onEvent(event: Event) {
        when (event) {
            Event.ScreenOff -> {
                state.accessibilityJob?.cancel()
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
        secondaryFrameDelegates.forEach { (_, delegate) ->
            delegate.onDestroy()
        }

        eventManager.removeObserver(this)

        Bugsnag.leaveBreadcrumb("Accessibility service destroyed.")
    }

    private fun updateState(transform: (State) -> State) {
        val newState = transform(state)
        logUtils.debugLog("Updating accessibility state from $state to $newState")
        state = newState
    }

    private fun getWindowsSafely(): List<AccessibilityWindowInfo>? {
        return try {
            ArrayList(windows)
        } catch (e: SecurityException) {
            // Sometimes throws a SecurityException talking about mismatching
            // user IDs. In that case, return null and don't update any window-based
            // state items.
            null
        }
    }

    private data class State(
        val accessibilityJob: Job? = null,
    )
}