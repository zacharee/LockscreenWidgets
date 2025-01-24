package tk.zwander.widgetdrawer.activities.add

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import tk.zwander.common.activities.add.ReconfigureWidgetActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.util.Event
import tk.zwander.common.util.createPersistablePreviewBitmap
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.pxAsDp
import tk.zwander.common.util.screenSize
import tk.zwander.lockscreenwidgets.R
import kotlin.math.floor

class ReconfigureDrawerWidgetActivity : ReconfigureWidgetActivity() {
    companion object {
        fun launch(context: Context, id: Int, providerInfo: AppWidgetProviderInfo) {
            val intent = Intent(context, ReconfigureDrawerWidgetActivity::class.java)

            intent.putExtra(EXTRA_PREVIOUS_ID, id)
            intent.putExtra(EXTRA_PROVIDER_INFO, providerInfo)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }

    override var currentWidgets: MutableSet<WidgetData>
        get() = prefManager.drawerWidgets
        set(value) {
            prefManager.drawerWidgets = LinkedHashSet(value)
        }

    override fun onDestroy() {
        super.onDestroy()

        eventManager.sendEvent(Event.ShowDrawer)
    }

    override fun createWidgetData(id: Int, provider: AppWidgetProviderInfo, overrideSize: WidgetSizeData?): WidgetData {
        return WidgetData.widget(
            this,
            id,
            provider.provider,
            provider.loadLabel(packageManager),
            provider.createPersistablePreviewBitmap(this),
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
}