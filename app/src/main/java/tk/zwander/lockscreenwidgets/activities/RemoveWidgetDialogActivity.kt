package tk.zwander.lockscreenwidgets.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.lockscreenwidgets.IRemoveConfirmCallback
import tk.zwander.lockscreenwidgets.R

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

        val callbackBinder = intent?.getBundleExtra(EXTRA_CALLBACK)?.getBinder(EXTRA_CALLBACK)
        val callback = if (callbackBinder != null) IRemoveConfirmCallback.Stub.asInterface(callbackBinder) else null

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
}