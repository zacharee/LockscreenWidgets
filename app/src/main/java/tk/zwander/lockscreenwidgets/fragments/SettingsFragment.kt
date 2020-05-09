package tk.zwander.lockscreenwidgets.fragments

import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.OnboardingActivity
import tk.zwander.lockscreenwidgets.util.PrefManager
import tk.zwander.lockscreenwidgets.util.isNotificationListenerActive

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_settings, rootKey)

        findPreference<SwitchPreference>(PrefManager.KEY_HIDE_ON_NOTIFICATIONS)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue.toString().toBoolean() && !requireContext().isNotificationListenerActive) {
                val intent = Intent(requireContext(), OnboardingActivity::class.java)
                intent.putExtra(OnboardingActivity.EXTRA_RETROACTIVE_FOR_NOTIF, true)
                startActivity(intent)
                false
            } else true
        }
    }
}