package tk.zwander.common.activities.add

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.compose.add.AddWidgetLayout
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

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isSystemInDarkTheme()
                isAppearanceLightNavigationBars = isAppearanceLightStatusBars
            }

            AddWidgetLayout(
                showShortcuts = showShortcuts,
                onBack = onBackPressedDispatcher::onBackPressed,
            ) {
                if (it is WidgetListInfo) {
                    tryBindWidget(it.itemInfo)
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