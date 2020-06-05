package tk.zwander.lockscreenwidgets.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.lockscreenwidgets.IRemoveConfirmCallback
import tk.zwander.lockscreenwidgets.R

/**
 * An Activity that simply shows a confirmation dialog.
 * This is used to confirm whether the user truly wants to remove
 * a specific widget from the frame.
 */
class RemoveWidgetDialogActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_CALLBACK = "CALLBACK"

        fun start(context: Context, removeCallback: IRemoveConfirmCallback) {
            val intent = Intent(context, RemoveWidgetDialogActivity::class.java)
            intent.putExtra(EXTRA_CALLBACK, Bundle().apply {
                putBinder(EXTRA_CALLBACK, removeCallback.asBinder())
            })
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }

    private val callbackBinder by lazy { intent?.getBundleExtra(EXTRA_CALLBACK)?.getBinder(EXTRA_CALLBACK) }
    private val callback by lazy { if (callbackBinder != null) IRemoveConfirmCallback.Stub.asInterface(callbackBinder) else null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Make sure we show over the lock scree
        window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

        //Create and show the dialog.
        //Make sure to finish this Activity if the dialog
        //is dismissed for any reason.
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.alert_remove_widget_confirm)
            .setMessage(R.string.alert_remove_widget_confirm_desc)
            .setPositiveButton(R.string.yes) { _, _ ->
                try {
                    callback?.onWidgetRemovalConfirmed()
                } catch (e: Exception) {}
            }
            .setNegativeButton(R.string.no, null)
            .setOnDismissListener {
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .create()
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            callback?.onDismiss()
        } catch (e: Exception) {}
    }
}