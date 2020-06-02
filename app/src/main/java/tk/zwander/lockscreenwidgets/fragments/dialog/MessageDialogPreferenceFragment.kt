package tk.zwander.lockscreenwidgets.fragments.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceDialogFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MessageDialogPreferenceFragment : PreferenceDialogFragmentCompat() {
    private var mWhichButtonClicked: Int
        get() = PreferenceDialogFragmentCompat::class.java
            .getDeclaredField("mWhichButtonClicked")
            .apply { isAccessible = true }
            .getInt(this)
        set(value) {
            PreferenceDialogFragmentCompat::class.java
                .getDeclaredField("mWhichButtonClicked")
                .apply { isAccessible = true }
                .set(this, value)
        }

    @SuppressLint("RestrictedApi")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        mWhichButtonClicked = DialogInterface.BUTTON_NEGATIVE

        val builder =
            MaterialAlertDialogBuilder(requireActivity())
                .setTitle(preference.dialogTitle)
                .setIcon(preference.dialogIcon)
                .setPositiveButton(preference.positiveButtonText, this)
                .setNegativeButton(preference.negativeButtonText, this)

        val contentView = onCreateDialogView(context)
        if (contentView != null) {
            onBindDialogView(contentView)
            builder.setView(contentView)
        } else {
            builder.setMessage(preference.dialogMessage)
        }

        onPrepareDialogBuilder(builder)

        // Create the dialog

        // Create the dialog
        val dialog: Dialog = builder.create()
        if (needInputMethod()) {
            requestInputMethod(dialog)
        }

        return dialog
    }

    override fun onDialogClosed(positiveResult: Boolean) {}

    private fun requestInputMethod(dialog: Dialog) {
        val window = dialog.window
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}