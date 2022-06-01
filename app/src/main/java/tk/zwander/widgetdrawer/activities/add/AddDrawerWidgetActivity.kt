package tk.zwander.widgetdrawer.activities.add

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
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
        get() = pxAsDp(screenSize.x)
    override val height: Float
        get() = pxAsDp(screenSize.y)

    private val fromDrawer by lazy { intent.getBooleanExtra(EXTRA_FROM_DRAWER, false) }

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

    override fun createWidgetData(id: Int, provider: AppWidgetProviderInfo, overrideSize: WidgetSizeData?): WidgetData {
        return WidgetData.widget(
            id,
            provider.provider,
            provider.loadLabel(packageManager),
            provider.loadPreviewOrIcon(this, 0)?.toBitmap(512, 512)
                .toBase64(),
            overrideSize ?: run {
                val widthRatio = provider.minWidth.toFloat() / width
                val defaultColSpan = floor((widthRatio * colCount)).toInt()
                    .coerceAtMost(colCount).coerceAtLeast(1)

                val rowHeight = resources.getDimensionPixelSize(R.dimen.drawer_row_height)

                val defaultRowSpan = floor(provider.minHeight.toFloat() / pxAsDp(rowHeight)).toInt()
                    .coerceAtLeast(10)
                    .coerceAtMost((screenSize.y / rowHeight) - 10)

                WidgetSizeData(defaultColSpan, defaultRowSpan)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()

        if (fromDrawer) {
            eventManager.sendEvent(Event.ShowDrawer)
        }
    }
}