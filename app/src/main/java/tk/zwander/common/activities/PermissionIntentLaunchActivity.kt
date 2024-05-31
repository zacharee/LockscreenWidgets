package tk.zwander.common.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.logUtils
import tk.zwander.lockscreenwidgets.R

@SuppressLint("CustomSplashScreen")
class PermissionIntentLaunchActivity : ComponentActivity() {
    companion object {
        const val EXTRA_INTENT_TO_LAUNCH = "intent_to_launch"
        const val EXTRA_LAUNCH_TYPE = "launch_type"

        fun start(context: Context, intent: Intent, launchType: LaunchType) {
            val launcher = Intent(context, PermissionIntentLaunchActivity::class.java)
            launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launcher.putExtra(EXTRA_INTENT_TO_LAUNCH, intent)
            launcher.putExtra(EXTRA_LAUNCH_TYPE, launchType)

            context.startActivity(launcher)
        }
    }

    enum class LaunchType {
        ACTIVITY,
        SERVICE,
        RECEIVER
    }

    private val intentToLaunch: Intent? by lazy {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(EXTRA_INTENT_TO_LAUNCH)
    }
    private val launchType by lazy { (intent.getSerializableExtra(EXTRA_LAUNCH_TYPE) as? LaunchType) ?: LaunchType.ACTIVITY }
    private val permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.all { it.value }) {
            performLaunch()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intentToLaunch == null) {
            finish()
            return
        }

        val permissions = getNecessaryPermissions()

        if (hasNecessaryPermissions(permissions)) {
            performLaunch()
        } else {
            eventManager.sendEvent(Event.CloseDrawer)
            permissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun performLaunch() {
        try {
            when (launchType) {
                LaunchType.ACTIVITY -> startActivity(intentToLaunch)
                LaunchType.SERVICE -> startService(intentToLaunch)
                LaunchType.RECEIVER -> sendBroadcast(intentToLaunch)
            }
        } catch (e: Throwable) {
            logUtils.normalLog("Unable to launch", e)
            Toast.makeText(
                this,
                R.string.unable_to_launch,
                Toast.LENGTH_SHORT
            ).show()
        }

        finish()
    }

    private fun getNecessaryPermissions(): List<String> {
        val permissions = arrayListOf<String>()

        if (intentToLaunch?.action == Intent.ACTION_CALL) {
            permissions.add(android.Manifest.permission.CALL_PHONE)
        }

        return permissions
    }

    private fun hasNecessaryPermissions(permissions: List<String>): Boolean {
        return permissions.all { checkCallingOrSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }
}