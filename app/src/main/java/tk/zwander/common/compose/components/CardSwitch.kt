package tk.zwander.common.compose.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.main.SubduedOutlinedButton

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
) {
    Row(modifier = modifier) {
        SubduedOutlinedButton(
            onClick = {
                onEnabledChanged(!enabled)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .animateContentSize()
        ) {
            icon?.let {
                Image(
                    painter = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.size(8.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = titleTextStyle
                )

                if (!summary.isNullOrBlank()) {
                    Spacer(Modifier.size(4.dp))

                    Text(
                        text = summary,
                        style = summaryTextStyle
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
                colors = SwitchDefaults.colors(
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}