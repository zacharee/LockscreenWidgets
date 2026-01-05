package tk.zwander.lockscreenwidgets.compose

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.WidgetData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.BaseDelegate
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.ISnappyLayoutManager
import tk.zwander.common.util.LSDisplay
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.themedContext
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.databinding.WidgetGridHolderBinding
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

@Composable
fun WidgetFramePreviewLayout(
    display: LSDisplay,
    frameId: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
    ) {
        val constraints = this
        val context = LocalContext.current
        val view = LocalView.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val widgetGridView = remember {
            WidgetGridHolderBinding.inflate(
                LayoutInflater.from(context.themedContext),
            ).root
        }

        val frameSize = remember(frameId) {
            FrameSizeAndPosition.getInstance(context).getSizeForType(
                type = FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId),
                display = display,
            )
        }

        val scale by remember {
            derivedStateOf {
                constraints.maxHeight / frameSize.x.dp
            }
        }

        val dummyDelegate = remember(frameId) {
            object : BaseDelegate<BaseDelegate.BaseState>(
                context.themedContext,
                display.uniqueIdCompat
            ) {
                override val lifecycle: Lifecycle
                    get() = lifecycleOwner.lifecycle
                val widgetGridAdapter = WidgetFrameAdapter(
                    context = context.themedContext,
                    viewModel = viewModel,
                )

                override val viewModel
                    get() = @SuppressLint("StaticFieldLeak")
                    object : MainWidgetFrameDelegate.IWidgetFrameViewModel<BaseState, BaseDelegate<BaseState>>(this) {
                        override val containerCornerRadiusKey: String =
                            PrefManager.KEY_FRAME_CORNER_RADIUS
                        override val widgetCornerRadiusKey: String =
                            PrefManager.KEY_FRAME_WIDGET_CORNER_RADIUS
                        override val ignoreWidgetTouchesKey: String? = null

                        override val frameId: Int
                            get() = frameId
                        override val saveMode: FrameSizeAndPosition.FrameType
                            get() = FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId)
                    }
                override var state: BaseState = BaseState()
                override val prefsHandler: HandlerRegistry = HandlerRegistry {}
                override val adapter = widgetGridAdapter
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
                        velocityY: Int,
                    ): Int {
                        return 0
                    }

                    override fun getFixScrollPos(
                        velocityX: Int,
                        velocityY: Int,
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

                init {
                    gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup
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

        DisposableEffect(frameId) {
            val listener = "$frameId-preview-layout"

            context.widgetHostCompat.startListening(listener)

            onDispose {
                context.widgetHostCompat.stopListening(listener)
            }
        }

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
                    factory = { widgetGridView },
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
                    modifier = Modifier.fillMaxSize()
                        .requiredSize(
                            width = frameSize.x.dp,
                            height = frameSize.y.dp,
                        ),
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