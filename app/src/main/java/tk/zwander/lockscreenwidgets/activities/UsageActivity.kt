package tk.zwander.lockscreenwidgets.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.compose.AppTheme
import tk.zwander.lockscreenwidgets.compose.UsageLayout
import tk.zwander.lockscreenwidgets.fragments.UsageFragment

/**
 * Host the usage instructions for Lockscreen Widgets.
 * See [UsageFragment]
 */
class UsageActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                UsageLayout(
                    title = title.toString(),
                    modifier = Modifier.fillMaxSize()
                        .statusBarsPadding()
                )
            }
        }
    }
}