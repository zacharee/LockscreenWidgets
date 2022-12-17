package tk.zwander.common.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference

/**
 * A [DialogPreference] that acts as an AlertDialog, with customizable messages and buttons.
 */
class MessageDialogPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs)