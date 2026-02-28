package tk.zwander.common.compose.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    backgroundColor: Color = Color.Transparent,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    accessory: (@Composable RowScope.() -> Unit)? = null,
) {
    SubduedOutlinedButton(
        onClick = {
            onEnabledChanged(!enabled)
        },
        modifier = modifier
            .then(
                if (modifier == Modifier) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier
                },
            )
            .defaultMinSize(minHeight = 64.dp)
            .wrapContentHeight()
            .animateContentSize(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = backgroundColor,
        ),
        contentPadding = contentPadding,
    ) {
        icon?.let {
            Image(
                painter = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.size(8.dp))
        }

        accessory?.let {
            it()
            Spacer(Modifier.size(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = titleTextStyle,
            )

            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = summaryTextStyle,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}