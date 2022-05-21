package tk.zwander.lockscreenwidgets.activities

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import tk.zwander.lockscreenwidgets.util.Event
import tk.zwander.lockscreenwidgets.util.eventManager
import tk.zwander.lockscreenwidgets.util.mainHandler

/**
 * Used when the lock screen or notification center needs to be dismissed.
 * This is either started when the add widget button is tapped from the lock screen/NC
 * or when Lockscreen Widgets detects an Activity being launched from a widget.
 */
class DismissOrUnlockActivity : AppCompatActivity() {
    companion object {
        fun launch(context: Context, runOnMain: Boolean = true) {
            if (runOnMain) {
                mainHandler.postDelayed({
                    launch(context)
                }, 100)
            } else {
                launch(context)
            }
        }

        private fun launch(context: Context) {
            val intent = Intent(context, DismissOrUnlockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            context.startActivity(intent)
        }
    }

    private val kgm by lazy { getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    private val eventListener: (Event.LockscreenDismissed) -> Unit = {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

        if (kgm.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                kgm.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissCancelled() {
                        finish()
                    }

                    override fun onDismissError() {
                        finish()
                    }

                    override fun onDismissSucceeded() {
                        finish()
                    }
                })
            } else {
                //If we're below 8.0, we have to do some weirdness to dismiss the lock screen.
                eventManager.addListener(eventListener)
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            }
        } else {
            @SuppressLint("MissingPermission")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                val i = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(i)
            }

            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        eventManager.removeListener(eventListener)
    }
}