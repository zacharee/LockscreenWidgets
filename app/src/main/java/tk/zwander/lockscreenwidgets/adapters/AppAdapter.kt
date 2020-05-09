package tk.zwander.lockscreenwidgets.adapters

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SortedList
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.app_item.view.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.AppInfo
import tk.zwander.lockscreenwidgets.data.WidgetListInfo

class AppAdapter(context: Context, private val selectionCallback: (provider: WidgetListInfo) -> Unit) : RecyclerView.Adapter<AppAdapter.AppVH>() {
    private val items = SortedList(AppInfo::class.java, object : SortedList.Callback<AppInfo>() {
        override fun areItemsTheSame(item1: AppInfo?, item2: AppInfo?): Boolean {
            return false
        }

        override fun areContentsTheSame(oldItem: AppInfo?, newItem: AppInfo?): Boolean {
            return false
        }

        override fun compare(o1: AppInfo, o2: AppInfo): Int {
            return o1.compareTo(o2)
        }

        override fun onMoved(fromPosition: Int, toPosition: Int) {
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onChanged(position: Int, count: Int) {
            notifyItemRangeChanged(position, count)
        }

        override fun onInserted(position: Int, count: Int) {
            notifyItemRangeInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) {
            notifyItemRangeRemoved(position, count)
        }
    })

    private val picasso = Picasso.Builder(context)
        .addRequestHandler(WidgetAdapter.AppIconRequestHandler(context))
        .addRequestHandler(WidgetAdapter.RemoteResourcesIconHandler(context))
        .build()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        AppVH(
            LayoutInflater.from(parent.context).inflate(
                R.layout.app_item,
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: AppVH, position: Int) {
        holder.parseInfo(items.get(holder.adapterPosition))
        holder.setIsRecyclable(false)
    }

    override fun getItemCount() = items.size()

    fun addItem(item: AppInfo) {
        items.add(item)
    }

    fun addItems(items: MutableCollection<AppInfo>) {
        this.items.addAll(items)
    }

    inner class AppVH(view: View) : RecyclerView.ViewHolder(view) {
        private val adapter = WidgetAdapter(picasso, selectionCallback)

        fun parseInfo(info: AppInfo) {
            itemView.widget_holder.adapter = adapter
            itemView.widget_holder.addItemDecoration(DividerItemDecoration(itemView.context, RecyclerView.HORIZONTAL))

            itemView.app_name.text = info.appName
            info.widgets.forEach {
                adapter.addItem(it)
            }

            picasso
                .load(Uri.parse("${WidgetAdapter.AppIconRequestHandler.SCHEME}:${info.appInfo.packageName}"))
                .fit()
                .into(itemView.app_icon)
        }
    }
}