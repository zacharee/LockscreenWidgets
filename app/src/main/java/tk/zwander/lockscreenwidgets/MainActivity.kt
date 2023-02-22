package tk.zwander.lockscreenwidgets

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tk.zwander.common.activities.OnboardingActivity
import tk.zwander.common.compose.main.MainContent
import tk.zwander.common.tiles.NCTile
import tk.zwander.common.tiles.widget.WidgetTileFive
import tk.zwander.common.tiles.widget.WidgetTileFour
import tk.zwander.common.tiles.widget.WidgetTileOne
import tk.zwander.common.tiles.widget.WidgetTileThree
import tk.zwander.common.tiles.widget.WidgetTileTwo
import tk.zwander.common.util.isOneUI
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.services.isAccessibilityEnabled
import tk.zwander.lockscreenwidgets.util.WidgetFrameDelegate

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

        //This should only run on Nougat and above.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //We don't want the NC tile to show on non-One UI devices.
            launch(Dispatchers.IO) {
                packageManager.setComponentEnabledSetting(
                    ComponentName(this@MainActivity, NCTile::class.java),
                    if (isOneUI) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP or PackageManager.SYNCHRONOUS
                )

                val components = arrayOf(
                    WidgetTileOne::class.java,
                    WidgetTileTwo::class.java,
                    WidgetTileThree::class.java,
                    WidgetTileFour::class.java,
                    WidgetTileFive::class.java
                )

                components.forEach {
                    packageManager.setComponentEnabledSetting(
                        ComponentName(this@MainActivity, it),
                        if (isOneUI) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP or PackageManager.SYNCHRONOUS
                    )
                }
            }
        }

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
