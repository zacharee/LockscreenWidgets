package tk.zwander.common.compose.settings

import android.graphics.drawable.Drawable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import tk.zwander.common.compose.util.rememberBooleanPreferenceState

open class SwitchPreference(
    title: String,
    summary: String,
    key: String,
    defaultValue: Boolean = false,
    icon: Drawable? = null,
) : BasePreference<Boolean>(
    title = title,
    summary = summary,
    key = key,
    defaultValue = defaultValue,
    icon = icon,
) {
    @Composable
    override fun Render(modifier: Modifier) {
        SwitchPreference(
            title = title,
            summary = summary,
            key = key,
            modifier = modifier,
            icon = rememberDrawablePainter(icon),
            defaultValue = defaultValue,
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
) {
    BasePreferenceLayout(
        title = title,
        summary = summary,
        modifier = modifier,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        widget = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}
