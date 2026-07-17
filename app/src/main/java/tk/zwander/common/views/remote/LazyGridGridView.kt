package tk.zwander.common.views.remote

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.Gravity
import android.view.RemotableViewMethod
import android.view.ViewDebug.ExportedProperty
import android.view.inspector.InspectableProperty
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.RemoteViews
import android.widget.RemoteViewsAdapter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tk.zwander.common.util.andRemoveFromParent

class LazyGridGridView(
    context: Context,
    attrs: AttributeSet,
) : GridView(context, attrs), ComposeAdapterView {
    override var remoteAdapter: RemoteViewsAdapter? = null
    override var deferNotifyDataSetChanged = false

    override val composeView = ComposeView(context)
    override val adapterState = mutableStateOf<RemoteViewsAdapter?>(null)
    override val adapterCountState = mutableIntStateOf(0)
    override val compositionScope = mutableStateOf<CoroutineScope?>(null)
    override val scrollableState = LazyGridState()

    override val listViewRef: AbsListView
        get() = this

    private val gravity = mutableIntStateOf(Gravity.START)
    private val horizontalSpacing = mutableIntStateOf(0)

    private val verticalSpacing = mutableIntStateOf(0)

    private val stretchMode = mutableIntStateOf(STRETCH_COLUMN_WIDTH)

    private val columnWidth = mutableIntStateOf(0)

    private val numColumns = mutableIntStateOf(AUTO_FIT)

    private val measuredColumnWidth = mutableIntStateOf(0)
    private val measuredHorizontalSpacing = mutableIntStateOf(0)
    private val measuredNumColumns = mutableIntStateOf(1)

    private val availableSpace = mutableIntStateOf(0)

    override fun onFinishInflate() {
        super.onFinishInflate()

        setUp(this)
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val density = LocalDensity.current

        LaunchedEffect(null) {
            snapshotFlow { scrollableState.layoutInfo.visibleItemsInfo }
                .collect { visibleItems ->
                    remoteAdapter?.setVisibleRangeHint(
                        visibleItems.firstOrNull()?.index ?: 0,
                        visibleItems.lastOrNull()?.index ?: 0,
                    )
                }
        }

        LaunchedEffect(null) {
            snapshotFlow { scrollableState.layoutInfo.maxSpan }
                .collect { maxSpan ->
                    measuredNumColumns.intValue = maxSpan
                }
        }

        val adjustedIndex = remember {
            { index: Int ->
                if (isStackFromBottom) {
                    adapterCountState.intValue - 1 - index
                } else {
                    index
                }
            }
        }

        BoxWithConstraints(
            modifier = modifier,
        ) {
            LaunchedEffect(constraints.maxWidth) {
                availableSpace.intValue = constraints.maxWidth
            }

            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = if (numColumns.intValue == AUTO_FIT) {
                    if (stretchMode.intValue == STRETCH_COLUMN_WIDTH) {
                        GridCells.Adaptive(
                            minSize = with(density) {
                                columnWidth.intValue.toDp()
                            },
                        )
                    } else {
                        GridCells.FixedSize(
                            size = with(density) {
                                columnWidth.intValue.toDp()
                            },
                        )
                    }
                } else {
                    GridCells.Fixed(count = numColumns.intValue)
                },
                verticalArrangement = Arrangement.spacedBy(
                    with(density) { verticalSpacing.intValue.toDp() },
                ),
                horizontalArrangement = if (stretchMode.intValue == STRETCH_SPACING || stretchMode.intValue == STRETCH_SPACING_UNIFORM) {
                    object : Arrangement.Horizontal by Arrangement.SpaceBetween {
                        override val spacing: Dp = with(density) { horizontalSpacing.intValue.toDp() }
                    }
                } else {
                    Arrangement.spacedBy(
                        with(density) { horizontalSpacing.intValue.toDp() },
                    )
                },
                state = scrollableState,
                overscrollEffect = null,
            ) {
                adapterState.value?.let { adapter ->
                    items(
                        count = adapterCountState.intValue,
                        key = if (adapter.hasStableIds()) {
                            {
                                adapter.getItemId(adjustedIndex(it))
                            }
                        } else {
                            null
                        },
                    ) { index ->
                        val realIndex = adjustedIndex(index)

                        AndroidView(
                            factory = { FrameLayout(it) },
                            update = {
                                it.removeAllViews()
                                it.addView(
                                    adapter.getView(
                                        realIndex,
                                        null,
                                        it,
                                    ).andRemoveFromParent(),
                                )
                            },
                            modifier = Modifier.onSizeChanged {
                                measuredColumnWidth.intValue = it.width
                                measuredHorizontalSpacing.intValue = horizontalSpacing.intValue +
                                        ((availableSpace.intValue - (numColumns.intValue * measuredColumnWidth.intValue)) /
                                                measuredNumColumns.intValue)
                            },
                        )
                    }
                }
            }
        }
    }

    @RemotableViewMethod
    override fun setGravity(gravity: Int) {
        this.gravity.intValue = gravity
    }

    @InspectableProperty(valueType = InspectableProperty.ValueType.GRAVITY)
    override fun getGravity(): Int {
        return this.gravity.intValue
    }

    @RemotableViewMethod
    override fun setHorizontalSpacing(horizontalSpacing: Int) {
        this.horizontalSpacing.intValue = horizontalSpacing
    }

    @InspectableProperty
    override fun getHorizontalSpacing(): Int {
        return this.horizontalSpacing.intValue
    }

    override fun getRequestedHorizontalSpacing(): Int {
        return this.horizontalSpacing.intValue
    }

    @RemotableViewMethod
    override fun setVerticalSpacing(verticalSpacing: Int) {
        this.verticalSpacing.intValue = verticalSpacing
    }

    @InspectableProperty
    override fun getVerticalSpacing(): Int {
        return this.verticalSpacing.intValue
    }

    @RemotableViewMethod
    override fun setStretchMode(stretchMode: Int) {
        this.stretchMode.intValue = stretchMode
    }

    @StretchMode
    @InspectableProperty(
        enumMapping = [InspectableProperty.EnumEntry(
            value = NO_STRETCH,
            name = "none"
        ), InspectableProperty.EnumEntry(
            value = STRETCH_SPACING,
            name = "spacingWidth"
        ), InspectableProperty.EnumEntry(
            value = STRETCH_SPACING_UNIFORM,
            name = "spacingWidthUniform"
        ), InspectableProperty.EnumEntry(value = STRETCH_COLUMN_WIDTH, name = "columnWidth")]
    )
    override fun getStretchMode(): Int {
        return this.stretchMode.intValue
    }

    @RemotableViewMethod
    override fun setColumnWidth(columnWidth: Int) {
        this.columnWidth.intValue = columnWidth
    }

    @InspectableProperty
    override fun getColumnWidth(): Int {
        return this.measuredColumnWidth.intValue
    }

    override fun getRequestedColumnWidth(): Int {
        return this.columnWidth.intValue
    }

    @RemotableViewMethod
    override fun setNumColumns(numColumns: Int) {
        this.numColumns.intValue = numColumns
    }

    @ExportedProperty
    @InspectableProperty
    override fun getNumColumns(): Int {
        return this.measuredNumColumns.intValue
    }

    override fun smoothScrollToPosition(position: Int) {
        compositionScope.value?.launch {
            scrollableState.animateScrollToItem(position)
        }
    }

    override fun smoothScrollByOffset(offset: Int) {
        super<ComposeAdapterView>.smoothScrollByOffset(offset)
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return super<ComposeAdapterView>.canScrollVertically(direction)
    }

    override fun computeVerticalScrollRange(): Int {
        return 0
    }

    override fun computeVerticalScrollExtent(): Int {
        return 0
    }

    override fun computeVerticalScrollOffset(): Int {
        return 0
    }

    override fun canScrollList(direction: Int): Boolean {
        return false
    }

    override fun setRemoteViewsAdapter(intent: Intent) {
        super<ComposeAdapterView>.setRemoteViewsAdapter(intent)
    }

    override fun setRemoteViewsAdapter(intent: Intent?, isAsync: Boolean) {
        super<ComposeAdapterView>.setRemoteViewsAdapter(intent, isAsync)
    }

    override fun setRemoteViewsInteractionHandler(handler: RemoteViews.InteractionHandler?) {
        super<ComposeAdapterView>.setRemoteViewsInteractionHandler(handler)
    }

    override fun setRemoteViewsAdapterAsync(intent: Intent): Runnable {
        return super<ComposeAdapterView>.setRemoteViewsAdapterAsync(intent)
    }

    override fun onRemoteAdapterConnected(): Boolean {
        return super<ComposeAdapterView>.onRemoteAdapterConnected()
    }

    override fun onRemoteAdapterDisconnected() {
        super<ComposeAdapterView>.onRemoteAdapterDisconnected()
    }

    override fun deferNotifyDataSetChanged() {
        super<ComposeAdapterView>.deferNotifyDataSetChanged()
    }
}