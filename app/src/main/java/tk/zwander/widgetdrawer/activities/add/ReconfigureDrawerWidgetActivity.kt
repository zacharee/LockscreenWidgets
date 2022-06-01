package tk.zwander.widgetdrawer.activities.add

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.add.ReconfigureWidgetActivity
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetSizeData
import tk.zwander.lockscreenwidgets.util.*
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

    override var currentWidgets: Collection<WidgetData>
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
}