package tk.zwander.lockscreenwidgets.compose

import android.view.Display
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.LSDisplay
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.requireLsDisplayManager
import tk.zwander.lockscreenwidgets.R
import kotlin.math.absoluteValue

@Composable
fun SelectDisplayDialog(
    pendingFrameId: Int,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefManager = remember {
        context.prefManager
    }
    var secondaryFrames by rememberPreferenceState(
        key = PrefManager.KEY_CURRENT_FRAMES_WITH_DISPLAY,
        value = { prefManager.currentSecondaryFramesWithStringDisplay },
        onChanged = { _, v -> prefManager.currentSecondaryFramesWithStringDisplay = v },
    )

    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(text = stringResource(R.string.select_display))
        },
        text = {
            val lsDisplayManager = remember {
                context.requireLsDisplayManager
            }
            val displays by lsDisplayManager.availableDisplays.collectAsState()
            val density = LocalDensity.current

            var maxDisplayWidth by remember {
                mutableStateOf(0.dp)
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "DEFAULT_DISPLAY") {
                    Card(
                        onClick = {
                            secondaryFrames = HashMap(
                                secondaryFrames.toMutableMap().apply {
                                    this[pendingFrameId] = "${Display.DEFAULT_DISPLAY}"
                                },
                            )
                            dismiss()
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = "${stringResource(R.string.default_display)} (${Display.DEFAULT_DISPLAY})",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )

                                Text(
                                    text = stringResource(R.string.default_display_desc),
                                )
                            }
                        }
                    }
                }

                items(
                    items = displays.entries.toList(),
                    key = { it.key },
                ) { (_, display) ->
                    Card(
                        onClick = {
                            secondaryFrames = HashMap(
                                secondaryFrames.toMutableMap().apply {
                                    this[pendingFrameId] = display.uniqueIdCompat
                                },
                            )
                            dismiss()
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${display.display.name} (${descriptionForDisplay(display)})",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )

                                Spacer(modifier = Modifier.size(8.dp))

                                val (width, height) = remember(display.uniqueIdCompat) {
                                    with (density) {
                                        val screenSize = display.realSize
                                        val screenWidth = screenSize.x
                                        val screenHeight = screenSize.y

                                        val desiredHeight = 48.dp
                                        val actualHeight = screenHeight.toDp()

                                        val heightRatio = desiredHeight / actualHeight

                                        val scaledWidth = (screenWidth * heightRatio).toDp()

                                        if (scaledWidth > maxDisplayWidth) {
                                            maxDisplayWidth = scaledWidth
                                        }

                                        scaledWidth to desiredHeight
                                    }
                                }

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = if (maxDisplayWidth > 0.dp) {
                                        Modifier.width(maxDisplayWidth)
                                    } else {
                                        Modifier
                                    },
                                ) {
                                    Box(
                                        modifier = Modifier.border(
                                            width = 1.dp,
                                            color = LocalContentColor.current,
                                            shape = RoundedCornerShape(2.dp),
                                        ).width(width).height(height),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = dismiss,
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun descriptionForDisplay(display: LSDisplay): String {
    val resources = LocalResources.current
    var description by remember {
        mutableStateOf("")
    }

    LaunchedEffect(display.displayId, resources) {
        val size = display.realSize
        val width = size.x
        val height = size.y

        val widthToHeightRatio = width.toDouble() / height.toDouble()

        val likelyTallThresholdRatio = 0.5
        val likelySquareThresholdRatio = 1.0

        if (widthToHeightRatio <= likelyTallThresholdRatio) {
            description = resources.getString(R.string.tall)
            return@LaunchedEffect
        }

        if (widthToHeightRatio >= likelySquareThresholdRatio) {
            description = resources.getString(R.string.square)
            return@LaunchedEffect
        }

        val distanceToTall = (widthToHeightRatio - likelyTallThresholdRatio).absoluteValue
        val distanceToSquare = (widthToHeightRatio - likelySquareThresholdRatio).absoluteValue

        description = if (distanceToTall < distanceToSquare) {
            resources.getString(R.string.tall)
        } else {
            resources.getString(R.string.square)
        }
    }

    return description
}
