package tk.zwander.common.compose.main

import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import tk.zwander.common.compose.components.MainContentDivider
import tk.zwander.common.compose.util.insetsContentPadding
import tk.zwander.common.util.LifecycleEffect
import tk.zwander.common.util.appWidgetManager
import tk.zwander.lockscreenwidgets.appwidget.WidgetStackProvider
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate
import tk.zwander.widgetdrawer.util.DrawerDelegate

@Preview
@Composable
fun MainContent() {
    val context = LocalContext.current
    val features = rememberFeatureCards()
    val links = rememberLinks()

    val gridState = rememberLazyStaggeredGridState()

    val hasFrameDelegateInstance =
        MainWidgetFrameDelegate.readOnlyInstance.collectAsState().value != null
    val hasDrawerDelegateInstance = DrawerDelegate.readOnlyInstance.collectAsState().value != null

    var widgetStackIds by remember {
        mutableStateOf<List<Int>>(listOf())
    }

    LaunchedEffect(hasFrameDelegateInstance, hasDrawerDelegateInstance) {
        if (!hasFrameDelegateInstance || !hasDrawerDelegateInstance) {
            gridState.animateScrollToItem(0)
        }
    }

    LifecycleEffect(Lifecycle.State.RESUMED) {
        widgetStackIds = context.appWidgetManager.getAppWidgetIds(
            ComponentName(context, WidgetStackProvider::class.java),
        ).toList()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(400.dp),
            contentPadding = insetsContentPadding(
                WindowInsets.systemBars,
                WindowInsets.ime,
                extraPadding = PaddingValues(16.dp),
            ),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            state = gridState,
        ) {
            if (!hasFrameDelegateInstance || !hasDrawerDelegateInstance) {
                item(key = "AccessibilityCard") {
                    AccessibilityCard(
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            items(features.size, key = { features[it].title }) {
                FeatureCard(
                    info = features[it],
                    modifier = Modifier.animateItem(),
                )
            }

            if (widgetStackIds.isNotEmpty()) {
                item(span = StaggeredGridItemSpan.FullLine, key = "WidgetStackDivider") {
                    MainContentDivider(
                        modifier = Modifier.animateItem(),
                    )
                }

                item(key = "WidgetStacksCard") {
                    WidgetStacksCard(modifier = Modifier.animateItem())
                }
            }

            item(span = StaggeredGridItemSpan.FullLine, key = "DebugDivider") {
                MainContentDivider(
                    modifier = Modifier.animateItem(),
                )
            }

            item(key = "DebugCard") {
                DebugCard(
                    modifier = Modifier.animateItem(),
                )
            }

            item(span = StaggeredGridItemSpan.FullLine, key = "LinkDivider") {
                MainContentDivider(
                    modifier = Modifier.animateItem(),
                )
            }

            items(links.size, key = { links[it].title }) {
                LinkItem(
                    option = links[it],
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}