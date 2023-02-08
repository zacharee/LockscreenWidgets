package tk.zwander.common.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import tk.zwander.common.compose.hide.HideOnAppsChooserLayout

class HideOnAppsChooserActivity : ComponentActivity() {
    companion object {
        fun start(context: Context) {
            val intent = Intent(context, HideOnAppsChooserActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            HideOnAppsChooserLayout(onBackPressedDispatcher::onBackPressed)
        }
    }
}
