package tk.zwander.widgetdrawer.activities.add

import android.appwidget.AppWidgetProviderInfo
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.add.AddWidgetActivity
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetSizeData
import tk.zwander.lockscreenwidgets.util.*
import kotlin.math.floor

/**
 * Manage selecting the widget for the drawer.
 */
class AddDrawerWidgetActivity : AddWidgetActivity() {
    override val colCount: Int
        get() = prefManager.drawerColCount
    override val rowCount: Int
        get() = 1
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
        eventManager.sendEvent(Event.ShowDrawer)
        finish()
    }

    override fun addNewShortcut(shortcut: WidgetData) {
        prefManager.drawerWidgets = prefManager.drawerWidgets.apply {
            add(shortcut)
        }
        eventManager.sendEvent(Event.ShowDrawer)
        finish()
    }

    override fun createWidgetData(id: Int, provider: AppWidgetProviderInfo): WidgetData {
        return WidgetData.widget(
            id,
            provider.provider,
            provider.loadLabel(packageManager),
            provider.loadPreviewOrIcon(this, 0)?.toBitmap(512, 512)
                .toBase64(),
            run {
                val widthRatio = provider.minWidth.toFloat() / width
                val defaultColSpan = floor((widthRatio * colCount)).toInt()
                    .coerceAtMost(colCount).coerceAtLeast(1)

                val defaultRowSpan = floor(provider.minHeight.toFloat() / pxAsDp(resources.getDimensionPixelSize(R.dimen.drawer_row_height))).toInt()
                    .coerceAtLeast(5)

                WidgetSizeData(defaultColSpan, defaultRowSpan)
            }
        )
    }
}