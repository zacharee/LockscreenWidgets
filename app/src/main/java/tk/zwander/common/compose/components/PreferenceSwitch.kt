package tk.zwander.common.compose.components

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.util.prefManager

@SuppressLint("ComposableNaming")
@Composable
fun PreferenceSwitch(
    key: String,
    title: String,
    summary: String? = null,
    def: Boolean = false,
): Boolean {
    val context = LocalContext.current
    var enabled by context.rememberBooleanPreferenceState(
        key = key,
        enabled = { context.prefManager.getBoolean(key, def) },
        onEnabledChanged = { context.prefManager.putBoolean(key, it) }
    )

    CardSwitch(
        enabled = enabled,
        onEnabledChanged = { enabled = it },
        title = title,
        summary = summary
    )

    return enabled
}