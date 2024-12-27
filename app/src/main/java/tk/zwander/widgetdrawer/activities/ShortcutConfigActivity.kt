package tk.zwander.widgetdrawer.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import tk.zwander.lockscreenwidgets.R

class ShortcutConfigActivity : AppCompatActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shortcutIntent = Intent(this, TriggerActivity::class.java)
            .apply {
                action = Intent.ACTION_MAIN
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        val iconRes = Intent.ShortcutIconResource.fromContext(
            this, R.mipmap.ic_launcher,
        )

        val resultIntent = ShortcutManagerCompat.createShortcutResultIntent(
            this,
            ShortcutInfoCompat.Builder(this, "open_widget_drawer")
                .setIntent(shortcutIntent)
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setShortLabel(resources.getString(R.string.open_widget_drawer))
                .setLongLabel(resources.getString(R.string.open_widget_drawer))
                .build(),
        )
        resultIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
        resultIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, resources.getString(R.string.open_widget_drawer))

        resultIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconRes)

        setResult(Activity.RESULT_OK, resultIntent)

        finish()
    }
}