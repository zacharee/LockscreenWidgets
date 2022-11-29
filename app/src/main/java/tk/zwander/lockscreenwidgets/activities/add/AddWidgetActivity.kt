package tk.zwander.lockscreenwidgets.activities.add

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tk.zwander.common.compose.add.AddWidgetLayout
import tk.zwander.common.util.appWidgetManager
import tk.zwander.lockscreenwidgets.activities.DismissOrUnlockActivity
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.data.list.WidgetListInfo

/**
 * Manage the widget addition flow: selection, permissions, configurations, etc.
 */
open class AddWidgetActivity : BaseBindWidgetActivity(), CoroutineScope by MainScope() {
    protected open val showShortcuts = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /**
         * We want the user to unlock the device when adding a widget, since potential configuration Activities
         * won't show on the lock screen.
         */
        DismissOrUnlockActivity.launch(this)

        setContent {
            AddWidgetLayout(
                appWidgetManager = appWidgetManager,
                showShortcuts = showShortcuts,
                onBack = {
                    onBackPressedDispatcher.onBackPressed()
                }
            ) {
                if (it is WidgetListInfo) {
                    tryBindWidget(it.providerInfo)
                } else if (it is ShortcutListInfo) {
                    tryBindShortcut(it)
                }
            }
        }

        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowHomeEnabled(true)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        DismissOrUnlockActivity.launch(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}