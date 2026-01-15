package tk.zwander.lockscreenwidgets.compose

import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.gestures.rememberDraggable2DState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.bugsnag.android.performance.compose.MeasuredComposable
import tk.zwander.common.compose.components.BlurView
import tk.zwander.common.compose.components.ConfirmFrameRemovalLayout
import tk.zwander.common.compose.components.ConfirmWidgetRemovalLayout
import tk.zwander.common.compose.components.FrameEditWrapperLayout
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.Event
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.collectAsMutableState
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.globalState
import tk.zwander.common.util.prefManager
import tk.zwander.common.views.SnappyRecyclerView
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

@Composable
fun MainWidgetFrameDelegate.WidgetFrameViewModel.WidgetFrameLayout(
    widgetGrid: SnappyRecyclerView,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val frameCornerRadius by rememberPreferenceState(
        key = PrefManager.KEY_FRAME_CORNER_RADIUS,
        value = { context.prefManager.cornerRadiusDp.dp },
    )
    val backgroundColor by rememberPreferenceState(
        key = framePrefs.keyFor(PrefManager.KEY_FRAME_BACKGROUND_COLOR),
        value = { Color(framePrefs.backgroundColor) },
    )
    val firstViewing by rememberPreferenceState(
        key = PrefManager.KEY_FIRST_VIEWING,
        value = { context.prefManager.firstViewing },
    )

    var acknowledgedTwoFingerTap by this.acknowledgedTwoFingerTap.collectAsMutableState()
    @Suppress("VariableNeverRead")
    var acknowledgedThreeFingerTap by this.acknowledgedThreeFingerTap.collectAsMutableState()
    var itemToRemove by this.itemToRemove.collectAsMutableState()

    val animatedBackgroundColor by animateColorAsState(backgroundColor)

    val proxTooClose by globalState.proxTooClose.collectAsState()
    var isInEditingMode by remember {
        mutableStateOf(false)
    }
    var removing by remember {
        mutableStateOf(false)
    }
    var isAdjustingMask by this.isAdjustingMask.collectAsMutableState()

    var maskAdjustment by rememberPreferenceState(
        key = PrefManager.KEY_MASKED_MODE_ADJUSTMENT_FOR_DISPLAY,
        value = {
            context.prefManager.maskedModeAdjustment[lsDisplay?.uniqueIdCompat] ?: Offset(0f, 0f)
        },
        onChanged = { _, value ->
            val mutatedValue = HashMap(context.prefManager.maskedModeAdjustment.toMutableMap())
            mutatedValue[lsDisplay?.uniqueIdCompat] = value
            context.prefManager.maskedModeAdjustment = mutatedValue
        },
    )
    var maskScale by rememberPreferenceState(
        key = PrefManager.KEY_MASKED_MODE_SCALE_FOR_DISPLAY,
        value = {
            context.prefManager.maskedModeScaleForDisplay[lsDisplay?.uniqueIdCompat] ?: 1.0f
        },
        onChanged = { _, value ->
            val mutatedValue = HashMap(context.prefManager.maskedModeScaleForDisplay.toMutableMap())
            mutatedValue[lsDisplay?.uniqueIdCompat] = value
            context.prefManager.maskedModeScaleForDisplay = mutatedValue
        },
    )

    Card(
        modifier = modifier
            .pointerInput(null) {
                awaitPointerEventScope {
                    while (true) {
                        val pointerEvent = awaitPointerEvent(pass = PointerEventPass.Initial)

                        if (pointerEvent.changes.size == 2 && !isAdjustingMask) {
                            pointerEvent.changes.forEach { it.consume() }

                            val thirdEvent = withTimeoutOrNull(10L) {
                                awaitPointerEvent(pass = PointerEventPass.Initial)
                            }

                            if (thirdEvent == null || thirdEvent.changes.size <= 2) {
                                isInEditingMode = !isInEditingMode && !isLocked
                                if (acknowledgedTwoFingerTap == null) {
                                    acknowledgedTwoFingerTap = false
                                } else if (acknowledgedTwoFingerTap == false) {
                                    acknowledgedTwoFingerTap = true
                                }
                            } else {
                                if (firstViewing) {
                                    @Suppress("AssignedValueIsNeverRead")
                                    acknowledgedThreeFingerTap = true
                                } else {
                                    context.eventManager.sendEvent(Event.TempHide(frameId = frameId))
                                }
                            }

                            thirdEvent?.changes?.forEach { it.consume() }
                            waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        }
                    }
                }
            }
            .motionEventSpy { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        context.eventManager.sendEvent(Event.FrameIntercept(frameId, true))
                    }

                    MotionEvent.ACTION_UP -> {
                        context.eventManager.sendEvent(Event.FrameIntercept(frameId, false))
                    }
                }
            },
        shape = RoundedCornerShape(size = frameCornerRadius),
        elevation = CardDefaults.outlinedCardElevation(),
        colors = CardDefaults.cardColors().copy(
            containerColor = animatedBackgroundColor,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            val wallpaper by wallpaperInfo.collectAsState()

            BlurView(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(-1f),
                blurKey = remember {
                    framePrefs.keyFor(PrefManager.KEY_BLUR_BACKGROUND)
                },
                blurAmountKey = remember {
                    framePrefs.keyFor(PrefManager.KEY_BLUR_BACKGROUND_AMOUNT)
                },
                cornerRadiusKey = PrefManager.KEY_FRAME_CORNER_RADIUS,
            )

            wallpaper?.let { wallpaper ->
                wallpaper.drawable?.let { drawable ->
                    val maskedModeDimAmount by rememberPreferenceState(
                        key = framePrefs.keyFor(PrefManager.KEY_MASKED_MODE_DIM_AMOUNT),
                        value = { framePrefs.maskedModeDimAmount },
                    )

                    AndroidView(
                        factory = {
                            AppCompatImageView(it).apply {
                                scaleType = ImageView.ScaleType.MATRIX
                            }
                        },
                        update = {
                            it.setImageDrawable(drawable)
                            it.imageMatrix = Matrix().apply {
                                setScale(maskScale, maskScale)
                                postTranslate(
                                    wallpaper.dx + with(density) { maskAdjustment.x.dp.toPx() },
                                    wallpaper.dy + with(density) { maskAdjustment.y.dp.toPx() },
                                )
                            }
                            it.colorFilter = PorterDuffColorFilter(
                                android.graphics.Color.argb(
                                    ((maskedModeDimAmount / 100f) * 255).toInt(),
                                    0, 0, 0,
                                ),
                                PorterDuff.Mode.SRC_ATOP,
                            )
                        },
                        modifier = Modifier.zIndex(0f),
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier.zIndex(1f),
                enter = fadeIn(),
                exit = fadeOut(),
                visible = !firstViewing,
            ) {
                val pageIndicatorBehavior by rememberPreferenceState(
                    key = PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR,
                    value = { context.prefManager.pageIndicatorBehavior },
                )

                AndroidView(
                    factory = { widgetGrid },
                    update = {
                        it.isHorizontalScrollBarEnabled =
                            pageIndicatorBehavior != PrefManager.VALUE_PAGE_INDICATOR_BEHAVIOR_HIDDEN
                        it.isScrollbarFadingEnabled =
                            pageIndicatorBehavior == PrefManager.VALUE_PAGE_INDICATOR_BEHAVIOR_AUTO_HIDE
                        it.scrollBarFadeDuration =
                            if (pageIndicatorBehavior == PrefManager.VALUE_PAGE_INDICATOR_BEHAVIOR_AUTO_HIDE) {
                                ViewConfiguration.getScrollBarFadeDuration()
                            } else {
                                0
                            }
                    },
                    modifier = Modifier
                        .fillMaxSize(),
                )
            }

            HintIntroLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f),
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = itemToRemove != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(3f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ConfirmWidgetRemovalLayout(
                        itemToRemove = itemToRemove,
                        onItemRemovalConfirmed = { removed, data ->
                            context.eventManager.sendEvent(
                                Event.RemoveWidgetConfirmed(
                                    removed,
                                    data
                                )
                            )
                            itemToRemove = null
                        },
                    )
                }
            }

            val debugIdVisibility by rememberPreferenceState(
                key = PrefManager.KEY_SHOW_DEBUG_ID_VIEW,
                value = { context.prefManager.showDebugIdView },
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = debugIdVisibility,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(4f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    MeasuredComposable(name = "IDList") {
                        IDListLayout(
                            modifier = Modifier.fillMaxSize(),
                            displayId = lsDisplay?.displayId,
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isInEditingMode && !isAdjustingMask,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(5f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    FrameEditWrapperLayout(
                        frameId = frameId,
                        onRemovePressed = {
                            removing = true
                        },
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = removing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(6f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ConfirmFrameRemovalLayout(
                        itemToRemove = frameId,
                        onItemRemovalConfirmed = { removed, data ->
                            context.eventManager.sendEvent(
                                Event.RemoveFrameConfirmed(
                                    removed,
                                    data
                                )
                            )
                            removing = false
                        },
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isAdjustingMask,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(7f),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .draggable2D(
                            state = rememberDraggable2DState { offset ->
                                with(density) {
                                    maskAdjustment = Offset(
                                        maskAdjustment.x + offset.x.toDp().value,
                                        maskAdjustment.y + offset.y.toDp().value,
                                    )
                                }
                            },
                        )
                        .transformable(
                            state = rememberTransformableState { zoomChange, panChange, _ ->
                                with(density) {
                                    maskAdjustment = Offset(
                                        maskAdjustment.x + panChange.x.toDp().value,
                                        maskAdjustment.y + panChange.y.toDp().value,
                                    )
                                }
                                maskScale *= zoomChange
                            }
                        ),
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        IconButton(
                            onClick = {
                                maskScale = 1f
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.recenter_24px),
                                contentDescription = stringResource(R.string.reset_mask_alignment),
                                modifier = Modifier.size(32.dp),
                                tint = Color.White,
                            )
                        }

                        IconButton(
                            onClick = {
                                maskScale = 1f
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_1x_mobiledata_24),
                                contentDescription = stringResource(R.string.reset_mask_scale),
                                modifier = Modifier.size(32.dp),
                                tint = Color.White,
                            )
                        }

                        IconButton(
                            onClick = {
                                isAdjustingMask = false
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.baseline_check_24),
                                contentDescription = stringResource(R.string.done),
                                modifier = Modifier.size(32.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = proxTooClose,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(8f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorResource(R.color.backdrop))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.touch_protection_active),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                    )
                }
            }
        }
    }
}
