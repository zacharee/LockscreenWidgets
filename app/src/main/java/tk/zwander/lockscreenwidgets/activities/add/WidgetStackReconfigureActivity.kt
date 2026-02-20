package tk.zwander.lockscreenwidgets.activities.add

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import tk.zwander.common.activities.add.ReconfigureWidgetActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.prefManager

class WidgetStackReconfigureActivity : ReconfigureWidgetActivity() {
    private val widgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    }

    override val colCount: Int = 1
    override val rowCount: Int = 1

    override var currentWidgets: MutableSet<WidgetData>
        get() = prefManager.widgetStackWidgets[widgetId] ?: LinkedHashSet()
        set(value) {
            val newWidgets = prefManager.widgetStackWidgets
            newWidgets[widgetId] = LinkedHashSet(value)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (widgetId == -1) {
            finish()
        }

        super.onCreate(savedInstanceState)
    }

    companion object {
        fun launch(context: Context, widgetId: Int, providerInfo: AppWidgetProviderInfo) {
            val intent = Intent(context, WidgetStackReconfigureActivity::class.java)
            intent.putExtra(EXTRA_PREVIOUS_ID, widgetId)
            intent.putExtra(EXTRA_PROVIDER_INFO, providerInfo)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }
}
