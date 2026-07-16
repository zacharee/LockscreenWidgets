package tk.zwander.common.views.remote

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.RemoteViewsAdapter
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tk.zwander.common.util.andRemoveFromParent

class LazyColumnListView(
    context: Context,
    attrs: AttributeSet,
) : ListView(context, attrs), ComposeAdapterView {
    override var remoteAdapter: RemoteViewsAdapter? = null
    override var deferNotifyDataSetChanged = false

    override val composeView = ComposeView(context)
    override val adapterState = mutableStateOf<RemoteViewsAdapter?>(null)
    override val compositionScope = mutableStateOf<CoroutineScope?>(null)

    override val scrollableState = LazyListState()

    override fun onFinishInflate() {
        super.onFinishInflate()

        setUp(this)
    }

    @Composable
    override fun Content(modifier: Modifier) {
        LaunchedEffect(null) {
            snapshotFlow { scrollableState.layoutInfo.visibleItemsInfo }
                .collect { visibleItems ->
                    remoteAdapter?.setVisibleRangeHint(
                        visibleItems.firstOrNull()?.index ?: 0,
                        visibleItems.lastOrNull()?.index ?: 0,
                    )
                }
        }

        val adjustedIndex = remember {
            { index: Int ->
                if (isStackFromBottom) {
                    remoteAdapter?.let { it.count - 1 - index } ?: 0
                } else {
                    index
                }
            }
        }

        LazyColumn(
            modifier = modifier,
            state = scrollableState,
        ) {
            adapterState.value?.let { adapter ->
                items(
                    count = adapter.count,
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
                    )
                }
            }
        }
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
