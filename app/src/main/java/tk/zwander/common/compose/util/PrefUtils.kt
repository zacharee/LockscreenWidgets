package tk.zwander.common.compose.util

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import tk.zwander.common.util.prefManager

@Composable
fun <T> rememberPreferenceState(
    key: String,
    value: (String) -> T,
    onChanged: (String, T) -> Unit
): MutableState<T> {
    val context = LocalContext.current
    val state = remember(key) {
        mutableStateOf(value(key))
    }

    LaunchedEffect(key1 = state.value) {
        onChanged(key, state.value)
    }

    DisposableEffect(key1 = key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (key == k) {
                state.value = value(key)
            }
        }

        context.prefManager.registerOnSharedPreferenceChangeListener(listener)

        onDispose {
            context.prefManager.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}

@Composable
fun rememberBooleanPreferenceState(
    key: String,
    defaultValue: Boolean = false,
    enabled: (String) -> Boolean = run {
        val context = LocalContext.current
        { context.prefManager.getBoolean(it, defaultValue) }
    },
    onEnabledChanged: (String, Boolean) -> Unit = run {
        val context = LocalContext.current
        { k, v -> context.prefManager.putBoolean(k, v) }
    },
): MutableState<Boolean> {
    return rememberPreferenceState(
        key = key,
        value = enabled,
        onChanged = onEnabledChanged
    )
}