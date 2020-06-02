package tk.zwander.lockscreenwidgets.activities

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.activity_hide_for_ids.*
import kotlinx.android.synthetic.main.add_id_dialog.view.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.adapters.HideForIDsAdapter
import tk.zwander.lockscreenwidgets.util.prefManager

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

    enum class Type {
        PRESENT,
        NON_PRESENT,
        NONE
    }

    //https://github.com/kitek/android-rv-swipe-delete/blob/master/app/src/main/java/pl/kitek/rvswipetodelete/SwipeToDeleteCallback.kt
    abstract class SwipeToDeleteCallback(context: Context) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        private val deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_delete_24)!!
        private val intrinsicWidth = deleteIcon.intrinsicWidth
        private val intrinsicHeight = deleteIcon.intrinsicHeight
        private val background = ColorDrawable()
        private val backgroundColor = Color.parseColor("#f44336")
        private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return false
        }

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

            // Draw the red delete background
            background.color = backgroundColor
            background.setBounds(itemView.left, itemView.top, itemView.right, itemView.bottom)
            background.draw(c)

            // Calculate position of delete icon
            val deleteIconTop = itemView.top + (itemHeight - intrinsicHeight) / 2
            val deleteIconMargin = (itemHeight - intrinsicHeight) / 2
            val deleteIconLeft = if (dX < 0) itemView.right - deleteIconMargin - intrinsicWidth else itemView.left + deleteIconMargin
            val deleteIconRight = if (dX < 0) itemView.right - deleteIconMargin else itemView.left + deleteIconMargin + intrinsicWidth
            val deleteIconBottom = deleteIconTop + intrinsicHeight

            // Draw the delete icon
            deleteIcon.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom)
            deleteIcon.draw(c)

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_hide_for_ids)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        adapter.items.replaceAll(items)
        list.adapter = adapter
        list.addItemDecoration(DividerItemDecoration(this, RecyclerView.VERTICAL))

        val swipeHandler = object : SwipeToDeleteCallback(this) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                items.remove(adapter.items.removeItemAt(viewHolder.adapterPosition))
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(list)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.add, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item?.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.add -> {
                val inputView = LayoutInflater.from(this).inflate(R.layout.add_id_dialog, null)
                MaterialAlertDialogBuilder(this)
                    .setView(inputView)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val input = inputView.id_input.text?.toString()
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
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        when (type) {
            Type.PRESENT -> prefManager.presentIds = items
            Type.NON_PRESENT -> prefManager.nonPresentIds = items
            else -> {}
        }
    }
}