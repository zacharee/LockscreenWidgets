package tk.zwander.common.compose.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import tk.zwander.common.compose.util.rememberBooleanPreferenceValue
import tk.zwander.common.util.prefManager

/**
 * Depend on a preference from any [SharedPreferences]. Falls back to [tk.zwander.common.util.PrefManager].
 * Must supply the expected default value.
 */
fun booleanPreferenceDependency(
    key: String,
    defaultValue: Boolean,
    preferences: SharedPreferences,
): @Composable PreferenceScreenScope.() -> Boolean {
    return {
        rememberBooleanPreferenceValue(
            key = key,
            preferences = preferences,
            defaultValue = defaultValue,
        ).value
    }
}

/**
 * Automatic version of [booleanPreferenceDependency] that pulls in the [SharedPreferences]
 * and default value from the actual preference entry. Requires the preference to be
 * part of the same screen.
 */
fun booleanPreferenceDependency(
    key: String,
): @Composable PreferenceScreenScope.() -> Boolean {
    return {
        val preference = findPreference(key) as? SwitchPreference
            ?: throw IllegalArgumentException("Unable to find preference with key $key, or preference is not a SwitchPreference.")

        rememberBooleanPreferenceValue(
            key = key,
            defaultValue = preference.defaultValue(),
            preferences = preference.preferences ?: LocalContext.current.prefManager.prefs,
        ).value
    }
}
