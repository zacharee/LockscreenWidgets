package tk.zwander.common.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

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

fun <K, V> MutableStateFlow<Map<K, V>>.update(block: MutableMap<K, V>.() -> Unit) {
    this.value = this.value.toMutableMap().apply(block)
}

fun <K, V> MutableStateFlow<Map<K, V>>.remove(key: K): V? {
    val newMap = this.value.toMutableMap()
    val result = newMap.remove(key)

    this.value = newMap

    return result
}

operator fun <K, V> MutableStateFlow<Map<K, V>>.set(key: K, value: V) {
    update {
        this[key] = value
    }
}
