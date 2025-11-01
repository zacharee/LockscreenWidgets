package tk.zwander.common.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.bugsnag.android.performance.compose.MeasuredComposable
import tk.zwander.common.compose.hide.HideOnAppsChooserLayout
import tk.zwander.common.util.setThemedContent

class HideOnAppsChooserActivity : BaseActivity() {
    companion object {
        fun start(context: Context) {
            val intent = Intent(context, HideOnAppsChooserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setThemedContent {
            MeasuredComposable(name = "HideOnAppsChooser") {
                HideOnAppsChooserLayout(onBackPressedDispatcher::onBackPressed)
            }
        }
    }
}
