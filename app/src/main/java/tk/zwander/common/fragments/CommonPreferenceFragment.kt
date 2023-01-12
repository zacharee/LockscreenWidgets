package tk.zwander.common.fragments

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.CallSuper
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.common.util.HandlerRegistry
import tk.zwander.common.util.backup.BackupRestoreManager
import tk.zwander.common.util.backup.backupRestoreManager
import tk.zwander.common.util.isOneUI
import tk.zwander.common.util.logUtils
import tk.zwander.lockscreenwidgets.R
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

abstract class CommonPreferenceFragment : PreferenceFragmentCompat() {
    protected abstract val prefsHandler: HandlerRegistry
    protected abstract val which: BackupRestoreManager.Which

    protected abstract val blurOptionKeys: Array<String>

    protected val showBlurOptions by lazy {
        requireContext().isOneUI || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    private val onWidgetBackUp = registerForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri: Uri? ->
        if (uri != null) {
            requireContext().apply {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { output ->
                    output.write(backupRestoreManager.createBackupString(which))
                }
            }
        }
    }

    private val onWidgetRestore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            requireContext().apply {
                try {
                    val input = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()

                    if (!backupRestoreManager.restoreBackupString(input, which)) {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.error)
                            .setMessage(R.string.unable_to_restore_widgets)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                        logUtils.normalLog("Unable to restore widgets")
                    } else {
                        requireActivity().finish()
                    }
                } catch (e: FileNotFoundException) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.error)
                        .setMessage(R.string.unable_to_restore_widgets)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    logUtils.normalLog("Unable to restore widgets", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsHandler.register(requireContext())
    }

    @CallSuper
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_backup_restore, rootKey)

        findPreference<Preference>("back_up_widgets")?.setOnPreferenceClickListener {
            val formatter = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

            try {
                onWidgetBackUp.launch("lockscreen_widgets_${which.name.lowercase()}_backup_${formatter.format(Date())}.lswidg")
            } catch (e: Exception) {
                context?.logUtils?.debugLog("Unable to back up widgets", e)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.error)
                    .setMessage(R.string.unable_to_back_up_widgets)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            true
        }

        findPreference<Preference>("restore_widgets")?.setOnPreferenceClickListener {
            try {
                onWidgetRestore.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                context?.logUtils?.debugLog("Unable to restore widgets", e)
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.error)
                    .setMessage(R.string.unable_to_restore_widgets)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            true
        }
    }

    override fun addPreferencesFromResource(preferencesResId: Int) {
        super.addPreferencesFromResource(preferencesResId)

        if (!showBlurOptions) {
            blurOptionKeys.forEach {
                preferenceScreen.removePreferenceRecursively(it)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        return object : PreferenceGroupAdapter(preferenceScreen) {
            @SuppressLint("RestrictedApi")
            override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                val preference = getItem(position)
                preference?.isSingleLineTitle = false

                super.onBindViewHolder(holder, position)

                if (preference is PreferenceCategory)
                    setZeroPaddingToLayoutChildren(holder.itemView)
                else
                    holder.itemView.findViewById<View?>(R.id.icon_frame)?.visibility =
                        if (preference?.icon == null) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        prefsHandler.unregister(requireContext())
    }

    private fun setZeroPaddingToLayoutChildren(view: View) {
        if (view !is ViewGroup)
            return
        val childCount = view.childCount
        for (i in 0 until childCount) {
            setZeroPaddingToLayoutChildren(view.getChildAt(i))
            view.setPaddingRelative(0, view.paddingTop, view.paddingEnd, view.paddingBottom)
        }
    }
}