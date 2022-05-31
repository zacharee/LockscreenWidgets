package tk.zwander.widgetdrawer.activities.add

import android.appwidget.AppWidgetProviderInfo
import tk.zwander.lockscreenwidgets.activities.add.AddWidgetActivity
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.util.pxAsDp
import tk.zwander.lockscreenwidgets.util.screenSize

/**
 * Manage selecting the widget for the drawer.
 */
class AddDrawerWidgetActivity : AddWidgetActivity() {
    override val colCount: Int
        get() = prefManager.drawerColCount
    override val rowCount: Int
        get() = Int.MAX_VALUE
    override val width: Float
        get() = pxAsDp(screenSize.x)
    override val height: Float
        get() = pxAsDp(screenSize.y)

    /**
     * Add the specified widget to the drawer and save it to SharedPreferences.
     *
     * @param id the ID of the widget to be added
     */
    override fun addNewWidget(id: Int, provider: AppWidgetProviderInfo) {
        prefManager.drawerWidgets = prefManager.drawerWidgets.apply {
            add(createWidgetData(id, provider))
        }
        finish()
    }

    override fun addNewShortcut(shortcut: WidgetData) {
        prefManager.drawerWidgets = prefManager.drawerWidgets.apply {
            add(shortcut)
        }
        finish()
    }
}