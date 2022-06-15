package tk.zwander.lockscreenwidgets

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tk.zwander.lockscreenwidgets.activities.OnboardingActivity
import tk.zwander.lockscreenwidgets.compose.main.MainContent
import tk.zwander.lockscreenwidgets.services.isAccessibilityEnabled
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate
import tk.zwander.lockscreenwidgets.util.prefManager

/**
 * Host the main page of the app (the social links). It also hosts the buttons to add a widget, view usage
 * details, and open the settings.
 *
 * If it's the user's first time running the app, or a required permission is missing (i.e. Accessibility),
 * this Activity will also make sure to start [OnboardingActivity] in the proper mode.
 */
class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val introRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                //The intro sequence or permissions request wasn't successful. Quit.
                finish()
            } else {
                //The user finished the intro sequence or granted the required permission.
                //Stay open, and make sure firstRun is false.
                prefManager.firstRun = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainContent()
        }

        if (prefManager.firstRun || !isAccessibilityEnabled) {
            OnboardingActivity.startForResult(
                this, introRequest,
                if (!prefManager.firstRun) OnboardingActivity.RetroMode.ACCESSIBILITY else OnboardingActivity.RetroMode.NONE
            )
        }
    }

    override fun onStop() {
        super.onStop()

        WidgetFrameDelegate.peekInstance(this)?.updateState { it.copy(isPreview = false) }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}
