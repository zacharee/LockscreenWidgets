package tk.zwander.common.views.remote

import android.annotation.SuppressLint
import android.content.Intent
import android.content.Intent.FilterComparison
import android.view.RemotableViewMethod
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.RemoteViews
import android.widget.RemoteViewsAdapter
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tk.zwander.common.util.andRemoveFromParent

interface ComposeAdapterView : RemoteViewsAdapter.RemoteAdapterConnectionCallback {
    val composeView: ComposeView

    var remoteAdapter: RemoteViewsAdapter?
    var deferNotifyDataSetChanged: Boolean

    val adapterState: MutableState<RemoteViewsAdapter?>
    val compositionScope: MutableState<CoroutineScope?>
    val scrollableState: ScrollableState

    @Composable
    fun Content(modifier: Modifier = Modifier)

    fun setUp(target: AbsListView) {
        target.isNestedScrollingEnabled = false
        target.isScrollContainer = false
        target.adapter = DummyAdapter(composeView)

        composeView.setContent {
            val nestedScrollConnection = rememberNestedScrollInteropConnection()
            val scope = rememberCoroutineScope()

            DisposableEffect(scope) {
                compositionScope.value = scope

                onDispose {
                    compositionScope.value = null
                }
            }

            DisposableEffect(null) {
                onDispose {
                    remoteAdapter?.saveRemoteViewsCache()
                }
            }

            Content(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection))
        }
    }

    fun canScrollVertically(direction: Int): Boolean {
        return false
    }

    @RemotableViewMethod(asyncImpl = "setRemoteViewsAdapterAsync")
    fun setRemoteViewsAdapter(intent: Intent) {
        setRemoteViewsAdapter(intent, false)
    }

    fun setRemoteViewsAdapterAsync(intent: Intent): Runnable {
        return RemoteViewsAdapter.AsyncRemoteAdapterAction(this, intent)
    }

    fun setRemoteViewsInteractionHandler(handler: RemoteViews.InteractionHandler?) {
        remoteAdapter?.setRemoteViewsInteractionHandler(handler)
    }

    override fun setRemoteViewsAdapter(intent: Intent?, isAsync: Boolean) {
        // Ensure that we don't already have a RemoteViewsAdapter that is bound to an existing
        // service handling the specified intent.
        if (remoteAdapter != null) {
            val fcNew = FilterComparison(intent)
            val fcOld = FilterComparison(
                remoteAdapter?.remoteViewsServiceIntent,
            )
            if (fcNew == fcOld) {
                return
            }
        }

        deferNotifyDataSetChanged = false

        remoteAdapter = RemoteViewsAdapter(composeView.context, intent, this, isAsync)
        if (remoteAdapter?.isDataReady == true) {
            adapterState.value = remoteAdapter
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    override fun onRemoteAdapterConnected(): Boolean {
        if (remoteAdapter !== adapterState) {
            adapterState.value = remoteAdapter
            if (deferNotifyDataSetChanged) {
                remoteAdapter?.notifyDataSetChanged()
                deferNotifyDataSetChanged = false
            }
            return false
        } else if (remoteAdapter != null) {
            RemoteViewsAdapter::class.java
                .getDeclaredMethod("superNotifyDataSetChanged")
                .apply { isAccessible = true }
                .invoke(remoteAdapter)
            return true
        }
        return false
    }

    override fun onRemoteAdapterDisconnected() {}

    override fun deferNotifyDataSetChanged() {
        deferNotifyDataSetChanged = true
    }

    @RemotableViewMethod
    fun smoothScrollToPosition(position: Int)

    @RemotableViewMethod
    fun smoothScrollByOffset(offset: Int) {
        compositionScope.value?.launch {
            scrollableState.animateScrollBy(offset.toFloat())
        }
    }

    class DummyAdapter(private val composeView: ComposeView) : BaseAdapter() {
        override fun getCount(): Int {
            return 1
        }

        override fun getItem(position: Int): Any? {
            return null
        }

        override fun getItemId(position: Int): Long {
            return 0L
        }

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup?,
        ): View {
            return composeView.andRemoveFromParent()
        }
    }
}