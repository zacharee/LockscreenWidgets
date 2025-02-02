package tk.zwander.common.compose.settings

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import tk.zwander.common.compose.util.rememberBooleanPreferenceState

open class SwitchPreference(
    title: @Composable () -> String,
    summary: @Composable () -> String?,
    key: @Composable () -> String,
    defaultValue: @Composable () -> Boolean = { false },
    icon: @Composable () -> Painter? = { null },
    enabled: @Composable () -> Boolean = { true },
    visible: @Composable () -> Boolean = { true },
    val canChange: (Boolean) -> Boolean = { true },
) : BasePreference<Boolean>(
    title = title,
    summary = summary,
    key = key,
    defaultValue = defaultValue,
    icon = icon,
    enabled = enabled,
    visible = visible,
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
    canChange: (Boolean) -> Boolean = { true }
) {
    var value by rememberBooleanPreferenceState(
        key = key,
        defaultValue = defaultValue,
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
    canChange: (Boolean) -> Boolean = { true }
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
    )
}
