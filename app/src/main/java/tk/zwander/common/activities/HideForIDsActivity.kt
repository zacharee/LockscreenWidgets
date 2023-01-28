package tk.zwander.common.activities

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.reflect.TypeToken
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.adapters.HideForIDsAdapter
import tk.zwander.lockscreenwidgets.databinding.ActivityHideForIdsBinding
import tk.zwander.lockscreenwidgets.databinding.AddIdDialogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /**
     * An implementation of [ItemTouchHelper.SimpleCallback] that provides a framework for swipe-to-delete in either direction.
     * Modified from: https://github.com/kitek/android-rv-swipe-delete/blob/master/app/src/main/java/pl/kitek/rvswipetodelete/SwipeToDeleteCallback.kt
     */
    abstract class SwipeToDeleteCallback(context: Context) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_delete_24)
        private val intrinsicWidth = deleteIcon?.intrinsicWidth ?: 1
        private val intrinsicHeight = deleteIcon?.intrinsicHeight ?: 1
        private val background = ColorDrawable()
        private val backgroundColor = Color.parseColor("#f44336")
        private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            //This callback implementation isn't for moving items.
            return false
        }

        /**
         * Take care of drawing the delete icon behind the item
         * being swiped.
         */
        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            val itemView = viewHolder.itemView
            val itemHeight = itemView.bottom - itemView.top
            val isCanceled = dX == 0f && !isCurrentlyActive

            if (isCanceled) {
                clearCanvas(c, itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                return
            }

            //Draw the red delete background
            background.color = backgroundColor
            background.setBounds(itemView.left, itemView.top, itemView.right, itemView.bottom)
            background.draw(c)

            //Calculate position of delete icon
            val deleteIconTop = itemView.top + (itemHeight - intrinsicHeight) / 2
            val deleteIconMargin = (itemHeight - intrinsicHeight) / 2
            val deleteIconLeft = if (dX < 0) itemView.right - deleteIconMargin - intrinsicWidth else itemView.left + deleteIconMargin
            val deleteIconRight = if (dX < 0) itemView.right - deleteIconMargin else itemView.left + deleteIconMargin + intrinsicWidth
            val deleteIconBottom = deleteIconTop + intrinsicHeight

            //Draw the delete icon
            deleteIcon?.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
            deleteIcon?.draw(c)

            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }

        private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
            c?.drawRect(left, top, right, bottom, clearPaint)
        }
    }

    private val adapter by lazy { HideForIDsAdapter() }
    private val type by lazy { intent?.getStringExtra(EXTRA_TYPE).run { if (this == null) Type.NONE else Type.valueOf(this) } }
    private val items by lazy {
        when (type) {
            Type.PRESENT -> prefManager.presentIds
            Type.NON_PRESENT -> prefManager.nonPresentIds
            else -> HashSet()
        }
    }
    private val gson by lazy { prefManager.gson }
    private val format = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
    private val activityBinding by lazy { ActivityHideForIdsBinding.inflate(layoutInflater) }

    private val saveRequest = registerForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri ->
        //Write the current list of IDs to the specified file
        contentResolver.openOutputStream(uri ?: return@registerForActivityResult)?.use { out ->
            val stringified = gson.toJson(items)

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
                items.clear()
                items.addAll(list)
                adapter.items.replaceAll(list)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(activityBinding.root)
        title = resources.getString(when (type) {
            Type.PRESENT -> R.string.settings_screen_present_ids
            Type.NON_PRESENT -> R.string.settings_screen_non_present_ids
            Type.NONE -> R.string.app_name
        })

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        adapter.items.replaceAll(items)
        activityBinding.list.adapter = adapter
        activityBinding.list.addItemDecoration(DividerItemDecoration(this, RecyclerView.VERTICAL))

        val swipeHandler = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                //Make sure we remove the swiped item from all lists.
                items.remove(adapter.items.removeItemAt(viewHolder.bindingAdapterPosition))
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(activityBinding.list)
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
                            items.add(input)
                            adapter.items.add(input)
                        } else {
                            items.add("com.android.systemui:id/$input")
                            adapter.items.add("com.android.systemui:id/$input")
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
            Type.PRESENT -> prefManager.presentIds = items
            Type.NON_PRESENT -> prefManager.nonPresentIds = items
            else -> {}
        }
    }
}