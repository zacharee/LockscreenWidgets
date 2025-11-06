package tk.zwander.common.compose.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.Event
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R

@Composable
fun FrameEditWrapperLayout(
    frameId: Int,
    onRemovePressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cornerRadius by rememberPreferenceState(
        key = PrefManager.KEY_FRAME_CORNER_RADIUS,
        value = { context.prefManager.cornerRadiusDp },
        onChanged = { _, value -> context.prefManager.cornerRadiusDp = value },
        initialValue = 2f,
    )

    Surface(
        modifier = modifier.border(
            width = 1.dp,
            color = Color.White,
            shape = RoundedCornerShape(cornerRadius.dp),
        ),
        color = colorResource(R.color.backdrop),
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
                            context.eventManager.sendEvent(Event.CenterFrameVertically(frameId))
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
                        modifier = Modifier.draggable2D(
                            state = rememberDraggable2DState {
                                context.eventManager.sendEvent(Event.FrameMoved(frameId, it.x, it.y))
                            },
                            onDragStopped = {
                                context.eventManager.sendEvent(Event.FrameMoveFinished(frameId))
                            },
                        ).size(32.dp),
                        tint = Color.White,
                    )

                    IconButton(
                        onClick = {
                            context.eventManager.sendEvent(Event.CenterFrameHorizontally(frameId))
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_vertical_align_center_24),
                            contentDescription = stringResource(R.string.center_horizontally),
                            modifier = Modifier.rotate(90f).size(32.dp),
                            tint = Color.White,
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    if (frameId != -1) {
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
                            context.eventManager.sendEvent(Event.TempHide(frameId))
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
                            context.eventManager.sendEvent(Event.LaunchAddWidget(frameId))
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_add_24),
                            contentDescription = stringResource(R.string.add_widget),
                            modifier = Modifier.rotate(90f).size(32.dp),
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
                    modifier = Modifier.align(AbsoluteAlignment.CenterLeft)
                        .draggable(
                            state = rememberDraggableState {
                                context.eventManager.sendEvent(Event.FrameResized(frameId, Event.FrameResized.Side.LEFT, it.toInt(), false))
                            },
                            orientation = Orientation.Horizontal,
                            onDragStopped = {
                                context.eventManager.sendEvent(Event.FrameResized(frameId, Event.FrameResized.Side.LEFT, it.toInt(), true))
                            },
                        ),
                    tint = Color.White,
                )

                Icon(
                    painter = painterResource(R.drawable.handle_top),
                    contentDescription = stringResource(R.string.expand_up),
                    modifier = Modifier.align(Alignment.TopCenter)
                        .draggable(
                            state = rememberDraggableState {
                                context.eventManager.sendEvent(Event.FrameResized(frameId, Event.FrameResized.Side.TOP, it.toInt(), false))
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                context.eventManager.sendEvent(Event.FrameResized(frameId, Event.FrameResized.Side.TOP, it.toInt(), true))
                            },
                        ),
                    tint = Color.White,
                )

                Icon(
                    painter = painterResource(R.drawable.handle_bottom),
                    contentDescription = stringResource(R.string.expand_down),
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .draggable(
                            state = rememberDraggableState {
                                context.eventManager.sendEvent(Event.FrameResized(frameId, Event.FrameResized.Side.BOTTOM, it.toInt(), false))
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                context.eventManager.sendEvent(Event.FrameResized(frameId, Event.FrameResized.Side.BOTTOM, it.toInt(), true))
                            },
                        ),
                    tint = Color.White,
                )

                Icon(
                    painter = painterResource(R.drawable.handle_right),
                    contentDescription = stringResource(R.string.expand_right),
                    modifier = Modifier.align(AbsoluteAlignment.CenterRight)
                        .draggable(
                            state = rememberDraggableState {
                                context.eventManager.sendEvent(Event.FrameResized(frameId, Event.FrameResized.Side.RIGHT, it.toInt(), false))
                            },
                            orientation = Orientation.Horizontal,
                            onDragStopped = {
                                context.eventManager.sendEvent(Event.FrameResized(frameId, Event.FrameResized.Side.RIGHT, it.toInt(), true))
                            },
                        ),
                    tint = Color.White,
                )
            }
        }
    }
}
