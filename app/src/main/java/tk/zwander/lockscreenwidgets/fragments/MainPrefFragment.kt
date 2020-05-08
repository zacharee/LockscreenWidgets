package tk.zwander.lockscreenwidgets.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import tk.zwander.systemuituner.lockscreenwidgets.R

class MainPrefFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_main, rootKey)
    }
}