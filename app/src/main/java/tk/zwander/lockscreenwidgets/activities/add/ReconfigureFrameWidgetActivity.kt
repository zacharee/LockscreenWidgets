package tk.zwander.lockscreenwidgets.activities.add

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import tk.zwander.common.activities.add.ReconfigureWidgetActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.util.prefManager

class ReconfigureFrameWidgetActivity : ReconfigureWidgetActivity() {
    companion object {
        fun launch(context: Context, id: Int, providerInfo: AppWidgetProviderInfo) {
            val intent = Intent(context, ReconfigureFrameWidgetActivity::class.java)

            intent.putExtra(EXTRA_PREVIOUS_ID, id)
            intent.putExtra(EXTRA_PROVIDER_INFO, providerInfo)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }

    override var currentWidgets: MutableSet<WidgetData>
        get() = prefManager.currentWidgets
        set(value) {
            prefManager.currentWidgets = LinkedHashSet(value)
        }
}