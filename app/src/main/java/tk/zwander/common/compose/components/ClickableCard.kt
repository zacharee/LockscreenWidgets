package tk.zwander.common.compose.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.main.SubduedOutlinedButton

@Composable
fun ClickableCard(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    endIcon: Painter? = null,
    onClick: () -> Unit,
) {
    Row(modifier = modifier) {
        SubduedOutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .animateContentSize(),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                )

                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            endIcon?.let {
                Icon(
                    painter = it,
                    contentDescription = null,
                )
            }
        }
    }
}
