package tk.zwander.common.compose.util

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager

@Composable
fun <T> rememberPreferenceState(
    key: String,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    resetKey: Any? = Unit,
    onChanged: suspend (String, T) -> Unit = { _, _ -> },
    value: (String) -> T,
): MutableState<T> {
    val context = LocalContext.current

    val updatedOnChanged by rememberUpdatedState(onChanged)
    val updatedValue by rememberUpdatedState(value)

    val state = remember(key, preferences, resetKey) {
        mutableStateOf(updatedValue(key))
    }

    LaunchedEffect(state) {
        snapshotFlow { state.value }.collect {
            if (it != updatedValue(key)) {
                try {
                    updatedOnChanged(key, it)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // one failed write shouldn't kill the pipeline
                    context.logUtils.debugLog("Unable to write state pref $key $it", e)
                }
            }
        }
    }

    DisposableEffect(state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (key == k) {
                state.value = updatedValue(key)
            }
        }

        preferences.registerOnSharedPreferenceChangeListener(listener)
        state.value = updatedValue(key)

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
    enabled: (String) -> Boolean = { preferences.getBoolean(it, defaultValue) },
    onEnabledChanged: (String, Boolean) -> Unit = { k, v ->
        preferences.edit {
            putBoolean(k, v)
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

@Composable
fun rememberBooleanPreferenceValue(
    key: String,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    defaultValue: Boolean = false,
): State<Boolean> {
    return rememberPreferenceState(
        key = key,
        preferences = preferences,
        value = {
            preferences.getBoolean(key, defaultValue)
        },
    )
}

@Composable
fun rememberIntPreferenceState(
    key: String,
    defaultValue: Int,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    value: (String) -> Int = {
        preferences.getInt(key, defaultValue)
    },
    onChanged: (String, Int) -> Unit = { k, v ->
        preferences.edit { putInt(k, v) }
    },
): MutableState<Int> {
    return rememberPreferenceState(
        key = key,
        preferences = preferences,
        onChanged = onChanged,
        value = value,
    )
}

@Composable
fun rememberLongPreferenceState(
    key: String,
    defaultValue: Long,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    value: (String) -> Long = {
        preferences.getLong(key, defaultValue)
    },
    onChanged: (String, Long) -> Unit = { k, v ->
        preferences.edit { putLong(k, v) }
    },
): MutableState<Long> {
    return rememberPreferenceState(
        key = key,
        preferences = preferences,
        onChanged = onChanged,
        value = value,
    )
}

@Composable
fun rememberFloatPreferenceState(
    key: String,
    defaultValue: Float,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    value: (String) -> Float = {
        preferences.getFloat(key, defaultValue)
    },
    onChanged: (String, Float) -> Unit = { k, v ->
        preferences.edit { putFloat(k, v) }
    },
): MutableState<Float> {
    return rememberPreferenceState(
        key = key,
        preferences = preferences,
        onChanged = onChanged,
        value = value,
    )
}

@Composable
fun rememberStringPreferenceState(
    key: String,
    defaultValue: String?,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    value: (String) -> String? = {
        preferences.getString(key, defaultValue)
    },
    onChanged: (String, String?) -> Unit = { k, v ->
        preferences.edit { putString(k, v) }
    },
): MutableState<String?> {
    return rememberPreferenceState(
        key = key,
        preferences = preferences,
        onChanged = onChanged,
        value = value,
    )
}

@Composable
fun rememberStringSetPreferenceState(
    key: String,
    defaultValue: Set<String>?,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    value: (String) -> Set<String>? = {
        preferences.getStringSet(key, defaultValue)
    },
    onChanged: (String, Set<String>?) -> Unit = { k, v ->
        preferences.edit { putStringSet(k, v) }
    },
): MutableState<Set<String>?> {
    return rememberPreferenceState(
        key = key,
        preferences = preferences,
        onChanged = onChanged,
        value = value,
    )
}
