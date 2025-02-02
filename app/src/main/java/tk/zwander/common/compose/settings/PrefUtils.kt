package tk.zwander.common.compose.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import tk.zwander.common.compose.util.rememberBooleanPreferenceState

@Composable
fun rememberBooleanPreferenceDependency(key: String): Boolean {
    val prefState by rememberUpdatedState(rememberBooleanPreferenceState(key).value)

    return prefState
}

fun booleanPreferenceDependency(key: String): @Composable () -> Boolean {
    return {
        rememberBooleanPreferenceState(key).value
    }
}
