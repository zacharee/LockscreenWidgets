package tk.zwander.common.compose.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.util.prefManager

@Composable
fun rememberBooleanPreferenceDependency(key: String, preferences: SharedPreferences = LocalContext.current.prefManager.prefs): Boolean {
    val prefState by rememberUpdatedState(rememberBooleanPreferenceState(key, preferences = preferences).value)

    return prefState
}

fun booleanPreferenceDependency(key: String, preferences: SharedPreferences? = null): @Composable () -> Boolean {
    return {
        rememberBooleanPreferenceState(key, preferences = preferences ?: LocalContext.current.prefManager.prefs).value
    }
}
