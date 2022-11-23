package tk.zwander.widgetdrawer.fragments

import android.os.Bundle
import androidx.preference.SwitchPreference
import tk.zwander.common.fragments.CommonPreferenceFragment
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.HandlerRegistry
import tk.zwander.lockscreenwidgets.util.PrefManager
import tk.zwander.lockscreenwidgets.util.backup.BackupRestoreManager
import tk.zwander.lockscreenwidgets.util.handler
import tk.zwander.lockscreenwidgets.util.prefManager

class DrawerSettings : CommonPreferenceFragment() {
    override val prefsHandler = HandlerRegistry {
        handler(PrefManager.KEY_DRAWER_ENABLED) {
            findPreference<SwitchPreference>(PrefManager.KEY_DRAWER_ENABLED)?.isChecked =
                requireContext().prefManager.drawerEnabled
        }
    }
    override val which = BackupRestoreManager.Which.DRAWER
    override val blurOptionKeys: Array<String> = arrayOf(
        PrefManager.KEY_BLUR_DRAWER_BACKGROUND,
        PrefManager.KEY_BLUR_DRAWER_BACKGROUND_AMOUNT
    )

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        addPreferencesFromResource(R.xml.prefs_drawer)
    }
}