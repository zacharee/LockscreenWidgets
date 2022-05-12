package tk.zwander.lockscreenwidgets

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tk.zwander.lockscreenwidgets.activities.OnboardingActivity
import tk.zwander.lockscreenwidgets.activities.SettingsActivity
import tk.zwander.lockscreenwidgets.activities.UsageActivity
import tk.zwander.lockscreenwidgets.util.*

/**
 * Host the main page of the app (the social links). It also hosts the buttons to add a widget, view usage
 * details, and open the settings.
 *
 * If it's the user's first time running the app, or a required permission is missing (i.e. Accessibility),
 * this Activity will also make sure to start [OnboardingActivity] in the proper mode.
 */
class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    companion object {
        const val REQ_INTRO = 102
    }

    private val frameDelegate: WidgetFrameDelegate?
        get() {
            return WidgetFrameDelegate.peekInstance(this).also {
                if (it == null) {
                    Toast.makeText(this, R.string.accessibility_not_started, Toast.LENGTH_SHORT).show()
                }
            }
        }

    private val introRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
        setContentView(R.layout.activity_main)

        if (prefManager.firstRun || !isAccessibilityEnabled) {
            OnboardingActivity.startForResult(this, introRequest,
                if (!prefManager.firstRun) OnboardingActivity.RetroMode.ACCESSIBILITY else OnboardingActivity.RetroMode.NONE)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.add_widget -> {
                eventManager.sendEvent(Event.LaunchAddWidget)
                true
            }
            R.id.usage -> {
                val intent = Intent(this, UsageActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            }
            R.id.preview -> {
                frameDelegate?.let {
                    it.isPreview = !it.isPreview
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onStop() {
        super.onStop()

        frameDelegate?.isPreview = false
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}