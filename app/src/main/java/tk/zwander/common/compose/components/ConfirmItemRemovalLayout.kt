package tk.zwander.common.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.data.WidgetData
import tk.zwander.lockscreenwidgets.R

@Preview
@Composable
fun ConfirmWidgetRemovalPreview() {
    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            ConfirmWidgetRemovalLayout(
                itemToRemove = null,
                onItemRemovalConfirmed = {_, _ -> },
                modifier = Modifier.height(150.dp),
            )
        }
    }
}

@Composable
fun ConfirmWidgetRemovalLayout(
    itemToRemove: WidgetData?,
    onItemRemovalConfirmed: (Boolean, WidgetData?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmItemRemovalLayout(
        itemToRemove = itemToRemove,
        text = stringResource(R.string.alert_remove_widget_confirm_desc),
        onItemRemovalConfirmed = onItemRemovalConfirmed,
        modifier = modifier,
    )
}

@Composable
fun ConfirmFrameRemovalLayout(
    itemToRemove: Int?,
    onItemRemovalConfirmed: (Boolean, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmItemRemovalLayout(
        itemToRemove = itemToRemove,
        text = stringResource(R.string.remove_frame_confirmation_message),
        onItemRemovalConfirmed = onItemRemovalConfirmed,
        modifier = modifier,
    )
}

@Composable
fun <T> ConfirmItemRemovalLayout(
    itemToRemove: T?,
    text: String,
    onItemRemovalConfirmed: (Boolean, T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(16.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            modifier = Modifier.width(IntrinsicSize.Max)
                .heightIn(max = 300.dp)
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = text,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 10.sp,
                        maxFontSize = 24.sp,
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = { onItemRemovalConfirmed(false, itemToRemove) }
                    ) {
                        Text(text = stringResource(R.string.no))
                    }

                    OutlinedButton(
                        onClick = { onItemRemovalConfirmed(true, itemToRemove) }
                    ) {
                        Text(text = stringResource(R.string.yes))
                    }
                }
            }
        }
    }
}
