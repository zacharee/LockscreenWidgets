package tk.zwander.lockscreenwidgets.activities.add

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import tk.zwander.common.activities.add.IFrameConfigureActivity
import tk.zwander.common.activities.add.ReconfigureWidgetActivity

class ReconfigureFrameWidgetActivity : ReconfigureWidgetActivity(), IFrameConfigureActivity {
    companion object {
        fun launch(context: Context, id: Int, frameId: Int, providerInfo: AppWidgetProviderInfo) {
            val intent = Intent(context, ReconfigureFrameWidgetActivity::class.java)

            intent.putExtra(EXTRA_PREVIOUS_ID, id)
            intent.putExtra(EXTRA_PROVIDER_INFO, providerInfo)
            intent.putExtra(EXTRA_HOLDER_ID, frameId)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }
}