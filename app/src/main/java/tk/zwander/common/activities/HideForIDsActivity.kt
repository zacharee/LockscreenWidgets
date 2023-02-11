package tk.zwander.common.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.compose.HideForIDsLayout
import tk.zwander.lockscreenwidgets.databinding.AddIdDialogBinding
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
class HideForIDsActivity : AppCompatActivity() {
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

    private val saveRequest = registerForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri ->
        //Write the current list of IDs to the specified file
        contentResolver.openOutputStream(uri ?: return@registerForActivityResult)?.use { out ->
            val stringified = gson.toJson(items.value)

            out.bufferedWriter().use { writer ->
                writer.append(stringified)
            }
        }
    }

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

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        setContent {
            val items by this.items.collectAsState()

            AppTheme {
                HideForIDsLayout(
                    items = items,
                    onRemove = {
                        this.items.value = TreeSet(items - it)
                    }
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.hide_for_ids, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.add -> {
                //Show the add ID dialog.
                //The user can either enter a fully-qualified ID,
                //or just the ID itself. A basic ID will have the System UI
                //namespace prepended. A fully-qualified ID will be entered as-is
                //(useful if the ID isn't part of the System UI namespace).
                val inputBinding = AddIdDialogBinding.inflate(layoutInflater, null, false)
                MaterialAlertDialogBuilder(this)
                    .setView(inputBinding.root)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val input = inputBinding.idInput.text?.toString()
                        if (input.isNullOrBlank()) return@setPositiveButton
                        if (input.contains(":id/")) {
                            items.value = TreeSet(items.value + input)
                        } else {
                            items.value = TreeSet(items.value + "com.android.systemui:id/$input")
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            R.id.backup -> {
                //Start the list backup flow.
                saveRequest.launch("LockscreenWidgets_ID_Backup_${format.format(Date())}.lsw")
                true
            }
            R.id.restore -> {
                //Start the list restore flow.
                openRequest.launch(arrayOf("*/*"))
                true
            }
            else -> return super.onOptionsItemSelected(item)
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