package tk.zwander.common.compose.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.lockscreenwidgets.R

private data class FrameDataItem(
    @StringRes val title: Int,
    val action: suspend CoroutineScope.() -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearFrameDataCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember {
        listOf(
            FrameDataItem(
                title = R.string.on_lock_screen,
                action = {
                    context.frameSizeAndPosition.setDefaultSizeForType(
                        FrameSizeAndPosition.FrameType.LockNormal.Portrait,
                    )
                    context.frameSizeAndPosition.setDefaultSizeForType(
                        FrameSizeAndPosition.FrameType.LockNormal.Landscape,
                    )
                    context.frameSizeAndPosition.setDefaultPositionForType(
                        FrameSizeAndPosition.FrameType.LockNormal.Portrait,
                    )
                    context.frameSizeAndPosition.setDefaultPositionForType(
                        FrameSizeAndPosition.FrameType.LockNormal.Landscape,
                    )
                },
            ),
            FrameDataItem(
                title = R.string.in_notification_center,
                action = {
                    context.frameSizeAndPosition.setDefaultSizeForType(
                        FrameSizeAndPosition.FrameType.NotificationNormal.Portrait,
                    )
                    context.frameSizeAndPosition.setDefaultSizeForType(
                        FrameSizeAndPosition.FrameType.NotificationNormal.Landscape,
                    )
                    context.frameSizeAndPosition.setDefaultPositionForType(
                        FrameSizeAndPosition.FrameType.NotificationNormal.Portrait,
                    )
                    context.frameSizeAndPosition.setDefaultPositionForType(
                        FrameSizeAndPosition.FrameType.NotificationNormal.Landscape,
                    )
                },
            ),
            FrameDataItem(
                title = R.string.in_locked_notification_center,
                action = {
                    context.frameSizeAndPosition.setDefaultSizeForType(
                        FrameSizeAndPosition.FrameType.LockNotification.Portrait,
                    )
                    context.frameSizeAndPosition.setDefaultSizeForType(
                        FrameSizeAndPosition.FrameType.LockNotification.Landscape,
                    )
                    context.frameSizeAndPosition.setDefaultPositionForType(
                        FrameSizeAndPosition.FrameType.LockNotification.Portrait,
                    )
                    context.frameSizeAndPosition.setDefaultPositionForType(
                        FrameSizeAndPosition.FrameType.LockNotification.Landscape,
                    )
                },
            ),
        )
    }

    var pendingAction by remember {
        mutableStateOf<FrameDataItem?>(null)
    }

    OutlinedCard(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(
                ButtonDefaults.ContentPadding
            ),
        ) {
            Text(
                text = stringResource(id = R.string.reset_frame_position_size),
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.size(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { item ->
                    ElevatedCard(
                        onClick = {
                            pendingAction = item
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = stringResource(id = item.title))
                        }
                    }
                }
            }
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = {
                pendingAction = null
            },
            title = {
                Text(text = stringResource(id = action.title))
            },
            text = {
                Text(text = stringResource(id = R.string.reset_frame_position_size_confirm_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(block = action.action)
                        pendingAction = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(text = stringResource(id = R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingAction = null }
                ) {
                    Text(text = stringResource(id = R.string.no))
                }
            },
        )
    }
}
