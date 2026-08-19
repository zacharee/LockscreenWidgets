package tk.zwander.common.compose.util

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.*
import tk.zwander.common.util.prefManager

fun <T> Context.preferenceAsState(
    key: String,
    scope: CoroutineScope,
    preferences: SharedPreferences = prefManager.prefs,
    onChanged: suspend (String, T) -> Unit = { _, _ -> },
    value: (String) -> T,
): MutableState<T> {
    val state = mutableStateOf(value(key))
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (key == k) {
            scope.launch(Dispatchers.IO) {
                state.value = value(key)
            }
        }
    }

    scope.launch(Dispatchers.IO) {
        snapshotFlow { state.value }.collect {
            if (it != value(key)) {
                onChanged(key, it)
            }
        }
    }

    scope.launch {
        preferences.registerOnSharedPreferenceChangeListener(listener)

        try {
            awaitCancellation()
        } catch (_: CancellationException) {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return object : MutableState<T> {
        override var value: T
            get() = derivedStateOf { state.value }.value
            set(value) {
                state.value = value
            }

        override fun component1(): T {
            return this.value
        }

        override fun component2(): (T) -> Unit {
            return {
                this.value = it
            }
        }
    }
}

@Composable
fun <T> rememberPreferenceState(
    key: String,
    preferences: SharedPreferences = LocalContext.current.prefManager.prefs,
    onChanged: suspend (String, T) -> Unit = { _, _ -> },
    value: (String) -> T,
): MutableState<T> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val state by remember {
        derivedStateOf {
            context.preferenceAsState(
                key = key,
                preferences = preferences,
                onChanged = onChanged,
                value = value,
                scope = scope,
            )
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
