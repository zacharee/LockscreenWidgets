package tk.zwander.common.compose.util

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tk.zwander.common.util.prefManager

@Composable
fun <T> rememberPreferenceState(
    key: String,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    value: (String) -> T,
): State<T> {
    return rememberPreferenceState(
        key = key,
        value = value,
        preferences = preferences,
        onChanged = { _, _ -> },
    )
}

@Composable
fun <T> rememberPreferenceState(
    key: String,
    value: (String) -> T,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    onChanged: suspend (String, T) -> Unit,
): MutableState<T> {
    val state = remember(key) {
        mutableStateOf(value(key))
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(key1 = state.value) {
        launch(Dispatchers.IO) {
            onChanged(key, state.value)
        }
    }

    DisposableEffect(key1 = key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (key == k) {
                scope.launch(Dispatchers.IO) {
                    state.value = value(key)
                }
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(listener)

        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return state
}

@Composable
fun rememberBooleanPreferenceState(
    key: String,
    defaultValue: Boolean = false,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    enabled: (String) -> Boolean = run {
        { preferences.getBoolean(it, defaultValue) }
    },
    onEnabledChanged: (String, Boolean) -> Unit = run {
        { k, v ->
            preferences.edit {
                putBoolean(k, v)
            }
        }
    },
): MutableState<Boolean> {
    return rememberPreferenceState(
        key = key,
        value = enabled,
        onChanged = onEnabledChanged,
        preferences = preferences,
    )
}
