package tk.zwander.widgetdrawer.compose

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.updatePadding
import tk.zwander.common.compose.components.BlurView
import tk.zwander.common.compose.components.ConfirmWidgetRemovalLayout
import tk.zwander.common.compose.components.DrawerToolbar
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.Event
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.collectAsMutableState
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.statusBarHeight
import tk.zwander.widgetdrawer.util.DrawerDelegate
import tk.zwander.widgetdrawer.views.DrawerRecycler

@Composable
fun DrawerDelegate.DrawerViewModel.Drawer(
    widgetGrid: DrawerRecycler,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val backgroundColor by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_BACKGROUND_COLOR,
        value = { Color(context.prefManager.drawerBackgroundColor) },
    )

    var itemToRemove by this.itemToRemove.collectAsMutableState()
    val selectedItem by this.selectedItem.collectAsState()
    val cutoutPadding = WindowInsets.displayCutout
    val statusBarPadding = cutoutPadding.only(WindowInsetsSides.Top).takeIf {
        it.getTop(density) > 0
    } ?: WindowInsets(top = context.statusBarHeight)

    val drawerSidePadding by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_SIDE_PADDING,
        value = {
            context.prefManager.drawerSidePadding.dp
        },
    )

    val backgroundOverStatusBar by rememberBooleanPreferenceState(
        key = PrefManager.KEY_DRAWER_BACKGROUND_OVER_STATUS_BAR,
        defaultValue = true,
    )

    LaunchedEffect(selectedItem) {
        widgetGrid.selectedItem = selectedItem
    }

    LaunchedEffect(drawerSidePadding) {
        val combinedPadding = cutoutPadding.add(
            WindowInsets(left = drawerSidePadding, right = drawerSidePadding),
        )

        widgetGrid.updatePadding(
            left = combinedPadding.getLeft(density, layoutDirection),
            top = statusBarPadding.getTop(density),
            right = combinedPadding.getRight(density, layoutDirection),
            bottom = combinedPadding.getBottom(density),
        )
    }

    Box(
        modifier = modifier.motionEventSpy { event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    context.eventManager.sendEvent(Event.DrawerIntercept(true))
                }
                MotionEvent.ACTION_UP -> {
                    context.eventManager.sendEvent(Event.DrawerIntercept(false))
                }
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(
                        if (backgroundOverStatusBar) {
                            PaddingValues.Zero
                        } else {
                            statusBarPadding.asPaddingValues()
                        },
                    )
                    .background(backgroundColor)
            )

            val blurStatusBarArea by rememberBooleanPreferenceState(
                key = PrefManager.KEY_BLUR_DRAWER_STATUS_BAR_AREA,
                defaultValue = true,
            )

            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(
                        if (blurStatusBarArea) {
                            PaddingValues.Zero
                        } else {
                            statusBarPadding.asPaddingValues()
                        },
                    ),
            ) {
                BlurView(
                    blurKey = PrefManager.KEY_BLUR_DRAWER_BACKGROUND,
                    blurAmountKey = PrefManager.KEY_BLUR_DRAWER_BACKGROUND_AMOUNT,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(-1f),
                )
            }

            AndroidView(
                factory = {
                    widgetGrid.apply {
                        widgetGrid.nestedScrollingListener = {
                            itemTouchHelper.attachToRecyclerView(
                                if (it) {
                                    null
                                } else {
                                    widgetGrid
                                }
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f),
            )

            DrawerToolbar(
                addWidget = {
                    context.eventManager.sendEvent(Event.CloseDrawer)
                    context.eventManager.sendEvent(Event.LaunchAddDrawerWidget(true))
                },
                closeDrawer = {
                    context.eventManager.sendEvent(Event.CloseDrawer)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .zIndex(1f),
            )

            AnimatedVisibility(
                visible = itemToRemove != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f),
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
        }
    }
}
