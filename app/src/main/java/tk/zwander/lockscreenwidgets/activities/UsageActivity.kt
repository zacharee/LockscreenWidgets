package tk.zwander.lockscreenwidgets.activities

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import com.bugsnag.android.performance.compose.MeasuredComposable
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.util.setThemedContent
import tk.zwander.lockscreenwidgets.compose.UsageLayout

class UsageActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setThemedContent {
            MeasuredComposable(name = "UsageLayout") {
                UsageLayout(
                    title = title.toString(),
                    modifier = Modifier.fillMaxSize()
                        .statusBarsPadding(),
                )
            }
        }
    }
}