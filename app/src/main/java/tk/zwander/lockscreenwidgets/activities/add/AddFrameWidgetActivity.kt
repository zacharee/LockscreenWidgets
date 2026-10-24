package tk.zwander.lockscreenwidgets.activities.add

import android.content.Context
import android.content.Intent
import tk.zwander.common.activities.add.AddWidgetActivity
import tk.zwander.common.activities.add.IFrameConfigureActivity

class AddFrameWidgetActivity : AddWidgetActivity(), IFrameConfigureActivity {
    companion object {
        fun launch(context: Context, frameId: Int) {
            val intent = Intent(context, AddFrameWidgetActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(EXTRA_HOLDER_ID, frameId)

            context.startActivity(intent)
        }
    }
}