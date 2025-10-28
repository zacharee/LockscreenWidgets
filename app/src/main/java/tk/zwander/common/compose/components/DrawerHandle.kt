package tk.zwander.common.compose.components

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.Event
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.requireLsDisplayManager
import tk.zwander.common.util.vibrate
import tk.zwander.lockscreenwidgets.R
import kotlin.math.absoluteValue

@SuppressLint("RtlHardcoded")
@Composable
fun DrawerHandle(
    params: WindowManager.LayoutParams,
    visible: Boolean,
    displayId: Int,
    updateWindow: () -> Unit,
    onScrollStateChanged: (Boolean) -> Unit,
    fadeOutComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val currentDisplay by context.requireLsDisplayManager.collectDisplay(displayId).collectAsState(null)
    val screenWidth = remember(currentDisplay) {
        currentDisplay?.realSize?.x ?: 1
    }

    var side by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_HANDLE_SIDE,
        value = { context.prefManager.drawerHandleSide },
        onChanged = { _, value -> context.prefManager.drawerHandleSide = value },
    )
    val savedColor by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_HANDLE_COLOR,
        value = { Color(context.prefManager.drawerHandleColor) },
        onChanged = { _, value -> context.prefManager.drawerHandleColor = value.toArgb() },
    )
    val showShadow by rememberPreferenceState(
        key = PrefManager.KEY_SHOW_DRAWER_HANDLE_SHADOW,
        value = { context.prefManager.drawerHandleShadow },
    )
    val tapToOpen by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_HANDLE_TAP_TO_OPEN,
        value = { context.prefManager.drawerHandleTapToOpen },
    )
    val lockPosition by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_HANDLE_LOCK_POSITION,
        value = { context.prefManager.drawerHandleLockPosition },
    )
    val width by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_HANDLE_WIDTH,
        value = { context.prefManager.drawerHandleWidth },
    )
    val height by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_HANDLE_HEIGHT,
        value = { context.prefManager.drawerHandleHeight },
    )

    LaunchedEffect(width, height) {
        with (density) {
            params.width = width.dp.roundToPx()
            params.height = height.dp.roundToPx()
            updateWindow()
        }
    }

    var isMoving by remember {
        mutableStateOf(false)
    }
    var isScrollingOpen by remember {
        mutableStateOf(false)
    }
    var scrollTotalX = remember { 0f }

    val shownColor by animateColorAsState(
        targetValue = if (isMoving) {
            Color(120, 200, 255, 255)
        } else {
            savedColor
        },
    )

    val leftSideCornerRadius by animateDpAsState(if (side == Gravity.LEFT) 4.dp else 0.dp)
    val rightSideCornerRadius by animateDpAsState(if (side == Gravity.RIGHT) 4.dp else 0.dp)

    val handleShape = AbsoluteRoundedCornerShape(
        bottomRight = leftSideCornerRadius,
        topRight = leftSideCornerRadius,
        bottomLeft = rightSideCornerRadius,
        topLeft = rightSideCornerRadius,
    )

    val interactionSource = remember { MutableInteractionSource() }

    val updatedScrollingOpen by rememberUpdatedState(isScrollingOpen)
    val updatedIsMoving by rememberUpdatedState(isMoving)
    val updatedLockPosition by rememberUpdatedState(lockPosition)
    val updatedSide by rememberUpdatedState(side)

    val transitionState = remember { MutableTransitionState(false) }

    val animatedElevation by animateDpAsState(if (showShadow) 8.dp else 0.dp)

    LaunchedEffect(isScrollingOpen) {
        onScrollStateChanged(isScrollingOpen)
    }

    LaunchedEffect(visible) {
        transitionState.targetState = visible
    }

    AnimatedVisibility(
        visibleState = transitionState,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        DisposableEffect(null) {
            onDispose {
                isScrollingOpen = false
                scrollTotalX = 0f

                fadeOutComplete()
            }
        }

        Box(
            modifier = modifier
                .absolutePadding(
                    left = if (side == Gravity.LEFT) 0.dp else 4.dp,
                    right = if (side == Gravity.RIGHT) 0.dp else 4.dp,
                    bottom = 4.dp,
                )
                .shadow(
                    elevation = animatedElevation,
                    shape = handleShape,
                    clip = false,
                )
                .background(
                    color = shownColor,
                    shape = handleShape,
                )
                .motionEventSpy { event ->
                    when (event.action) {
                        MotionEvent.ACTION_UP -> {
                            if (updatedScrollingOpen) {
                                context.eventManager.sendEvent(
                                    Event.ScrollOpenFinish(side),
                                )
                            }

                            isScrollingOpen = false
                            isMoving = false
                            scrollTotalX = 0f
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (updatedIsMoving) {
                                val oldGravity = updatedSide
                                val gravity = when {
                                    event.rawX <= 1 / 3f * screenWidth -> {
                                        Gravity.LEFT
                                    }

                                    event.rawX >= 2 / 3f * screenWidth -> {
                                        Gravity.RIGHT
                                    }

                                    else -> -1
                                }
                                if (gravity != -1) {
                                    params.gravity = Gravity.TOP or gravity
                                    @Suppress("AssignedValueIsNeverRead")
                                    side = gravity

                                    if (oldGravity != gravity) {
                                        updateWindow()
                                    }
                                }
                            }
                        }
                    }
                }
                .draggable(
                    state = rememberDraggableState { offset ->
                        if (!updatedScrollingOpen) {
                            if (offset.absoluteValue > 20) {
                                context.vibrate(25L)
                                isScrollingOpen = true

                                context.eventManager.sendEvent(
                                    Event.ScrollInDrawer(
                                        context.prefManager.drawerHandleSide,
                                        scrollTotalX.absoluteValue,
                                        true,
                                        offset,
                                    )
                                )
                            }
                        } else {
                            scrollTotalX += offset

                            context.eventManager.sendEvent(
                                Event.ScrollInDrawer(
                                    context.prefManager.drawerHandleSide,
                                    scrollTotalX.absoluteValue,
                                    false,
                                    offset,
                                )
                            )
                        }
                    },
                    orientation = Orientation.Horizontal,
                    enabled = !isMoving,
                    interactionSource = interactionSource,
                )
                .draggable(
                    state = rememberDraggableState { offset ->
                        if (updatedIsMoving) {
                            params.y += offset.toInt()
                            updateWindow()
                            context.prefManager.drawerHandleYPosition = params.y
                        }
                    },
                    orientation = Orientation.Vertical,
                    interactionSource = interactionSource,
                )
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onLongClickLabel = stringResource(R.string.move),
                    onLongClick = {
                        if (!updatedScrollingOpen && !updatedIsMoving && !updatedLockPosition) {
                            context.vibrate()
                            @Suppress("AssignedValueIsNeverRead")
                            isMoving = true
                        }
                    },
                    onDoubleClick = if (tapToOpen) null else {
                        {
                            context.vibrate(25L)
                            context.eventManager.sendEvent(Event.ShowDrawer)
                        }
                    },
                    onClick = {
                        if (tapToOpen) {
                            context.vibrate(25L)
                            context.eventManager.sendEvent(Event.ShowDrawer)
                        }
                    },
                    enabled = !isMoving,
                ),
        )
    }
}
