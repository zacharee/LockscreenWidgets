package tk.zwander.lockscreenwidgets.compose

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.view.ViewConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.bugsnag.android.performance.compose.MeasuredComposable
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import tk.zwander.common.compose.components.BlurView
import tk.zwander.common.compose.components.ConfirmFrameRemovalLayout
import tk.zwander.common.compose.components.ConfirmWidgetRemovalLayout
import tk.zwander.common.compose.components.ContentColoredOutlinedButton
import tk.zwander.common.compose.components.FrameEditWrapperLayout
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.Event
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.collectAsMutableState
import tk.zwander.common.util.eventManager
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
    val frameCornerRadius by rememberPreferenceState(
        key = PrefManager.KEY_FRAME_CORNER_RADIUS,
        value = { context.prefManager.cornerRadiusDp.dp },
    )
    val backgroundColor by rememberPreferenceState(
        key = framePrefs.keyFor(PrefManager.KEY_FRAME_BACKGROUND_COLOR),
        value = { Color(framePrefs.backgroundColor) },
    )
    val wallpaper by wallpaperInfo.collectAsState()
    val pageIndicatorBehavior by rememberPreferenceState(
        key = PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR,
        value = { context.prefManager.pageIndicatorBehavior },
    )
    val debugIdVisibility by rememberPreferenceState(
        key = PrefManager.KEY_SHOW_DEBUG_ID_VIEW,
        value = { context.prefManager.debugLog },
    )
    val selectingFrame by isSelectingFrame.collectAsState()
    val maskedModeDimAmount by rememberPreferenceState(
        key = framePrefs.keyFor(PrefManager.KEY_MASKED_MODE_DIM_AMOUNT),
        value = { framePrefs.maskedModeDimAmount },
    )
    var itemToRemove by this.itemToRemove.collectAsMutableState()
    val firstViewing by rememberPreferenceState(
        key = PrefManager.KEY_FIRST_VIEWING,
        value = { context.prefManager.firstViewing },
    )

    var acknowledgedTwoFingerTap by this.acknowledgedTwoFingerTap.collectAsMutableState()
    @Suppress("VariableNeverRead")
    var acknowledgedThreeFingerTap by this.acknowledgedThreeFingerTap.collectAsMutableState()

    val animatedBackgroundColor by animateColorAsState(backgroundColor)

    var proxTooClose by remember {
        mutableStateOf(false)
    }
    var isInEditingMode by remember {
        mutableStateOf(false)
    }
    var removing by remember {
        mutableStateOf(false)
    }

    DisposableEffect(null) {
        val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

        val proximityListener = object : SensorEventListener2 {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

            override fun onFlushCompleted(sensor: Sensor?) {}

            override fun onSensorChanged(event: SensorEvent) {
                val dist = event.values[0]

                proxTooClose = dist < event.sensor.maximumRange
            }
        }

        sensorManager.registerListener(
            proximityListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
            1 * 200 * 1000, /* 200ms */
        )

        onDispose {
            sensorManager.unregisterListener(proximityListener)
        }
    }

    Card(
        modifier = modifier.pointerInput(null) {
            awaitPointerEventScope {
                while (true) {
                    val pointerEvent = awaitPointerEvent(pass = PointerEventPass.Initial)

                    if (pointerEvent.changes.size == 2) {
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
        },
        shape = RoundedCornerShape(size = frameCornerRadius),
        elevation = CardDefaults.outlinedCardElevation(),
        colors = CardDefaults.cardColors().copy(
            containerColor = animatedBackgroundColor,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            BlurView(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(-1f),
                blurKey = framePrefs.keyFor(PrefManager.KEY_BLUR_BACKGROUND),
                blurAmountKey = framePrefs.keyFor(PrefManager.KEY_BLUR_BACKGROUND_AMOUNT),
                cornerRadiusKey = PrefManager.KEY_FRAME_CORNER_RADIUS,
            )

            wallpaper?.let { wallpaper ->
                wallpaper.drawable?.let { drawable ->
                    Image(
                        painter = rememberDrawablePainter(drawable),
                        contentDescription = null,
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = wallpaper.dx
                                translationY = wallpaper.dy
                            }
                            .zIndex(0f),
                        colorFilter = PorterDuffColorFilter(
                            android.graphics.Color.argb(
                                ((maskedModeDimAmount / 100f) * 255).toInt(),
                                0, 0, 0,
                            ), PorterDuff.Mode.SRC_ATOP
                        ).asComposeColorFilter(),
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                modifier = Modifier.zIndex(1f),
                enter = fadeIn(),
                exit = fadeOut(),
                visible = !firstViewing,
            ) {
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
                        val items by debugIdItems.collectAsState()

                        IDListLayout(
                            items = items,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isInEditingMode,
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
                visible = proxTooClose,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(7f),
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

            androidx.compose.animation.AnimatedVisibility(
                visible = selectingFrame,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(8f),
            ) {
                MeasuredComposable(name = "SelectFrameLayoutContent") {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        contentColor = MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.surface),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(text = "$frameId")

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    ContentColoredOutlinedButton(
                                        onClick = {
                                            context.eventManager.sendEvent(
                                                Event.FrameSelected(
                                                    null,
                                                    state.selectionPreviewRequestCode
                                                )
                                            )
                                        },
                                    ) {
                                        Text(text = stringResource(R.string.cancel))
                                    }

                                    ContentColoredOutlinedButton(
                                        onClick = {
                                            context.eventManager.sendEvent(
                                                Event.FrameSelected(
                                                    frameId,
                                                    state.selectionPreviewRequestCode
                                                )
                                            )
                                        },
                                    ) {
                                        Text(text = stringResource(R.string.select))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
