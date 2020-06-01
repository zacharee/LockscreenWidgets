package tk.zwander.lockscreenwidgets.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import kotlinx.android.synthetic.main.hide_for_ids_item.view.*
import tk.zwander.lockscreenwidgets.R

class HideForIDsAdapter : RecyclerView.Adapter<HideForIDsAdapter.HideForIDsVH>() {
    private val items = SortedList(
        String::class.java,
        object : SortedList.Callback<String>() {
            override fun areItemsTheSame(item1: String?, item2: String?): Boolean {
                return item1 == item2
            }

            override fun areContentsTheSame(oldItem: String?, newItem: String?): Boolean {
                return oldItem == newItem
            }

            override fun compare(o1: String, o2: String): Int {
                return o1.compareTo(o2)
            }

            override fun onInserted(position: Int, count: Int) {
                notifyItemRangeInserted(position, count)
            }

            override fun onChanged(position: Int, count: Int) {
                notifyItemRangeChanged(position, count)
            }

            override fun onRemoved(position: Int, count: Int) {
                notifyItemRangeRemoved(position, count)
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                notifyItemMoved(fromPosition, toPosition)
            }
        }
    )

    override fun getItemCount(): Int {
        return items.size()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HideForIDsVH {
        return HideForIDsVH(
            LayoutInflater.from(parent.context).inflate(R.layout.hide_for_ids_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: HideForIDsVH, position: Int) {
        holder.onBind(items[position])
    }

    inner class HideForIDsVH(view: View) : RecyclerView.ViewHolder(view) {
        fun onBind(id: String) {
            itemView.id_text.text = id
        }
    }
}