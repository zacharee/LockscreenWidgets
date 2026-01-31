package tk.zwander.common.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.full.declaredMemberProperties

val globalState: GlobalState
    get() = GlobalState.getInstance()

@ConsistentCopyVisibility
data class GlobalState private constructor(
    val wasOnKeyguard: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val isOnFaceWidgets: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val currentAppLayer: MutableStateFlow<Map<Int, Int>> = MutableStateFlow(mapOf()),
    val isOnScreenOffMemo: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val onMainLockScreen: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val showingNotificationsPanel: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val showingSecurityInput: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val notificationCount: MutableStateFlow<Int> = MutableStateFlow(0),
    val hideForPresentIds: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val hideForNonPresentIds: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val currentSysUiLayer: MutableStateFlow<Map<Int, Int>> = MutableStateFlow(mapOf()),
    val currentSystemLayer: MutableStateFlow<Map<Int, Int>> = MutableStateFlow(mapOf()),
    val currentAppPackage: MutableStateFlow<Map<Int, String?>> = MutableStateFlow(mapOf()),
    val isOnEdgePanel: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val hidingForPresentApp: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val showingKeyboard: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val notificationsPanelFullyExpanded: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val handlingClick: MutableStateFlow<Map<Int, Unit>> = MutableStateFlow(mapOf()),
    val itemIsActive: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val proxTooClose: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val accessibilitySeesNotificationsOnMainLockScreen: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
) {
    companion object {
        private var instance: GlobalState? = null

        fun getInstance(): GlobalState {
            return instance ?: GlobalState().also { instance = it }
        }
    }

    fun onCreate(context: Context) {
        wasOnKeyguard.value = context.keyguardManager.isKeyguardLocked
    }

    override fun toString(): String {
        return "GlobalState(\n" +
                GlobalState::class.declaredMemberProperties
                    .joinToString("\n", postfix = "\n") { "${it.name}=${(it.get(this) as StateFlow<*>).value}" } +
                ")"
    }
}
