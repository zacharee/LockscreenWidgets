package tk.zwander.common.compose.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.main.SubduedOutlinedButton

@Composable
fun ClickableCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String?,
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
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                )

                if (!summary.isNullOrBlank()) {
                    Spacer(Modifier.size(4.dp))

                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}