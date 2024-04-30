package tk.zwander.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.CoroutineScope

@Composable
fun LifecycleEffect(
    vararg triggerOn: Lifecycle.State,
    keys: List<Any> = listOf(),
    block: suspend CoroutineScope.(Lifecycle.State) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()

    LaunchedEffect(lifecycleState, *keys.toTypedArray()) {
        if (triggerOn.contains(lifecycleState)) {
            block(lifecycleState)
        }
    }
}
