package tk.zwander.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.FrameReorderActivity
import tk.zwander.lockscreenwidgets.activities.add.AddFrameWidgetActivity
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate

@Composable
fun WidgetFrameDelegate.WidgetFrameViewModel.FrameEditWrapperLayout(
    frameId: Int,
    onRemovePressed: () -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    frameMoveFinished: () -> Unit,
    frameMoved: (velX: Float, velY: Float) -> Unit,
    frameResized: (which: Side, velocity: Int, isUp: Boolean) -> Unit,
    centerFrameHorizontally: () -> Unit,
    centerFrameVertically: () -> Unit,
    tempHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cornerRadius by rememberPreferenceState(
        key = PrefManager.KEY_FRAME_CORNER_RADIUS,
        value = { context.prefManager.cornerRadiusDp },
        onChanged = { _, value -> context.prefManager.cornerRadiusDp = value },
    )
    val maskedModeEnabled by rememberBooleanPreferenceState(
        key = PrefManager.KEY_FRAME_MASKED_MODE,
        preferences = FrameSpecificPreferences[frameId].framePreferences,
    )

    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = Color.White,
                shape = RoundedCornerShape(cornerRadius.dp),
            )
            .background(
                color = colorResource(R.color.backdrop),
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    IconButton(
                        onClick = {
                            centerFrameVertically()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_vertical_align_center_24),
                            contentDescription = stringResource(R.string.center_vertically),
                            modifier = Modifier.size(32.dp),
                            tint = Color.White,
                        )
                    }

                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_move_24),
                        contentDescription = stringResource(R.string.move),
                        modifier = Modifier
                            .draggable2D(
                                state = rememberDraggable2DState {
                                    frameMoved(it.x, it.y)
                                },
                                onDragStopped = {
                                    frameMoveFinished()
                                },
                            )
                            .size(32.dp),
                        tint = Color.White,
                    )

                    IconButton(
                        onClick = {
                            centerFrameHorizontally()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_vertical_align_center_24),
                            contentDescription = stringResource(R.string.center_horizontally),
                            modifier = Modifier
                                .rotate(90f)
                                .size(32.dp),
                            tint = Color.White,
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    if (frameId != WidgetFrameDelegate.ID) {
                        IconButton(
                            onClick = onRemovePressed,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_remove_circle_24),
                                contentDescription = stringResource(R.string.remove_frame),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            FrameReorderActivity.start(context, frameId)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.edit_24px),
                            contentDescription = stringResource(R.string.edit_layout),
                            modifier = Modifier.size(32.dp),
                            tint = Color.White,
                        )
                    }

                    if (maskedModeEnabled) {
                        IconButton(
                            onClick = {
                                isAdjustingMask.value = true
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.join_right_24px),
                                contentDescription = stringResource(R.string.adjust_wallpaper_mask),
                                modifier = Modifier.size(32.dp),
                                tint = Color.White,
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            tempHide()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_visibility_off_24),
                            contentDescription = stringResource(R.string.hide),
                            modifier = Modifier.size(32.dp),
                            tint = Color.White,
                        )
                    }

                    IconButton(
                        onClick = {
                            AddFrameWidgetActivity.launch(context, frameId)
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_add_24),
                            contentDescription = stringResource(R.string.add_widget),
                            modifier = Modifier
                                .rotate(90f)
                                .size(32.dp),
                            tint = Color.White,
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                ) {
                    IconButton(
                        onClick = onClose,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close_24px),
                            contentDescription = stringResource(R.string.done),
                            modifier = Modifier
                                .rotate(90f)
                                .size(32.dp),
                            tint = Color.White,
                        )
                    }

                    IconButton(
                        onClick = onSettings,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_settings_24),
                            contentDescription = stringResource(R.string.settings),
                            modifier = Modifier
                                .rotate(90f)
                                .size(32.dp),
                            tint = Color.White,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.handle_left),
                    contentDescription = stringResource(R.string.expand_left),
                    modifier = Modifier
                        .align(AbsoluteAlignment.CenterLeft)
                        .draggable(
                            state = rememberDraggableState {
                                frameResized(Side.LEFT, it.toInt(), false)
                            },
                            orientation = Orientation.Horizontal,
                            onDragStopped = {
                                frameResized(Side.LEFT, 0, true)
                            },
                        ),
                    tint = Color.White,
                )

                Icon(
                    painter = painterResource(R.drawable.handle_top),
                    contentDescription = stringResource(R.string.expand_up),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .draggable(
                            state = rememberDraggableState {
                                frameResized(Side.TOP, it.toInt(), false)
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                frameResized(Side.TOP, 0, true)
                            },
                        ),
                    tint = Color.White,
                )

                Icon(
                    painter = painterResource(R.drawable.handle_bottom),
                    contentDescription = stringResource(R.string.expand_down),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .draggable(
                            state = rememberDraggableState {
                                frameResized(Side.BOTTOM, it.toInt(), false)
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                frameResized(Side.BOTTOM, 0, true)
                            },
                        ),
                    tint = Color.White,
                )

                Icon(
                    painter = painterResource(R.drawable.handle_right),
                    contentDescription = stringResource(R.string.expand_right),
                    modifier = Modifier
                        .align(AbsoluteAlignment.CenterRight)
                        .draggable(
                            state = rememberDraggableState {
                                frameResized(Side.RIGHT, it.toInt(), false)
                            },
                            orientation = Orientation.Horizontal,
                            onDragStopped = {
                                frameResized(Side.RIGHT, 0, true)
                            },
                        ),
                    tint = Color.White,
                )
            }
        }
    }
}

enum class Side {
    LEFT,
    TOP,
    RIGHT,
    BOTTOM
}
