package tk.zwander.common.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.zwander.lazyspannedgrid.rememberLazySpannedGridState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.platacard.pagerindicator.PagerWormIndicator
import tk.zwander.common.activities.SelectIconPackActivity
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.listeners.WidgetResizeListener
import tk.zwander.common.util.Event
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.orDefault
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.add.ReconfigureFrameWidgetActivity
import tk.zwander.lockscreenwidgets.util.FramePrefs
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MainWidgetFrameDelegate.WidgetFrameViewModel.FrameWidgetGridWrapper(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefManager = remember {
        context.prefManager
    }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val constraints = constraints
        val gridState = rememberLazySpannedGridState()
        val rowCount by rememberPreferenceState(
            key = PrefManager.KEY_FRAME_ROW_COUNT,
            preferences = framePrefs.framePreferences,
        ) {
            framePrefs.rowCount
        }
        val columnCount by rememberPreferenceState(
            key = PrefManager.KEY_FRAME_COL_COUNT,
            preferences = framePrefs.framePreferences,
        ) {
            framePrefs.colCount
        }
        var currentWidgetsState by rememberPreferenceState(
            key = FramePrefs.generateCurrentWidgetsKey(holderId),
            value = { currentWidgets.toList() },
            onChanged = { _, value -> currentWidgets = value.toSet() },
        )
        val controlBarVisible by rememberPreferenceState(
            key = PrefManager.KEY_FRAME_SHOW_BOTTOM_BAR,
            value = { framePrefs.showControlBar },
            preferences = framePrefs.framePreferences,
        )

        val rememberFramePosition by rememberPreferenceState(
            key = PrefManager.KEY_FRAME_REMEMBER_POSITION,
        ) {
            prefManager.rememberFramePosition
        }
        var storedPosition by rememberPreferenceState(
            key = PrefManager.KEY_CURRENT_PAGE,
            value = { framePrefs.currentIndex },
            onChanged = { _, value ->
                framePrefs.currentIndex = value
            },
            preferences = framePrefs.framePreferences,
        )
        val derivedStoredPosition by remember {
            derivedStateOf { storedPosition }
        }
        val pageIndicatorBehavior by rememberPreferenceState(
            key = PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR,
            value = { context.prefManager.pageIndicatorBehavior },
        )
        var showingPager by remember(pageIndicatorBehavior) {
            mutableStateOf(
                pageIndicatorBehavior != PrefManager.VALUE_PAGE_INDICATOR_BEHAVIOR_HIDDEN,
            )
        }
        val pageInfo by remember(pageIndicatorBehavior) {
            derivedStateOf {
                val pageSizePx = gridState.lineSizePx * gridState.mainAxisLineCount.coerceAtLeast(1)
                val scrollOffsetPx =
                    gridState.firstVisibleLine * gridState.lineSizePx + gridState.firstVisibleLineScrollOffset
                val pageOffset =
                    (scrollOffsetPx % pageSizePx.coerceAtLeast(1)) / constraints.maxWidth.toFloat()
                        .coerceAtLeast(1f)
                val page = (scrollOffsetPx / pageSizePx.coerceAtLeast(1))

                val pageCount = gridState.layoutInfo.totalLineCount / columnCount

                (page + pageOffset) to pageCount
            }
        }
        val frameLocked by rememberBooleanPreferenceState(
            key = PrefManager.KEY_LOCK_WIDGET_FRAME,
        )
        val state by state.collectAsState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(pageInfo) {
            showingPager = pageIndicatorBehavior != PrefManager.VALUE_PAGE_INDICATOR_BEHAVIOR_HIDDEN
        }

        LaunchedEffect(showingPager, pageInfo) {
            if (showingPager && pageIndicatorBehavior == PrefManager.VALUE_PAGE_INDICATOR_BEHAVIOR_AUTO_HIDE) {
                delay(2000.milliseconds)
                showingPager = false
            }
        }

        LaunchedEffect(null) {
            if (rememberFramePosition && gridState.layoutInfo.totalLineCount > 1) {
                gridState.scrollToLine(derivedStoredPosition)
            }
        }

        LaunchedEffect(gridState.firstVisibleLine) {
            val newLine = gridState.firstVisibleLine
            if (newLine != derivedStoredPosition && !gridState.isScrollInProgress) {
                storedPosition = newLine
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            WidgetGrid(
                currentWidgets = currentWidgetsState,
                onWidgetsChanged = { widgets ->
                    currentWidgetsState = widgets
                },
                orientation = Orientation.Horizontal,
                columnCount = columnCount,
                rowCount = rowCount,
                resizeThresholdPx = remember(context) {
                    { which ->
                        val display = display.orDefault(context)
                        val frameSize = context.frameSizeAndPosition.getSizeForType(saveMode, display)
                        if (which == WidgetResizeListener.Which.LEFT || which == WidgetResizeListener.Which.RIGHT) {
                            display.dpToPx(frameSize.x.toInt()) / colCount
                        } else {
                            display.dpToPx(frameSize.y.toInt()) / rowCount
                        }
                    }
                },
                launchAddActivity = {
                    context.eventManager.sendEvent(Event.LaunchAddWidget(holderId))
                },
                launchReconfigure = { id, providerInfo ->
                    updateState {
                        it.copy(isPreview = false)
                    }
                    ReconfigureFrameWidgetActivity.launch(context, id, holderId, providerInfo)
                },
                launchShortcutIconOverride = { id ->
                    SelectIconPackActivity.launchForOverride(context, id)
                },
                modifier = Modifier.fillMaxWidth()
                    .weight(1f),
                rowSpanForAddButton = 1,
                enableSnapping = true,
                lazyGridState = gridState,
                locked = frameLocked && !state.isPreview,
                itemSpacingKey = PrefManager.KEY_FRAME_ITEM_SPACING,
                preferences = framePrefs.framePreferences,
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = controlBarVisible,
                modifier = Modifier.fillMaxWidth(),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = {
                                isEditing.value = true
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_settings_24),
                                contentDescription = stringResource(R.string.settings),
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))
                    }

                    PagerIndicator(
                        showingPager = pageInfo.second > 1,
                        pageFraction = pageInfo.first,
                        pageCount = pageInfo.second,
                        onPageClick = {
                            scope.launch {
                                gridState.animateScrollToLine(
                                    (it * columnCount)
                                        .coerceAtLeast(0)
                                        .coerceAtMost((pageInfo.second * columnCount) - 1),
                                )
                            }
                        },
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = {
                                scope.launch {
                                    gridState.animateScrollToLine(
                                        (gridState.firstVisibleLine + (pageInfo.second * columnCount) - columnCount)
                                                % (pageInfo.second * columnCount),
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_left_24px),
                                contentDescription = stringResource(R.string.previous),
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    gridState.animateScrollToLine(
                                        (gridState.firstVisibleLine + columnCount) % (pageInfo.second * columnCount),
                                    )
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_left_24px),
                                contentDescription = stringResource(R.string.next),
                                modifier = Modifier.rotate(180f)
                                    .size(24.dp),
                            )
                        }
                    }
                }
            }
        }

        PagerIndicator(
            showingPager = showingPager && !controlBarVisible,
            pageFraction = pageInfo.first,
            pageCount = pageInfo.second,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            onPageClick = {
                scope.launch {
                    gridState.animateScrollToLine(
                        (it * columnCount)
                            .coerceAtLeast(0)
                            .coerceAtMost(pageInfo.second - 1),
                    )
                }
            },
        )
    }
}

@Composable
private fun PagerIndicator(
    showingPager: Boolean,
    pageFraction: Float,
    pageCount: Int,
    onPageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val updatedPageFraction by rememberUpdatedState(pageFraction)

    AnimatedVisibility(
        visible = showingPager && pageCount > 1,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier,
    ) {
        PagerWormIndicator(
            pageCount = pageCount,
            currentPageFraction = remember {
                derivedStateOf {
                    updatedPageFraction
                }
            },
            activeDotColor = LocalContentColor.current,
            dotColor = LocalContentColor.current.copy(alpha = 0.5f),
            onDotClick = onPageClick,
            dotCount = pageCount,
        )
    }
}
