package tk.zwander.lockscreenwidgets.compose.add

import android.appwidget.AppWidgetManager
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.agrawalsuneet.dotsloader.loaders.AllianceLoader
import com.google.android.material.composethemeadapter.MdcTheme
import tk.zwander.lockscreenwidgets.data.list.BaseListInfo

@Composable
fun AddWidgetLayout(
    appWidgetManager: AppWidgetManager,
    showShortcuts: Boolean,
    onBack: () -> Unit,
    onSelected: (BaseListInfo) -> Unit,
) {
    var filter by remember {
        mutableStateOf<String?>(null)
    }

    val (items, filteredItems) = items(
        filter = filter,
        showShortcuts = showShortcuts,
        appWidgetManager = appWidgetManager
    )

    MdcTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Crossfade(
                modifier = Modifier.fillMaxSize(),
                targetState = items.isEmpty()
            ) {
                if (it) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        val dp30 = with (LocalDensity.current) {
                            30.dp.roundToPx()
                        }
                        val dp8 = with (LocalDensity.current) {
                            8.dp.roundToPx()
                        }
                        val dotColor = MaterialTheme.colors.secondary.toArgb()

                        AndroidView(
                            factory = { ctx ->
                                AllianceLoader(
                                    context = ctx,
                                    dotsRadius = dp30,
                                    distanceMultiplier = 4,
                                    drawOnlyStroke = true,
                                    strokeWidth = dp8,
                                    firsDotColor = dotColor,
                                    secondDotColor = dotColor,
                                    thirdDotColor = dotColor
                                )
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AddWidgetToolbar(
                            filter = filter,
                            onFilterChanged = { filter = it },
                            onBack = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )

                        AddWidgetScroller(
                            filteredItems = filteredItems,
                            onSelected = onSelected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}
