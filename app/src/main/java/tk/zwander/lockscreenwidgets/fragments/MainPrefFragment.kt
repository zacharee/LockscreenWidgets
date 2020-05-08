package tk.zwander.lockscreenwidgets.fragments

import android.content.res.Configuration
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.seekbarpreference.SeekBarPreference
import tk.zwander.systemuituner.lockscreenwidgets.R

class MainPrefFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_main, rootKey)

        val screenWidthDp = resources.configuration.screenWidthDp
        val screenHeightDp = resources.configuration.screenHeightDp
        val orientation = resources.configuration.orientation

        val adjustedWidthDp = if (orientation == Configuration.ORIENTATION_PORTRAIT) screenWidthDp else screenHeightDp
        val adjustedHeightDp = if (orientation == Configuration.ORIENTATION_PORTRAIT) screenHeightDp else screenWidthDp

        findPreference<SeekBarPreference>("frame_width_opt")?.apply {
            minValue = adjustedWidthDp
            maxValue = adjustedWidthDp * 10
            defaultValue = resources.getInteger(R.integer.def_frame_width) * 10
            scaledProgress = requireContext().prefManager.frameWidthDp

            onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val scaled = newValue.toString().toFloat()

                    requireContext().prefManager.frameWidthDp = scaled
                    true
                }
        }

        findPreference<SeekBarPreference>("frame_height_opt")?.apply {
            minValue = adjustedHeightDp
            maxValue = adjustedHeightDp * 10
            defaultValue = resources.getInteger(R.integer.def_frame_height) * 10
            scaledProgress = requireContext().prefManager.frameHeightDp

            onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _, newValue ->
                    val scaled = newValue.toString().toFloat()

                    requireContext().prefManager.frameHeightDp = scaled
                    true
                }
        }
    }
}