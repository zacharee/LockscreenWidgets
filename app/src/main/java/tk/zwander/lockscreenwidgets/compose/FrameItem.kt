package tk.zwander.lockscreenwidgets.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.rememberScaledFrameSize
import tk.zwander.common.util.LSDisplay

@Composable
fun FrameItem(
    display: LSDisplay,
    frameId: Int,
    onSelected: (checked: Boolean?) -> Unit,
    modifier: Modifier = Modifier,
    paddingStart: Dp = 16.dp,
    checked: Boolean? = null,
) {
    val [width, height] = display.rememberScaledFrameSize(
        frameId = frameId,
    )

    Box(
        modifier = modifier.padding(start = paddingStart),
    ) {
        Card(
            onClick = {
                onSelected(checked)
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (checked == true) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Unspecified
                        },
                    )
                    .heightIn(min = 56.dp)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "$frameId",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )

                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = LocalContentColor.current,
                                shape = RoundedCornerShape(2.dp),
                            )
                            .width(width)
                            .height(height),
                    ) {
                        WidgetFramePreviewLayout(
                            modifier = Modifier,
                            frameId = frameId,
                            display = display,
                        )
                    }
                }

                if (checked != null) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = null,
                    )
                }
            }
        }
    }
}
