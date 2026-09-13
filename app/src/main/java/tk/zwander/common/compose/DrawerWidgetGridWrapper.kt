package tk.zwander.common.compose

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import tk.zwander.common.activities.SelectIconPackActivity
import tk.zwander.common.compose.util.preferenceAsState
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.listeners.WidgetResizeListener
import tk.zwander.common.util.Event
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.orDefault
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.widgetdrawer.activities.add.ReconfigureDrawerWidgetActivity
import tk.zwander.widgetdrawer.util.DrawerDelegate

@Composable
fun DrawerDelegate.DrawerViewModel.DrawerWidgetGridWrapper(
    previousNonZeroCutout: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val prefManager = remember(context) { context.prefManager }
    val rowCount = remember(display) {
        display.orDefault(context).rotatedRealSize.y / resources.getDimensionPixelSize(R.dimen.drawer_row_height)
    }
    val columnCount by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_COL_COUNT,
    ) {
        prefManager.drawerColCount
    }
    val drawerLocked by rememberBooleanPreferenceState(
        key = PrefManager.KEY_LOCK_WIDGET_DRAWER,
    )
    val cutoutPadding = remember(previousNonZeroCutout) {
        WindowInsets(top = previousNonZeroCutout)
    }
    var currentWidgetsState by context.preferenceAsState(
        key = PrefManager.KEY_DRAWER_WIDGETS,
        value = { currentWidgets.toList() },
        onChanged = { _, value -> currentWidgets = value.toSet() },
        scope = scope,
    )

    val drawerSidePadding by rememberPreferenceState(
        key = PrefManager.KEY_DRAWER_SIDE_PADDING,
        value = {
            context.prefManager.drawerSidePadding.dp
        },
    )
    val combinedPadding = cutoutPadding.add(
        WindowInsets(left = drawerSidePadding, right = drawerSidePadding),
    )

    WidgetGrid(
        currentWidgets = currentWidgetsState,
        onWidgetsChanged = { widgets ->
            currentWidgetsState = widgets
        },
        orientation = Orientation.Vertical,
        columnCount = columnCount,
        rowCount = rowCount,
        resizeThresholdPx = { which ->
            if (which == WidgetResizeListener.Which.LEFT || which == WidgetResizeListener.Which.RIGHT) {
                display.orDefault(context).rotatedRealSize.x / colCount
            } else {
                resources.getDimensionPixelSize(R.dimen.drawer_row_height)
            }
        },
        launchAddActivity = {
            context.eventManager.sendEvent(Event.CloseDrawer)
            context.eventManager.sendEvent(Event.LaunchAddDrawerWidget(true))
        },
        launchReconfigure = { id, providerInfo ->
            context.eventManager.sendEvent(Event.CloseDrawer)
            ReconfigureDrawerWidgetActivity.launch(context, id, providerInfo)
        },
        launchShortcutIconOverride = { id ->
            context.eventManager.sendEvent(Event.CloseDrawer)
            SelectIconPackActivity.launchForOverride(context, id, true)
        },
        modifier = modifier,
        rowSpanForAddButton = 20,
        enableSnapping = false,
        contentPadding = combinedPadding.asPaddingValues(),
        minRowSpan = 5,
        locked = drawerLocked,
        itemSpacingKey = PrefManager.KEY_DRAWER_ITEM_SPACING,
    )
}