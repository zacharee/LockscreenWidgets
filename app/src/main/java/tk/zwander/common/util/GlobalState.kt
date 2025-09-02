package tk.zwander.common.util

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow

val globalState: GlobalState
    get() = GlobalState.getInstance()

class GlobalState private constructor() {
    companion object {
        private var instance: GlobalState? = null

        @Synchronized
        fun getInstance(): GlobalState {
            return instance ?: GlobalState().also { instance = it }
        }
    }

    fun onCreate(context: Context) {
        wasOnKeyguard.value = context.keyguardManager.isKeyguardLocked
        isScreenOn.value = context.powerManager.isInteractive
    }

    val wasOnKeyguard = MutableStateFlow(false)
    val isScreenOn = MutableStateFlow(false)
    val screenOrientation = MutableStateFlow(Surface.ROTATION_0)
    val isOnFaceWidgets = MutableStateFlow(false)
    val currentAppLayer = MutableStateFlow(0)
    val isOnScreenOffMemo = MutableStateFlow(false)
    val onMainLockScreen = MutableStateFlow(false)
    val showingNotificationsPanel = MutableStateFlow(false)
    val notificationCount = MutableStateFlow(0)
    val hideForPresentIds = MutableStateFlow(false)
    val hideForNonPresentIds = MutableStateFlow(false)
    val currentSysUiLayer = MutableStateFlow(-1)
    val currentSystemLayer = MutableStateFlow(0)
    val currentAppPackage = MutableStateFlow<String?>(null)
    val isOnEdgePanel = MutableStateFlow(false)
    val hidingForPresentApp = MutableStateFlow(false)
    val showingKeyboard = MutableStateFlow(false)
    val notificationsPanelFullyExpanded = MutableStateFlow(false)
    val handlingClick = MutableStateFlow(mapOf<Int, Unit>())
}
