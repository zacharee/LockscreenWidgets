package tk.zwander.common.compose.components

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import tk.zwander.common.compose.util.rememberBooleanPreferenceState

@SuppressLint("ComposableNaming")
@Composable
fun PreferenceSwitch(
    key: String,
    title: String,
    summary: String? = null,
    defaultValue: Boolean = false,
): Boolean {
    var enabled by rememberBooleanPreferenceState(
        key = key,
        defaultValue = defaultValue,
    )

    CardSwitch(
        enabled = enabled,
        onEnabledChanged = { enabled = it },
        title = title,
        summary = summary,
    )

    return enabled
}