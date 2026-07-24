package tk.zwander.lockscreenwidgets.compose

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import tk.zwander.common.compose.WidgetGrid
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.provider.IFrameProvider
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.*
import tk.zwander.common.util.BaseDelegate.BaseState
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
        val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current

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
            PreviewDelegate(
                themedContext = context.themedContext,
                targetDisplayId = display.uniqueIdCompat,
                lifecycleOwner = lifecycleOwner,
                frameId = frameId,
                view = view,
                savedStateRegistryOwner = savedStateRegistryOwner,
            )
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

                val rowCount by rememberPreferenceState(
                    key = framePrefs.keyFor(FramePrefs.KEY_FRAME_ROW_COUNT),
                ) {
                    framePrefs.rowCount
                }
                val columnCount by rememberPreferenceState(
                    key = framePrefs.keyFor(FramePrefs.KEY_FRAME_COL_COUNT),
                ) {
                    framePrefs.colCount
                }

                var currentWidgetsState by rememberPreferenceState(
                    key = FramePrefs.generateCurrentWidgetsKey(frameId),
                    value = { framePrefs.currentWidgets.toList() },
                    onChanged = { _, value -> framePrefs.currentWidgets = value.toSet() },
                )

                Box(
                    modifier = Modifier.requiredSize(
                        width = frameSize.x.dp,
                        height = frameSize.y.dp,
                    ),
                ) {
                    dummyDelegate.viewModel.WidgetGrid(
                        currentWidgets = currentWidgetsState,
                        onWidgetsChanged = { widgets ->
                            currentWidgetsState = widgets
                        },
                        orientation = Orientation.Horizontal,
                        columnCount = columnCount,
                        rowCount = rowCount,
                        resizeThresholdPx = { 0 },
                        launchAddActivity = {},
                        launchReconfigure = { _, _ -> },
                        launchShortcutIconOverride = {},
                        modifier = Modifier.scale(scale),
                        rowSpanForAddButton = 1,
                        enableSnapping = true,
                    )
                }

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

class PreviewDelegate(
    themedContext: Context,
    targetDisplayId: String,
    view: View,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val frameId: Int,
) : BaseDelegate<BaseState>(
    context = themedContext,
    targetDisplayId = targetDisplayId,
), IFrameProvider {
    override val holderId: Int
        get() = frameId

    override val lifecycle: Lifecycle
        get() = lifecycleOwner.lifecycle

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryOwner.savedStateRegistry

    override val viewModel
        get() = PreviewViewModel()

    override var state: BaseState = BaseState()
    override val prefsHandler: HandlerRegistry = HandlerRegistry {}
    override val params: WindowManager.LayoutParams = WindowManager.LayoutParams()
    override val rootView: View = view

    override fun isLocked(): Boolean {
        return false
    }

    override suspend fun updateWindow() {}

    override fun onWidgetClick(trigger: Boolean): Boolean {
        return false
    }

    @SuppressLint("StaticFieldLeak")
    inner class PreviewViewModel : MainWidgetFrameDelegate.IWidgetFrameViewModel<
            BaseState,
            BaseDelegate<BaseState>,
            >(this) {
        override val containerCornerRadiusKey: String =
            PrefManager.KEY_FRAME_CORNER_RADIUS
        override val widgetCornerRadiusKey: String =
            PrefManager.KEY_FRAME_WIDGET_CORNER_RADIUS
        override val ignoreWidgetTouchesKey: String? = null
        override val doubleTapTurnOffDisplayKey: String? = null

        override val saveMode: FrameSizeAndPosition.FrameType
            get() = FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId)
    }
}
