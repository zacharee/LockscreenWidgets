package tk.zwander.lockscreenwidgets.activities.add

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import tk.zwander.common.activities.add.ReconfigureWidgetActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.prefManager

class WidgetStackReconfigureActivity : ReconfigureWidgetActivity() {
    override val colCount: Int = 1
    override val rowCount: Int = 1
    override val width: Float = 100f
    override val height: Float = 100f

    override var currentWidgets: Set<WidgetData>
        get() = prefManager.widgetStackWidgets[holderId] ?: LinkedHashSet()
        set(value) {
            val stacks = prefManager.widgetStackWidgets
            stacks[holderId] = LinkedHashSet(value)
            prefManager.widgetStackWidgets = stacks
        }

    companion object {
        fun launch(context: Context, stackId: Int, widgetId: Int, providerInfo: AppWidgetProviderInfo) {
            val intent = Intent(context, WidgetStackReconfigureActivity::class.java)
            intent.putExtra(EXTRA_PREVIOUS_ID, widgetId)
            intent.putExtra(EXTRA_PROVIDER_INFO, providerInfo)
            intent.putExtra(EXTRA_HOLDER_ID, stackId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }
}
