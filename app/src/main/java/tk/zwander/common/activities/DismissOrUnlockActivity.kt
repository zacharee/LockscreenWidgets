package tk.zwander.common.activities

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.keyguardManager
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.mainHandler

/**
 * Used when the lock screen or notification center needs to be dismissed.
 * This is either started when the add widget button is tapped from the lock screen/NC
 * or when Lockscreen Widgets detects an Activity being launched from a widget.
 */
class DismissOrUnlockActivity : AppCompatActivity(), EventObserver {
    companion object {
        private const val EXTRA_ACTIVITY_INTENT = "activity_intent"

        fun launch(context: Context, runOnMain: Boolean = true, activityIntent: Intent? = null) {
            if (runOnMain) {
                mainHandler.postDelayed({
                    launch(context, activityIntent)
                }, 100)
            } else {
                launch(context, activityIntent)
            }
        }

        private fun launch(context: Context, activityIntent: Intent?) {
            val intent = Intent(context, DismissOrUnlockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            intent.putExtra(EXTRA_ACTIVITY_INTENT, activityIntent)
            context.startActivity(intent)
        }
    }

    private val kgm by lazy { keyguardManager }
    private val activityIntent: Intent?
        get() = IntentCompat.getParcelableExtra(intent, EXTRA_ACTIVITY_INTENT, Intent::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        eventManager.addObserver(this)
        handle()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        handle()
    }

    override suspend fun onEvent(event: Event) {
        when (event) {
            Event.ScreenOff -> {
                finish()
            }
            else -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        eventManager.removeObserver(this)
    }

    private fun handle() {
        logUtils.debugLog("Handling dismiss/unlock? ${kgm.isKeyguardLocked}")

        if (kgm.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                kgm.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissCancelled() {
                        logUtils.debugLog("Dismiss cancelled.", null)
                        startActivityIntent()
                        finish()
                    }

                    override fun onDismissError() {
                        logUtils.debugLog("Dismiss error.", null)
                        startActivityIntent()
                        finish()
                    }

                    override fun onDismissSucceeded() {
                        logUtils.debugLog("Dismiss success", null)
                        startActivityIntent()
                        finish()
                    }
                })
            } else {
                logUtils.debugLog("Trying dismiss workaround", null)

                //If we're below 8.0, we have to do some weirdness to dismiss the lock screen.
                with (eventManager) {
                    registerListener { _: Event.LockscreenDismissed ->
                        logUtils.debugLog("Dismiss done.", null)
                        startActivityIntent()
                        finish()
                    }
                }
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                )
            }
        } else {
            @SuppressLint("MissingPermission")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                val i = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(i)
            }

            startActivityIntent()
            finish()
        }
    }

    private fun startActivityIntent() {
        activityIntent?.let {
            try {
                startActivity(it)
            } catch (e: Throwable) {
                logUtils.normalLog("Unable to launch Activity Intent $it", e)
            }
        }
    }
}
