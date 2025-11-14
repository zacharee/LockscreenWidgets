package tk.zwander.lockscreenwidgets.compose

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.recyclerview.widget.RecyclerView
import com.bugsnag.android.performance.compose.MeasuredComposable
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import tk.zwander.common.adapters.BaseAdapter
import tk.zwander.common.compose.components.BlurView
import tk.zwander.common.compose.components.ConfirmFrameRemovalLayout
import tk.zwander.common.compose.components.ConfirmWidgetRemovalLayout
import tk.zwander.common.compose.components.ContentColoredOutlinedButton
import tk.zwander.common.compose.components.FrameEditWrapperLayout
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.BaseDelegate
import tk.zwander.common.util.Event
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.ISnappyLayoutManager
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.collectAsMutableState
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.globalState
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.requireLsDisplayManager
import tk.zwander.common.util.themedContext
import tk.zwander.common.views.SnappyRecyclerView
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.databinding.WidgetGridHolderBinding
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

@Composable
fun WidgetFramePreviewLayout(
    displayId: String,
    frameId: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
    ) {
        val constraints = this
        val context = LocalContext.current
        val view = LocalView.current
        val density = LocalDensity.current

        val widgetGridView = remember {
            WidgetGridHolderBinding.inflate(
                LayoutInflater.from(context.themedContext),
            ).root
        }

        val frameSize = remember(frameId) {
            FrameSizeAndPosition.getInstance(context).getSizeForType(
                type = FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId),
                display = context.requireLsDisplayManager.requireDisplayByStringId(displayId),
            )
        }

        val scale by remember {
            derivedStateOf {
                with(density) {
                    constraints.maxHeight.toPx() / frameSize.x
                }
            }
        }

        val dummyDelegate = remember(frameId) {
            object : BaseDelegate<BaseDelegate.BaseState>(context.themedContext, displayId) {
                val widgetGridAdapter = WidgetFrameAdapter(
                    frameId = frameId,
                    context = context.themedContext,
                    rootView = view,
                    onRemoveCallback = { _, _ -> },
                    displayId = displayId,
                    saveTypeGetter = { FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId) },
                    viewModel = viewModel,
                )

                override val viewModel: BaseViewModel<out BaseState, out BaseDelegate<BaseState>>
                    get() = @SuppressLint("StaticFieldLeak")
                    object : BaseViewModel<BaseState, BaseDelegate<BaseState>>(this) {
                        override val containerCornerRadiusKey: String = PrefManager.KEY_FRAME_CORNER_RADIUS
                        override val widgetCornerRadiusKey: String = PrefManager.KEY_FRAME_WIDGET_CORNER_RADIUS

                    }
                override var state: BaseState = BaseState()
                override val prefsHandler: HandlerRegistry = HandlerRegistry {}
                override val adapter: BaseAdapter = widgetGridAdapter
                override val gridLayoutManager: LayoutManager = object : LayoutManager(
                    context.themedContext,
                    RecyclerView.HORIZONTAL,
                    FramePrefs.getRowCountForFrame(context, frameId),
                    FramePrefs.getColCountForFrame(context, frameId),
                ), ISnappyLayoutManager {
                    override fun canScrollHorizontally(): Boolean {
                        return false
                    }

                    override fun getPositionForVelocity(
                        velocityX: Int,
                        velocityY: Int
                    ): Int {
                        return 0
                    }

                    override fun getFixScrollPos(
                        velocityX: Int,
                        velocityY: Int
                    ): Int {
                        return 0
                    }

                    override fun canSnap(): Boolean {
                        return false
                    }
                }
                override val params: WindowManager.LayoutParams = WindowManager.LayoutParams()
                override val rootView: View = view
                override val recyclerView: RecyclerView = widgetGridView
                override var currentWidgets: List<WidgetData>
                    get() = FramePrefs.getWidgetsForFrame(this, frameId).toList()
                    set(value) {
                        FramePrefs.setWidgetsForFrame(this, frameId, value)
                    }

                override fun isLocked(): Boolean {
                    return false
                }

                override fun retrieveCounts(): Pair<Int?, Int?> {
                    return FramePrefs.getGridSizeForFrame(this, frameId)
                }

                override suspend fun updateWindow() {}

                override fun onWidgetClick(trigger: Boolean): Boolean {
                    return false
                }
            }
        }

        val framePrefs = remember(frameId) {
            FrameSpecificPreferences(frameId = frameId, context = context)
        }

        val frameCornerRadius by rememberPreferenceState(
            key = PrefManager.KEY_FRAME_CORNER_RADIUS,
            value = { context.prefManager.cornerRadiusDp.dp },
        )
        val backgroundColor by rememberPreferenceState(
            key = framePrefs.keyFor(PrefManager.KEY_FRAME_BACKGROUND_COLOR),
            value = { Color(framePrefs.backgroundColor) },
        )
        val animatedBackgroundColor by animateColorAsState(backgroundColor)

        Card(
            shape = RoundedCornerShape(size = frameCornerRadius),
            elevation = CardDefaults.outlinedCardElevation(),
            colors = CardDefaults.cardColors().copy(
                containerColor = animatedBackgroundColor,
            ),
            modifier = Modifier,
        ) {
            Box(
                modifier = Modifier,
            ) {
                val pageIndicatorBehavior by rememberPreferenceState(
                    key = PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR,
                    value = { context.prefManager.pageIndicatorBehavior },
                )

                AndroidView(
                    factory = {
                        widgetGridView
                    },
                    update = {
                        it.layoutManager = dummyDelegate.gridLayoutManager
                        it.adapter = dummyDelegate.widgetGridAdapter
                        dummyDelegate.widgetGridAdapter.updateWidgets(dummyDelegate.currentWidgets)

                        it.scaleX = scale
                        it.scaleY = scale

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
                        .graphicsLayer()
                        .requiredSize(
                            width = with(density) {
                                frameSize.x.toDp()
                            },
                            height = with(density) {
                                frameSize.y.toDp()
                            },
                        )
                        .clickable(enabled = false, onClick = {}),
                )

                Box(
                    modifier = Modifier.clickable(
                        enabled = true,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ).fillMaxSize(),
                )
            }
        }
    }
}

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

            val selectingFrame by isSelectingFrame.collectAsState()

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
