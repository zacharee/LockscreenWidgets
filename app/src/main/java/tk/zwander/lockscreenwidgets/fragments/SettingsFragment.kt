package tk.zwander.lockscreenwidgets.fragments

import android.os.Build
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import tk.zwander.common.activities.HideForIDsActivity
import tk.zwander.common.activities.HideOnAppsChooserActivity
import tk.zwander.common.activities.OnboardingActivity
import tk.zwander.common.fragments.CommonPreferenceFragment
import tk.zwander.common.util.Event
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.backup.BackupRestoreManager
import tk.zwander.common.util.canReadWallpaper
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.handler
import tk.zwander.common.util.isOneUI
import tk.zwander.common.util.isPixelUI
import tk.zwander.common.util.isTouchWiz
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.services.isNotificationListenerActive

/**
 * The settings page.
 * Most needed logic is handled by AndroidX, but there are some cases where
 * we need to either request permissions or pass extras to Activities we launch.
 */
class SettingsFragment : CommonPreferenceFragment() {
    override val prefsHandler = HandlerRegistry {
        handler(PrefManager.KEY_WIDGET_FRAME_ENABLED) {
            findPreference<SwitchPreferenceCompat>(PrefManager.KEY_WIDGET_FRAME_ENABLED)?.isChecked =
                requireContext().prefManager.widgetFrameEnabled
        }
        handler(PrefManager.KEY_CURRENT_FRAMES) {
            findPreference<Preference>("add_secondary_frame")?.updateFrameCountSummary()
            findPreference<Preference>("remove_secondary_frame")?.updateRemoveFrameVisibility()
        }
    }
    override val which = BackupRestoreManager.Which.FRAME
    override val blurOptionKeys: Array<String> = arrayOf(
        PrefManager.KEY_BLUR_BACKGROUND,
        PrefManager.KEY_BLUR_BACKGROUND_AMOUNT
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        addPreferencesFromResource(R.xml.prefs_frame)

        findPreference<SwitchPreferenceCompat>(PrefManager.KEY_HIDE_ON_NOTIFICATIONS)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue.toString().toBoolean() && !requireContext().isNotificationListenerActive) {
                OnboardingActivity.start(requireContext(), OnboardingActivity.RetroMode.NOTIFICATION)
                false
            } else true
        }

        //Currently, the option to show the frame when the notification center is fully expanded is only
        //for Samsung One UI 1.0 and above, so we need to hide the relevant toggles.
        val ncCondition = requireContext().isOneUI || (requireContext().isPixelUI && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

        findPreference<SwitchPreferenceCompat>(PrefManager.KEY_SHOW_IN_NOTIFICATION_CENTER)?.apply {
            if (!ncCondition) {
                preferenceScreen.removePreferenceRecursively(key)
            }
        }

        findPreference<SwitchPreferenceCompat>(PrefManager.KEY_SHOW_ON_MAIN_LOCK_SCREEN)?.apply {
            if (!ncCondition) {
                preferenceScreen.removePreferenceRecursively(key)
            }
        }

        findPreference<SwitchPreferenceCompat>(PrefManager.KEY_SEPARATE_POS_FOR_LOCK_NC)?.apply {
            if (!ncCondition) {
                preferenceScreen.removePreferenceRecursively(key)
            }
        }

        findPreference<SwitchPreferenceCompat>(PrefManager.KEY_HIDE_ON_FACEWIDGETS)?.apply {
            if (!requireContext().isOneUI || Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                preferenceScreen.removePreferenceRecursively(key)
            }
        }

        findPreference<SwitchPreferenceCompat>(PrefManager.KEY_FRAME_MASKED_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            (if (newValue.toString().toBoolean() && !requireContext().canReadWallpaper) {
                OnboardingActivity.start(requireContext(), OnboardingActivity.RetroMode.STORAGE)
                false
            } else true)
        }

        findPreference<Preference>("present_ids_launch")?.setOnPreferenceClickListener {
            HideForIDsActivity.start(requireContext(), HideForIDsActivity.Type.PRESENT)
            true
        }

        findPreference<Preference>("non_present_ids_launch")?.setOnPreferenceClickListener {
            HideForIDsActivity.start(requireContext(), HideForIDsActivity.Type.NON_PRESENT)
            true
        }

        findPreference<Preference>("hide_on_present_apps")?.setOnPreferenceClickListener {
            HideOnAppsChooserActivity.start(requireContext())
            true
        }

        findPreference<Preference>(PrefManager.KEY_HIDE_ON_EDGE_PANEL)?.apply {
            if (!requireContext().isTouchWiz) {
                preferenceScreen.removePreferenceRecursively(key)
            }
        }

        findPreference<Preference>("add_secondary_frame")?.apply {
            updateFrameCountSummary()
            setOnPreferenceClickListener {
                val maxFrameId = context.prefManager.currentSecondaryFrames.maxOrNull() ?: 1
                val newFrameId = maxFrameId + 1

                context.prefManager.currentSecondaryFrames = context.prefManager.currentSecondaryFrames.toMutableList().apply {
                    add(newFrameId)
                }
                true
            }
        }

        findPreference<Preference>("remove_secondary_frame")?.apply {
            updateRemoveFrameVisibility()
            setOnPreferenceClickListener {
                requireContext().eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.SHOW_FOR_SELECTION, 101, false))
                true
            }
        }
    }

    override fun onEvent(event: Event) {
        super.onEvent(event)

        if (event is Event.FrameSelected) {
            if (event.frameId != null && event.requestCode == 101) {
                requireContext().prefManager.currentSecondaryFrames = requireContext().prefManager.currentSecondaryFrames.toMutableList().apply {
                    removeAll { it == event.frameId }
                }
                requireContext().eventManager.sendEvent(Event.PreviewFrames(Event.PreviewFrames.ShowMode.HIDE))
            }
        }
    }

    private fun Preference.updateFrameCountSummary() {
        val quantity = requireContext().prefManager.currentSecondaryFrames.size + 1
        summary = resources.getQuantityString(R.plurals.frame_count_info, quantity, "$quantity")
    }

    private fun Preference.updateRemoveFrameVisibility() {
        isVisible = requireContext().prefManager.currentSecondaryFrames.isNotEmpty()
    }
}