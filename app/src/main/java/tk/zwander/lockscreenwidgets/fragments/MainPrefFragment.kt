package tk.zwander.lockscreenwidgets.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import tk.zwander.lockscreenwidgets.R

/**
 * The home-page of the app.
 * Currently contains social links and such.
 */
class MainPrefFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_main, rootKey)
    }
}