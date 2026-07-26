package tk.zwander.common.compose.settings

import android.content.SharedPreferences
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.util.prefManager

open class SwitchPreference(
    title: @Composable () -> String,
    summary: @Composable () -> String?,
    key: @Composable () -> String,
    defaultValue: @Composable () -> Boolean = { false },
    icon: @Composable () -> Painter? = { null },
    enabled: @Composable () -> Boolean = { true },
    visible: @Composable () -> Boolean = { true },
    val canChange: (Boolean) -> Boolean = { true },
    badge: (@Composable () -> Unit)? = null,
    preferences: SharedPreferences? = null,
) : BasePreference<Boolean>(
    title = title,
    summary = summary,
    key = key,
    defaultValue = defaultValue,
    icon = icon,
    enabled = enabled,
    visible = visible,
    badge = badge,
    preferences = preferences,
) {
    @Composable
    override fun Render(modifier: Modifier) {
        SwitchPreference(
            title = title(),
            summary = summary(),
            key = key(),
            modifier = modifier,
            icon = icon(),
            defaultValue = defaultValue(),
            enabled = enabled(),
            canChange = canChange,
            badge = badge,
            preferences = preferences,
        )
    }
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String?,
    key: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    defaultValue: Boolean = false,
    enabled: Boolean = true,
    canChange: (Boolean) -> Boolean = { true },
    badge: (@Composable () -> Unit)? = null,
    preferences: SharedPreferences? = null,
) {
    val context = LocalContext.current
    var value by rememberBooleanPreferenceState(
        key = key,
        defaultValue = defaultValue,
        preferences = preferences ?: context.prefManager.prefs,
    )

    SwitchPreference(
        title = title,
        summary = summary,
        checked = value,
        modifier = modifier,
        icon = icon,
        onCheckedChange = { value = it },
        enabled = enabled,
        canChange = canChange,
        badge = badge,
    )
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
    canChange: (Boolean) -> Boolean = { true },
    badge: (@Composable () -> Unit)? = null,
) {
    val onCheckedChangeProxy = remember(canChange) {
        { newValue: Boolean ->
            if (canChange(newValue)) {
                onCheckedChange(newValue)
            }
        }
    }

    BasePreferenceLayout(
        title = title,
        summary = summary,
        modifier = modifier,
        icon = icon,
        onClick = { onCheckedChangeProxy(!checked) },
        widget = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChangeProxy,
                enabled = enabled,
            )
        },
        enabled = enabled,
        badge = badge,
    )
}
