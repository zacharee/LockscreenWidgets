package tk.zwander.widgetdrawer.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tk.zwander.lockscreenwidgets.R
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager

class TriggerActivity : AppCompatActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            val shortcutIntent = Intent(this, TriggerActivity::class.java)

            val resultIntent = Intent()
            resultIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            resultIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, resources.getString(R.string.open_widget_drawer))

            val iconRes = Intent.ShortcutIconResource.fromContext(
                this, R.mipmap.ic_launcher
            )

            resultIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconRes)

            setResult(Activity.RESULT_OK, resultIntent)
        } else {
            eventManager.sendEvent(Event.ShowDrawer)
        }

        finish()
    }
}
