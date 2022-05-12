package tk.zwander.lockscreenwidgets.fragments

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.HideForIDsActivity
import tk.zwander.lockscreenwidgets.activities.OnboardingActivity
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.*
import tk.zwander.seekbarpreference.SeekBarPreference
import java.text.SimpleDateFormat
import java.util.*

/**
 * The settings page.
 * Most needed logic is handled by AndroidX, but there are some cases where
 * we need to either request permissions or pass extras to Activities we launch.
 */
class SettingsFragment : PreferenceFragmentCompat() {
    private val onDebugExportResult = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri: Uri? ->
        if (uri != null) {
            requireContext().apply {
                logUtils.exportLog(contentResolver.openOutputStream(uri))
            }
        }
    }

    private val onWidgetBackUp = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri: Uri? ->
        if (uri != null) {
            requireContext().apply {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { output ->
                    output.write(backupRestoreManager.createBackupString())
                }
            }
        }
    }

    private val onWidgetRestore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            requireContext().apply {
                val input = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()

                if (!backupRestoreManager.restoreBackupString(input)) {
                    Toast.makeText(this, R.string.unable_to_restore_widgets, Toast.LENGTH_SHORT)
                    logUtils.normalLog("Unable to restore widgets")
                } else {
                    requireActivity().finish()
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_settings, rootKey)

        findPreference<SwitchPreference>(PrefManager.KEY_HIDE_ON_NOTIFICATIONS)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue.toString().toBoolean() && !requireContext().isNotificationListenerActive) {
                OnboardingActivity.start(requireContext(), OnboardingActivity.RetroMode.NOTIFICATION)
                false
            } else true
        }

        //Currently, the option to show the frame when the notification center is fully expanded is only
        //for Samsung One UI 1.0 and above, so we need to hide the relevant toggles.
        val ncCondition = requireContext().isOneUI

        findPreference<SwitchPreference>(PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER)?.apply {
            if (!ncCondition) {
                preferenceScreen.removePreferenceRecursively(key)
            }
        }

        findPreference<SwitchPreference>(PrefManager.KEY_SHOW_ON_MAIN_LOCK_SCREEN)?.apply {
            if (!ncCondition) {
                preferenceScreen.removePreferenceRecursively(key)
            }
        }

        findPreference<SwitchPreference>(PrefManager.KEY_SEPARATE_POS_FOR_LOCK_NC)?.apply {
            if (!ncCondition) {
                preferenceScreen.removePreferenceRecursively(key)
            }
        }

        findPreference<SwitchPreference>(PrefManager.KEY_HIDE_ON_FACEWIDGETS)?.apply {
            if (!requireContext().isOneUI || Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                preferenceScreen.removePreferenceRecursively(key)
            }
        }

        findPreference<SwitchPreference>(PrefManager.KEY_FRAME_MASKED_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            (if (newValue.toString().toBoolean()
                && requireContext().checkCallingOrSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                OnboardingActivity.start(requireContext(), OnboardingActivity.RetroMode.STORAGE)
                false
            } else true)
        }

        val showBlurOptions = requireContext().isOneUI || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        findPreference<SwitchPreference>(PrefManager.KEY_BLUR_BACKGROUND)?.isVisible = showBlurOptions
        findPreference<SeekBarPreference>(PrefManager.KEY_BLUR_BACKGROUND_AMOUNT)?.isVisible = showBlurOptions

        findPreference<Preference>("present_ids_launch")?.setOnPreferenceClickListener {
            HideForIDsActivity.start(requireContext(), HideForIDsActivity.Type.PRESENT)
            true
        }

        findPreference<Preference>("non_present_ids_launch")?.setOnPreferenceClickListener {
            HideForIDsActivity.start(requireContext(), HideForIDsActivity.Type.NON_PRESENT)
            true
        }

        findPreference<Preference>("clear_debug_log")?.setOnPreferenceClickListener {
            context?.logUtils?.resetDebugLog()
            true
        }

        findPreference<Preference>("export_debug_log")?.setOnPreferenceClickListener {
            val formatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

            onDebugExportResult.launch("lockscreen_widgets_debug_${formatter.format(Date())}.txt")
            true
        }

        findPreference<Preference>("back_up_widgets")?.setOnPreferenceClickListener {
            val formatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

            onWidgetBackUp.launch("lockscreen_widgets_backup_${formatter.format(Date())}.lswidg")
            true
        }

        findPreference<Preference>("restore_widgets")?.setOnPreferenceClickListener {
            onWidgetRestore.launch(arrayOf("*/*"))
            true
        }
    }
}