package tk.zwander.lockscreenwidgets.fragments

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
import tk.zwander.lockscreenwidgets.services.isNotificationListenerActive
import tk.zwander.lockscreenwidgets.util.PrefManager
import tk.zwander.lockscreenwidgets.util.backup.BackupRestoreManager
import tk.zwander.lockscreenwidgets.util.backup.backupRestoreManager
import tk.zwander.lockscreenwidgets.util.hasStoragePermission
import tk.zwander.lockscreenwidgets.util.isOneUI
import tk.zwander.lockscreenwidgets.util.logUtils
import java.text.SimpleDateFormat
import java.util.*

/**
 * The settings page.
 * Most needed logic is handled by AndroidX, but there are some cases where
 * we need to either request permissions or pass extras to Activities we launch.
 */
class SettingsFragment : PreferenceFragmentCompat() {
    private val onWidgetBackUp = registerForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri: Uri? ->
        if (uri != null) {
            requireContext().apply {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { output ->
                    output.write(backupRestoreManager.createBackupString(BackupRestoreManager.Which.FRAME))
                }
            }
        }
    }

    private val onWidgetRestore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            requireContext().apply {
                val input = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()

                if (!backupRestoreManager.restoreBackupString(input, BackupRestoreManager.Which.FRAME)) {
                    Toast.makeText(this, R.string.unable_to_restore_widgets, Toast.LENGTH_SHORT)
                    logUtils.normalLog("Unable to restore widgets")
                } else {
                    requireActivity().finish()
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_frame, rootKey)

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
            (if (newValue.toString().toBoolean() && !requireContext().hasStoragePermission) {
                OnboardingActivity.start(requireContext(), OnboardingActivity.RetroMode.STORAGE)
                false
            } else true)
        }

        val showBlurOptions = requireContext().isOneUI || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (!showBlurOptions) {
            preferenceScreen.removePreferenceRecursively(PrefManager.KEY_BLUR_BACKGROUND)
            preferenceScreen.removePreferenceRecursively(PrefManager.KEY_BLUR_BACKGROUND_AMOUNT)
        }

        findPreference<Preference>("present_ids_launch")?.setOnPreferenceClickListener {
            HideForIDsActivity.start(requireContext(), HideForIDsActivity.Type.PRESENT)
            true
        }

        findPreference<Preference>("non_present_ids_launch")?.setOnPreferenceClickListener {
            HideForIDsActivity.start(requireContext(), HideForIDsActivity.Type.NON_PRESENT)
            true
        }

        findPreference<Preference>("back_up_widgets")?.setOnPreferenceClickListener {
            val formatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

            try {
                onWidgetBackUp.launch("lockscreen_widgets_frame_backup_${formatter.format(Date())}.lswidg")
            } catch (e: Exception) {
                context?.logUtils?.debugLog("Unable to back up widgets", e)
                Toast.makeText(requireContext(), R.string.unable_to_back_up_widgets, Toast.LENGTH_SHORT).show()
            }
            true
        }

        findPreference<Preference>("restore_widgets")?.setOnPreferenceClickListener {
            try {
                onWidgetRestore.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                context?.logUtils?.debugLog("Unable to restore widgets", e)
                Toast.makeText(requireContext(), R.string.unable_to_restore_widgets, Toast.LENGTH_SHORT).show()
            }
            true
        }
    }
}