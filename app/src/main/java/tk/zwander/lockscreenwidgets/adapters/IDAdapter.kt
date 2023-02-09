package tk.zwander.lockscreenwidgets.adapters

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import tk.zwander.lockscreenwidgets.data.IDData
import kotlin.collections.ArrayList

/**
 * Host the IDs currently on-screen (or just-removed), to be displayed in the debug ID frame
 */
class IDAdapter : RecyclerView.Adapter<IDAdapter.BaseVH>() {
    private val oldItems = ArrayList<String>()
    private val items = SortedList(IDData::class.java, object: SortedList.Callback<IDData>() {
        override fun areItemsTheSame(item1: IDData?, item2: IDData?): Boolean {
            return item1 == item2
        }

        override fun areContentsTheSame(oldItem: IDData?, newItem: IDData?): Boolean {
            return oldItem?.id == newItem?.id
        }

        override fun compare(o1: IDData, o2: IDData): Int {
            return when {
                o1.type == IDData.IDType.ADDED && o2.type != IDData.IDType.ADDED -> -1
                o2.type == IDData.IDType.ADDED && o1.type != IDData.IDType.ADDED -> 1
                o1.type == IDData.IDType.REMOVED && o2.type == IDData.IDType.SAME -> -1
                o2.type == IDData.IDType.REMOVED && o1.type == IDData.IDType.SAME -> 1
                else -> o1.id.compareTo(o2.id)
            }
        }

        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position, count)
        }

        override fun onChanged(position: Int, count: Int) {
            notifyItemRangeChanged(position, count)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }
    })

    /**
     * Process a new list of IDs, marking any as added, removed, or the same
     */
    fun setItems(newItems: List<String>) {
        if (newItems.containsAll(oldItems) && oldItems.containsAll(newItems))
            return

        val removed = oldItems - newItems.toSet()
        val added = newItems - oldItems.toSet()
        val same = oldItems - removed.toSet() - added.toSet()

        val newList = ArrayList<IDData>()

        removed.forEach {
            newList.add(IDData(it, IDData.IDType.REMOVED))
        }

        added.forEach {
            newList.add(IDData(it, IDData.IDType.ADDED))
        }

        same.forEach {
            newList.add(IDData(it, IDData.IDType.SAME))
        }

        oldItems.clear()
        oldItems.addAll(newItems)

        items.replaceAll(newList)
    }

    override fun getItemCount(): Int {
        return items.size()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseVH {
        return BaseVH(
            AppCompatTextView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        )
    }

    override fun onBindViewHolder(holder: BaseVH, position: Int) {
        holder.bind(items[position])
    }

    inner class BaseVH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView
            get() = itemView as TextView

        fun bind(data: IDData) {
            text.text = data.id

            text.setTextColor(
                when (data.type) {
                    IDData.IDType.ADDED -> Color.GREEN
                    IDData.IDType.REMOVED -> Color.RED
                    IDData.IDType.SAME -> Color.WHITE
                }
            )
        }
    }
}