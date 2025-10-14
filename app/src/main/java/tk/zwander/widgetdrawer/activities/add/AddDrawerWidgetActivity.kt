package tk.zwander.widgetdrawer.activities.add

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import tk.zwander.common.activities.add.AddWidgetActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import kotlin.math.floor

/**
 * Manage selecting the widget for the drawer.
 */
class AddDrawerWidgetActivity : AddWidgetActivity() {
    companion object {
        const val EXTRA_FROM_DRAWER = "from_drawer"

        fun launch(context: Context, fromDrawer: Boolean) {
            val intent = Intent(context, AddDrawerWidgetActivity::class.java)
            intent.putExtra(EXTRA_FROM_DRAWER, fromDrawer)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }

    override val colCount: Int
        get() = prefManager.drawerColCount
    override val rowCount: Int
        get() = 1
    override val width: Float
        get() = display.pxToDp(display.realSize.x)
    override val height: Float
        get() = display.pxToDp(display.realSize.y)

    override var currentWidgets: MutableSet<WidgetData>
        get() = prefManager.drawerWidgets
        set(value) {
            prefManager.drawerWidgets = LinkedHashSet(value)
        }

    private val fromDrawer by lazy { intent.getBooleanExtra(EXTRA_FROM_DRAWER, false) }

    override fun calculateInitialWidgetRowSpan(provider: AppWidgetProviderInfo): Int {
        val rowHeight = resources.getDimensionPixelSize(R.dimen.drawer_row_height)

        return floor(provider.minHeight.toFloat() / display.pxToDp(rowHeight)).toInt()
            .coerceAtLeast(10)
            .coerceAtMost((display.realSize.y / rowHeight) - 10)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (fromDrawer) {
            eventManager.sendEvent(Event.ShowDrawer)
        }
    }
}