package tk.zwander.lockscreenwidgets.activities.add

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.util.prefManager

class ReconfigureWidgetActivity : BaseBindWidgetActivity() {
    companion object {
        const val EXTRA_PREVIOUS_ID = "previous_id"
        const val EXTRA_PROVIDER_INFO = "provider_info"

        fun launch(context: Context, id: Int, providerInfo: AppWidgetProviderInfo) {
            val intent = Intent(context, ReconfigureWidgetActivity::class.java)

            intent.putExtra(EXTRA_PREVIOUS_ID, id)
            intent.putExtra(EXTRA_PROVIDER_INFO, providerInfo)

            context.startActivity(intent)
        }
    }

    private val prevId by lazy { intent.getIntExtra(EXTRA_PREVIOUS_ID, -1) }
    private val providerInfo by lazy { intent.getParcelableExtra(EXTRA_PROVIDER_INFO) as AppWidgetProviderInfo? }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (prevId == -1 || providerInfo == null) {
            finish()
            return
        }

        tryBindWidget(providerInfo!!, prevId)
    }

    @SuppressLint("NewApi")
    override fun tryBindWidget(info: AppWidgetProviderInfo, id: Int) {
        var goodId = id

        if (widgetHost.appWidgetIds.contains(id)
            && appWidgetManager.getAppWidgetInfo(id).provider != info.provider) {
            goodId = widgetHost.allocateAppWidgetId()
        }

        super.tryBindWidget(info, goodId)
    }

    override fun addNewWidget(id: Int, provider: ComponentName) {
        val widget = WidgetData.widget(id, provider)
        val newSet = prefManager.currentWidgets.toMutableList()

        val oldWidgetIndex = newSet.indexOf(WidgetData.widget(prevId, provider))

        newSet.removeAt(oldWidgetIndex)
        newSet.add(oldWidgetIndex, widget)

        prefManager.currentWidgets = LinkedHashSet(newSet)

        finish()
    }
}