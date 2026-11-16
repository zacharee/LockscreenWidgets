package tk.zwander.common.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

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
    val itemIsActive: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val proxTooClose: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val accessibilitySeesNotificationsOnMainLockScreen: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val showingPowerMenu: MutableStateFlow<Map<Int, Boolean>> = MutableStateFlow(mapOf()),
    val powerMenuNodeId: MutableStateFlow<Long> = MutableStateFlow(0),
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
        return buildString {
            appendLine("GlobalState(")
            appendLine("wasOnKeyguard=${wasOnKeyguard.value}")
            appendLine("isOnFaceWidgets=${isOnFaceWidgets.value}")
            appendLine("currentAppLayer=${currentAppLayer.value}")
            appendLine("isOnScreenOffMemo=${isOnScreenOffMemo.value}")
            appendLine("onMainLockScreen=${onMainLockScreen.value}")
            appendLine("showingNotificationsPanel=${showingNotificationsPanel.value}")
            appendLine("showingSecurityInput=${showingSecurityInput.value}")
            appendLine("notificationCount=${notificationCount.value}")
            appendLine("hideForPresentIds=${hideForPresentIds.value}")
            appendLine("hideForNonPresentIds=${hideForNonPresentIds.value}")
            appendLine("currentSysUiLayer=${currentSysUiLayer.value}")
            appendLine("currentSystemLayer=${currentSystemLayer.value}")
            appendLine("currentAppPackage=${currentAppPackage.value}")
            appendLine("isOnEdgePanel=${isOnEdgePanel.value}")
            appendLine("hidingForPresentApp=${hidingForPresentApp.value}")
            appendLine("showingKeyboard=${showingKeyboard.value}")
            appendLine("notificationsPanelFullyExpanded=${notificationsPanelFullyExpanded.value}")
            appendLine("handlingClick=${handlingClick.value}")
            appendLine("itemIsActive=${itemIsActive.value}")
            appendLine("proxTooClose=${proxTooClose.value}")
            appendLine("accessibilitySeesNotificationsOnMainLockScreen=${accessibilitySeesNotificationsOnMainLockScreen.value}")
            appendLine("showingPowerMenu=${showingPowerMenu.value}")
            appendLine("powerMenuNodeId=${powerMenuNodeId.value}")
            append(")")
        }
    }
}
