package tk.zwander.common.util

import android.content.Context
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.full.declaredMemberProperties

val globalState: GlobalState
    get() = GlobalState.getInstance()

@ConsistentCopyVisibility
data class GlobalState private constructor(
    val wasOnKeyguard: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val isScreenOn: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val screenOrientation: MutableStateFlow<Int> = MutableStateFlow(Surface.ROTATION_0),
    val isOnFaceWidgets: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val currentAppLayer: MutableStateFlow<Int> = MutableStateFlow(0),
    val isOnScreenOffMemo: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val onMainLockScreen: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val showingNotificationsPanel: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val notificationCount: MutableStateFlow<Int> = MutableStateFlow(0),
    val hideForPresentIds: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val hideForNonPresentIds: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val currentSysUiLayer: MutableStateFlow<Int> = MutableStateFlow(-1),
    val currentSystemLayer: MutableStateFlow<Int> = MutableStateFlow(0),
    val currentAppPackage: MutableStateFlow<String?> = MutableStateFlow(null),
    val isOnEdgePanel: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val hidingForPresentApp: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val showingKeyboard: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val notificationsPanelFullyExpanded: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val handlingClick: MutableStateFlow<Map<Int, Unit>> = MutableStateFlow(mapOf()),
) {
    companion object {
        private var instance: GlobalState? = null

        fun getInstance(): GlobalState {
            return instance ?: GlobalState().also { instance = it }
        }
    }

    fun onCreate(context: Context) {
        wasOnKeyguard.value = context.keyguardManager.isKeyguardLocked
        isScreenOn.value = context.powerManager.isInteractive
    }

    override fun toString(): String {
        return "GlobalState(\n" +
                GlobalState::class.declaredMemberProperties
                    .joinToString("\n", postfix = "\n") { "${it.name}=${it.get(this)}" } +
                ")"
    }
}
