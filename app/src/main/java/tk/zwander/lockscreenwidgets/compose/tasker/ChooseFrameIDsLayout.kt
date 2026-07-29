package tk.zwander.lockscreenwidgets.compose.tasker

import android.view.Display
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tk.zwander.common.util.lsDisplayManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.compose.FrameItem
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

@Composable
fun ChooseFrameIDsLayout(
    initialSelectedIds: List<Int>,
    onSave: (List<Int>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val display = remember {
        { frameId: Int ->
            val id = context.prefManager.currentSecondaryFramesWithStringDisplay[frameId] ?: Display.DEFAULT_DISPLAY.toString()
            context.lsDisplayManager.findDisplayByStringId(id)
                ?: context.lsDisplayManager.availableDisplays.value.values.first()
        }
    }

    var currentSelections by remember {
        mutableStateOf(initialSelectedIds)
    }

    Surface(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.select_frames)) },
                    navigationIcon = {
                        IconButton(
                            onClick = onCancel,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close_24px),
                                contentDescription = stringResource(R.string.cancel),
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                onSave(currentSelections)
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_save_24),
                                contentDescription = stringResource(R.string.apply),
                            )
                        }
                    },
                )
            },
            content = { paddingValues ->
                val frameIds = remember {
                    MainWidgetFrameDelegate.allIds(context).sorted()
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues + PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = frameIds, key = { it }) { frameId ->
                        val display = remember(frameId) {
                            display(frameId)
                        }

                        FrameItem(
                            display = display,
                            onSelected = {
                                currentSelections = if (currentSelections.contains(frameId)) {
                                    currentSelections.toMutableList().apply {
                                        remove(frameId)
                                    }
                                } else {
                                    currentSelections + frameId
                                }
                            },
                            frameId = frameId,
                            paddingStart = 0.dp,
                            modifier = Modifier.fillMaxWidth(),
                            checked = currentSelections.contains(frameId),
                        )
                    }
                }
            },
            bottomBar = {
                BottomAppBar(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = stringResource(R.string.select_frames_tasker_instruction),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            },
        )
    }
}
