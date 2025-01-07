package tk.zwander.common.activities.add

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.compose.add.AddWidgetLayout
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetSizeData
import tk.zwander.common.util.componentNameCompat
import tk.zwander.common.util.density
import tk.zwander.common.util.shortcutIdManager
import tk.zwander.common.util.toSafeBitmap
import tk.zwander.lockscreenwidgets.data.list.LauncherItemListInfo
import tk.zwander.lockscreenwidgets.data.list.LauncherShortcutListInfo
import tk.zwander.lockscreenwidgets.data.list.ShortcutListInfo
import tk.zwander.lockscreenwidgets.data.list.WidgetListInfo

/**
 * Manage the widget addition flow: selection, permissions, configurations, etc.
 */
abstract class AddWidgetActivity : BaseBindWidgetActivity(), CoroutineScope by MainScope() {
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
                showShortcuts = showShortcuts,
                onBack = onBackPressedDispatcher::onBackPressed,
            ) {
                when (it) {
                    is WidgetListInfo -> {
                        tryBindWidget(it.itemInfo)
                    }
                    is ShortcutListInfo -> {
                        tryBindShortcut(it)
                    }
                    is LauncherItemListInfo -> {
                        val item = WidgetData.launcherItem(
                            shortcutIdManager.allocateShortcutId(),
                            it.appInfo.appInfo.packageName,
                            it.itemInfo.componentInfo.componentNameCompat,
                            WidgetSizeData(1, 1),
                        )

                        addNewShortcut(item)
                    }
                    is LauncherShortcutListInfo -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                            val shortcut = WidgetData.shortcut(
                                shortcutIdManager.allocateShortcutId(),
                                it.name, it.icon?.loadDrawable(this)?.toSafeBitmap(density, maxSize = 128.dp),
                                null, it.itemInfo.intent,
                                WidgetSizeData(1, 1)
                            )

                            addNewShortcut(shortcut)
                        }
                    }
                }
            }
        }

        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.setDisplayShowHomeEnabled(true)
    }

    override fun onNewIntent(intent: Intent) {
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