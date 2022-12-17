package tk.zwander.common.compose.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import tk.zwander.common.util.prefManager

@Composable
fun Context.rememberBooleanPreferenceState(
    key: String,
    enabled: () -> Boolean,
    onEnabledChanged: (Boolean) -> Unit
): MutableState<Boolean> {
    val enabledState = remember {
        mutableStateOf(enabled())
    }

    DisposableEffect(key1 = enabledState.value) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (key == k) {
                enabledState.value = enabled()
            }
        }

        onEnabledChanged(enabledState.value)
        prefManager.registerOnSharedPreferenceChangeListener(listener)

        onDispose {
            prefManager.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return enabledState
}