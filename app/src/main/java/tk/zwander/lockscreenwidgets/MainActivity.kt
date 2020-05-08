package tk.zwander.lockscreenwidgets

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import tk.zwander.lockscreenwidgets.activities.OnboardingActivity
import tk.zwander.lockscreenwidgets.util.isAccessibilityEnabled
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.systemuituner.lockscreenwidgets.R

class MainActivity : AppCompatActivity() {
    companion object {
        const val REQ_INTRO = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (prefManager.firstRun || !isAccessibilityEnabled) {
            startActivityForResult(
                Intent(this, OnboardingActivity::class.java),
                REQ_INTRO
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_INTRO) {
            if (resultCode != Activity.RESULT_OK) {
                finish()
            } else {
                prefManager.firstRun = false
            }
        }
    }
}