package tk.zwander.lockscreenwidgets.util

import android.content.Context
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.data.WidgetType
import kotlin.math.roundToInt
import kotlin.math.sign

fun Context.createTouchHelperCallback(adapter: WidgetFrameAdapter, widgetMoved: (moved: Boolean) -> Unit, onItemSelected: (selected: Boolean) -> Unit): ItemTouchHelper.SimpleCallback {
    return object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return adapter.onMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                .also { moved ->
                    widgetMoved(moved)
                }
        }

        override fun getDragDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            return if (viewHolder is WidgetFrameAdapter.AddWidgetVH || prefManager.lockWidgetFrame) 0
            else super.getDragDirs(recyclerView, viewHolder)
        }

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return if (viewHolder.itemViewType != WidgetType.HEADER.ordinal) super.getMovementFlags(recyclerView, viewHolder)
            else 0
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                viewHolder?.itemView?.alpha = 0.5f
                onItemSelected(true)

                //The user has long-pressed a widget. Show the editing UI on that widget.
                //If the UI is already shown on it, hide it.
                val adapterPos = viewHolder?.bindingAdapterPosition ?: -1
                adapter.currentEditingInterfacePosition =
                    if (adapter.currentEditingInterfacePosition == adapterPos) -1 else adapterPos
            }

            super.onSelectedChanged(viewHolder, actionState)
        }

        override fun clearView(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ) {
            super.clearView(recyclerView, viewHolder)

            onItemSelected(false)

            viewHolder.itemView.alpha = 1.0f
        }

        override fun interpolateOutOfBoundsScroll(
            recyclerView: RecyclerView,
            viewSize: Int,
            viewSizeOutOfBounds: Int,
            totalSize: Int,
            msSinceStartScroll: Long
        ): Int {
            //The default scrolling speed is *way* too fast. Slow it down a bit.
            val direction = sign(viewSizeOutOfBounds.toFloat()).toInt()
            return (viewSize * 0.01f * direction).roundToInt()
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }
}
