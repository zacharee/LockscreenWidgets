package tk.zwander.common.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.util.contracts.rememberCreateDocumentLauncherWithDownloadFallback
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.compose.HideForIDsLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeSet

/**
 * Configuration Activity for the "Hide on Present IDs" and "Hide on Non-Present IDs"
 * options. The appropriate list of IDs will be loaded from and saved to SharedPreferences.
 *
 * The user can add, remove, back-up, and restore IDs from this Activity.
 */
class HideForIDsActivity : BaseActivity() {
    companion object {
        const val EXTRA_TYPE = "type"

        fun start(context: Context, type: Type) {
            val intent = Intent(context, HideForIDsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra(EXTRA_TYPE, type.toString())

            context.startActivity(intent)
        }
    }

    /**
     * Enum class to track the current ID list type
     */
    enum class Type {
        PRESENT,
        NON_PRESENT,
        NONE
    }

    private val type by lazy { intent?.getStringExtra(EXTRA_TYPE).run { if (this == null) Type.NONE else Type.valueOf(this) } }

    private val items by lazy {
        MutableStateFlow(
            when (type) {
                Type.PRESENT -> TreeSet(prefManager.presentIds)
                Type.NON_PRESENT -> TreeSet(prefManager.nonPresentIds)
                else -> TreeSet()
            }
        )
    }

    private val gson by lazy { prefManager.gson }
    private val format = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())

    private val openRequest = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        //Copy the IDs stored in the specified file to the list here
        contentResolver.openInputStream(uri ?: return@registerForActivityResult)?.use { input ->
            val builder = StringBuilder()

            input.bufferedReader().useLines { seq ->
                seq.forEach {
                    builder.append(it)
                }
            }

            val list = try {
                gson.fromJson<HashSet<String>>(
                    builder.toString(),
                    object : TypeToken<HashSet<String>>() {}.type
                )
            } catch (e: Exception) {
                logUtils.debugLog("Unable to parse ID list", e)

                null
            }

            if (list.isNullOrEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.invalid_id_backup_error)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } else {
                items.value = TreeSet(list)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = resources.getString(when (type) {
            Type.PRESENT -> R.string.settings_screen_present_ids
            Type.NON_PRESENT -> R.string.settings_screen_non_present_ids
            Type.NONE -> R.string.app_name
        })

        setContent {
            val items by this.items.collectAsState()

            val saveRequest = rememberCreateDocumentLauncherWithDownloadFallback(mimeType = "text/plain") { uri ->
                //Write the current list of IDs to the specified file
                contentResolver.openOutputStream(uri ?: return@rememberCreateDocumentLauncherWithDownloadFallback)?.use { out ->
                    val stringified = gson.toJson(this.items.value)

                    out.bufferedWriter().use { writer ->
                        writer.append(stringified)
                    }
                }
            }

            AppTheme {
                HideForIDsLayout(
                    items = items,
                    title = title.toString(),
                    onAdd = {
                        if (it.contains(":id/")) {
                            this.items.value = TreeSet(items + it)
                        } else {
                            this.items.value = TreeSet(items + "com.android.systemui:id/$it")
                        }
                    },
                    onRemove = {
                        this.items.value = TreeSet(items - it)
                    },
                    onBackUpClicked = {
                        saveRequest.launch("LockscreenWidgets_ID_Backup_${format.format(Date())}.lsw")
                    },
                    onRestoreClicked = {
                        openRequest.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxSize()
                        .systemBarsPadding()
                        .imePadding()
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        //Persist the current list to the proper preference
        when (type) {
            Type.PRESENT -> prefManager.presentIds = items.value.toHashSet()
            Type.NON_PRESENT -> prefManager.nonPresentIds = items.value.toHashSet()
            else -> {}
        }
    }
}