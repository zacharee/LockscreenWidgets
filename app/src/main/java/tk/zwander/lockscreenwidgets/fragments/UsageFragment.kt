package tk.zwander.lockscreenwidgets.fragments

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.fragments.dialog.MessageDialogPreferenceFragment
import tk.zwander.lockscreenwidgets.prefs.MessageDialogPreference
import tk.zwander.lockscreenwidgets.util.isOneUI

/**
 * The usage page.
 * Show some explanations for specific options and features.
 */
class UsageFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_usage, rootKey)

        if (!requireContext().isOneUI) {
            findPreference<Preference>("usage_widget_tiles")?.isVisible = false
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is MessageDialogPreference -> {
                val f = MessageDialogPreferenceFragment()
                f.arguments = Bundle().apply {
                    putString("key", preference.key)
                }
                f.setTargetFragment(this, 0)

                f.show(parentFragmentManager, null)
            }
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }
}