package tk.zwander.common.compose.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle

@Composable
fun CardSwitch(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: Painter? = null,
    contentDescription: String? = null,
    titleTextStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    summaryTextStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    backgroundColor: Color = Color.Transparent,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    accessory: (@Composable RowScope.(Modifier) -> Unit)? = null,
) {
    CardWithEndAccessory(
        onClick = {
            onEnabledChanged(!enabled)
        },
        title = title,
        modifier = modifier,
        summary = summary,
        icon = icon,
        contentDescription = contentDescription,
        titleTextStyle = titleTextStyle,
        summaryTextStyle = summaryTextStyle,
        backgroundColor = backgroundColor,
        contentPadding = contentPadding,
        accessory = accessory,
    ) {
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}
