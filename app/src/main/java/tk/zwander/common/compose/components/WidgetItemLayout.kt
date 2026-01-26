package tk.zwander.common.compose.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.listeners.WidgetResizeListener.Which
import tk.zwander.common.util.BaseDelegate
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.peekLogUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import kotlin.math.absoluteValue
import kotlin.math.sign

@Composable
fun BaseDelegate.BaseViewModel<*, *>.WidgetItemLayout(
    needsReconfigure: Boolean,
    widgetData: WidgetData,
    widgetContents: @Composable (Modifier) -> Unit,
    cornerRadiusKey: String,
    ignoreTouchesKey: String?,
    doubleTapTurnOffKey: String?,
    launchIconOverride: () -> Unit,
    launchReconfigure: () -> Unit,
    remove: () -> Unit,
    getResizeThresholdPx: (Which) -> Int,
    onResize: (Boolean, Int, Int, Int, Boolean) -> Unit,
    liftCallback: () -> Unit,
    rowCount: Int,
    colCount: Int,
    isEditing: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val widgetCornerRadius by rememberPreferenceState(
        key = cornerRadiusKey,
        value = {
            (context.prefManager.getInt(
                it,
                resources.getInteger(R.integer.def_corner_radius_dp_scaled_10x),
            ) / 10f).dp
        },
    )
    val animatedCornerRadius by animateDpAsState(widgetCornerRadius)
    val ignoreTouches by ignoreTouchesKey?.let { rememberBooleanPreferenceState(ignoreTouchesKey) }
        ?: remember { mutableStateOf(false) }
    val doubleTapTurnOffDisplay by doubleTapTurnOffKey?.let { rememberBooleanPreferenceState(doubleTapTurnOffKey) }
        ?: remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        elevation = CardDefaults.outlinedCardElevation(),
        shape = RoundedCornerShape(animatedCornerRadius),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                widgetContents(Modifier.fillMaxSize())

                if (ignoreTouches) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                                onDoubleClick = if (doubleTapTurnOffDisplay) {
                                    {
                                        context.logUtils.debugLog("Sending display off action from touch ignore overlay", null)
                                        context.eventManager.sendEvent(Event.TurnOffDisplay)
                                    }
                                } else{
                                    null
                                },
                            ),
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = needsReconfigure,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .clickable(onClick = launchReconfigure),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val iconBitmap = remember {
                                widgetData.getIconBitmap(context)
                            }

                            Image(
                                bitmap = iconBitmap?.asImageBitmap() ?: ImageBitmap(1, 1),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                            )

                            Text(
                                text = widgetData.label ?: "",
                            )
                        }

                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_restore_24),
                            contentDescription = stringResource(R.string.reconfigure),
                            modifier = Modifier.fillMaxSize()
                                .background(colorResource(R.color.backdrop)),
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = isEditing,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(colorResource(R.color.backdrop))
                            .border(
                                width = 1.dp,
                                color = LocalContentColor.current,
                                shape = RoundedCornerShape(animatedCornerRadius),
                            ),
                    ) {
                        if (colCount > 1) {
                            Icon(
                                painter = painterResource(R.drawable.handle_left),
                                contentDescription = stringResource(R.string.expand_left),
                                modifier = Modifier.align(AbsoluteAlignment.CenterLeft)
                                    .dragDetection(
                                        getResizeThresholdPx = getResizeThresholdPx,
                                        which = Which.LEFT,
                                        resizeCallback = { overThreshold, step, amount ->
                                            onResize(
                                                overThreshold,
                                                step,
                                                amount,
                                                -1,
                                                false,
                                            )
                                        },
                                        liftCallback = liftCallback,
                                        this@WidgetItemLayout,
                                    ),
                            )

                            Icon(
                                painter = painterResource(R.drawable.handle_right),
                                contentDescription = stringResource(R.string.expand_right),
                                modifier = Modifier.align(AbsoluteAlignment.CenterRight)
                                    .dragDetection(
                                        getResizeThresholdPx = getResizeThresholdPx,
                                        which = Which.RIGHT,
                                        resizeCallback = { overThreshold, step, amount ->
                                            onResize(
                                                overThreshold,
                                                step,
                                                amount,
                                                1,
                                                false,
                                            )
                                        },
                                        liftCallback = liftCallback,
                                        this@WidgetItemLayout,
                                    ),
                            )
                        }

                        if (rowCount > 1) {
                            Icon(
                                painter = painterResource(R.drawable.handle_top),
                                contentDescription = stringResource(R.string.expand_up),
                                modifier = Modifier.align(Alignment.TopCenter)
                                    .dragDetection(
                                        getResizeThresholdPx = getResizeThresholdPx,
                                        which = Which.TOP,
                                        resizeCallback = { overThreshold, step, amount ->
                                            onResize(
                                                overThreshold,
                                                step,
                                                amount,
                                                -1,
                                                true,
                                            )
                                        },
                                        liftCallback = liftCallback,
                                        this@WidgetItemLayout,
                                    ),
                            )

                            Icon(
                                painter = painterResource(R.drawable.handle_bottom),
                                contentDescription = stringResource(R.string.expand_down),
                                modifier = Modifier.align(Alignment.BottomCenter)
                                    .dragDetection(
                                        getResizeThresholdPx = getResizeThresholdPx,
                                        which = Which.BOTTOM,
                                        resizeCallback = { overThreshold, step, amount ->
                                            onResize(
                                                overThreshold,
                                                step,
                                                amount,
                                                1,
                                                true,
                                            )
                                        },
                                        liftCallback = liftCallback,
                                        viewModel = this@WidgetItemLayout,
                                    ),
                            )
                        }

                        Row(
                            modifier = Modifier.align(Alignment.TopEnd)
                                .padding(8.dp),
                        ) {
                            if (widgetData.type == WidgetType.WIDGET) {
                                IconButton(
                                    onClick = launchReconfigure,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_baseline_restore_24),
                                        contentDescription = stringResource(R.string.reconfigure),
                                    )
                                }
                            }

                            if (widgetData.type == WidgetType.LAUNCHER_ITEM
                                || widgetData.type == WidgetType.SHORTCUT) {
                                IconButton(
                                    onClick = launchIconOverride,
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.image),
                                        contentDescription = stringResource(R.string.choose_icon_override),
                                    )
                                }
                            }

                            IconButton(
                                onClick = remove,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_remove_circle_24),
                                    contentDescription = stringResource(R.string.remove),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.dragDetection(
    getResizeThresholdPx: (which: Which) -> Int,
    which: Which,
    resizeCallback: (Boolean, Int, Int) -> Unit,
    liftCallback: () -> Unit,
    viewModel: BaseDelegate.BaseViewModel<*, *>,
) = composed(
    inspectorInfo = {
        name = "dragDetection"

        properties["getResizeThresholdPx"] = getResizeThresholdPx
        properties["which"] = which
        properties["resizeCallback"] = resizeCallback
        properties["liftCallback"] = liftCallback
        properties["viewModel"] = viewModel
    },
) {
    var totalDeltaX = remember { 0f }
    var totalDeltaY = remember { 0f }
    var threshold = remember { getResizeThresholdPx(which) }

    val state = rememberDraggable2DState { delta ->
        val deltaX = delta.x
        val deltaY = delta.y

        val newDx = deltaX + totalDeltaX
        val newDy = deltaY + totalDeltaY

        peekLogUtils?.debugLog("Dragging $which: dx=$deltaX, dy=$deltaY, newDx=$newDx, newDy=$newDy", null)

        if (which == Which.LEFT || which == Which.RIGHT) {
            totalDeltaX = if (newDx.absoluteValue > threshold) {
                val reportedDx = threshold * newDx.sign
                resizeCallback(true, newDx.sign.toInt(), reportedDx.toInt())
                if (newDx < 0) {
                    newDx + threshold
                } else {
                    newDx - threshold
                }
            } else {
                newDx
            }
        } else {
            totalDeltaY = if (newDy.absoluteValue > threshold) {
                val reportedDy = threshold * newDy.sign
                resizeCallback(true, newDy.sign.toInt(), reportedDy.toInt())
                if (newDy < 0) {
                    newDy + threshold
                } else {
                    newDy - threshold
                }
            } else {
                newDy
            }
        }
    }

    draggable2D(
        state = state,
        onDragStarted = {
            viewModel.isResizingItem.value = true
            totalDeltaX = 0f
            totalDeltaY = 0f
            threshold = getResizeThresholdPx(which)
        },
        onDragStopped = {
            viewModel.isResizingItem.value = false
            liftCallback()
        },
        startDragImmediately = true,
    )
}
