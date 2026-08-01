package tk.zwander.lockscreenwidgets.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.zwander.lazyspannedgrid.rememberLazySpannedGridState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.activities.SelectIconPackActivity
import tk.zwander.common.compose.WidgetGrid
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.*
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.add.ReconfigureFrameWidgetActivity
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.FrameSpecificPreferences
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

class FrameReorderActivity : BaseActivity(), CoroutineScope by App.instance {
    companion object {
        private const val NO_FRAME = -2
        private const val FRAME_ID = "frameId"

        fun start(context: Context, frameId: Int) {
            context.startActivity(
                Intent(context, FrameReorderActivity::class.java).apply {
                    putExtra(FRAME_ID, frameId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    val frameId by lazy { intent.getIntExtra(FRAME_ID, NO_FRAME).takeIf { it != NO_FRAME } }
    val delegate by lazy {
        ReorderDelegate(
            context = this,
            targetDisplayId = lsDisplayManager.availableDisplays.value.values.firstOrNull()?.uniqueIdCompat
                ?: Display.DEFAULT_DISPLAY.toString(),
            rootView = findViewById(android.R.id.content),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (frameId == null) {
            finish()
            return
        }

        setThemedContent {
            val density = LocalDensity.current

            val framePrefs = remember {
                FrameSpecificPreferences[delegate.holderId]
            }

            var currentWidgetsState by rememberPreferenceState(
                key = FramePrefs.generateCurrentWidgetsKey(delegate.holderId),
                value = { delegate.currentWidgets.toList() },
                onChanged = { _, value -> delegate.currentWidgets = value.toSet() },
            )

            Surface(
                modifier = Modifier.fillMaxSize()
                    .systemBarsPadding(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = object : Arrangement.Vertical by Arrangement.SpaceAround {
                        override val spacing: Dp = 16.dp
                    },
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .weight(0.25f),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            text = stringResource(R.string.edit_layout_desc),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .weight(0.75f),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        delegate.viewModel.WidgetGrid(
                            currentWidgets = currentWidgetsState,
                            onWidgetsChanged = {
                                currentWidgetsState = it
                            },
                            orientation = Orientation.Horizontal,
                            columnCount = delegate.colCount,
                            rowCount = delegate.rowCount,
                            resizeThresholdPx = { with(density) { 64.dp.roundToPx() } },
                            launchAddActivity = {
                                eventManager.sendEvent(Event.LaunchAddWidget(delegate.holderId))
                            },
                            launchReconfigure = { id, providerInfo ->
                                ReconfigureFrameWidgetActivity.launch(this@FrameReorderActivity, id, delegate.holderId, providerInfo)
                            },
                            launchShortcutIconOverride = { id ->
                                SelectIconPackActivity.launchForOverride(this@FrameReorderActivity, id)
                            },
                            locked = false,
                            itemSpacingKey = PrefManager.KEY_FRAME_ITEM_SPACING,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceDim)
                                .padding(vertical = 8.dp),
                            rowSpanForAddButton = 1,
                            minColSpan = 1,
                            minRowSpan = 1,
                            enableSnapping = true,
                            contentPadding = PaddingValues.Zero,
                            lazyGridState = rememberLazySpannedGridState(),
                            preferences = framePrefs.framePreferences,
                            blockIndividualWidgetTouches = true,
                        )
                    }
                }
            }
        }
    }

    inner class ReorderDelegate(
        context: Context,
        targetDisplayId: String,
        override val rootView: ViewGroup,
    ) : BaseDelegate<Unit>(context, targetDisplayId) {
        override val viewModel by lazy {
            ReorderViewModel(this)
        }
        override val state: MutableStateFlow<Unit> = MutableStateFlow(Unit)
        override val prefsHandler: HandlerRegistry = HandlerRegistry {}
        override val params: WindowManager.LayoutParams = WindowManager.LayoutParams()

        override suspend fun updateWindow() {}

        override fun onWidgetClick(trigger: Boolean): Boolean {
            return false
        }

        override var currentWidgets: Set<WidgetData>
            get() = FramePrefs.getWidgetsForFrame(this, holderId)
            set(value) {
                FramePrefs.setWidgetsForFrame(this, holderId, value)
            }
        override val holderId: Int = frameId!!
        override val colCount: Int
            get() = FrameSpecificPreferences[holderId].colCount
        override val rowCount: Int
            get() = FrameSpecificPreferences[holderId].rowCount
    }

    class ReorderViewModel(delegate: ReorderDelegate) : MainWidgetFrameDelegate.IWidgetFrameViewModel<Unit, ReorderDelegate>(delegate) {
        override val saveMode: FrameSizeAndPosition.FrameType = FrameSizeAndPosition.FrameType.Preview.Portrait
        override val ignoreWidgetTouchesKey: Pair<String, SharedPreferences>? = null
        override val doubleTapTurnOffDisplayKey: String? = null
    }
}
