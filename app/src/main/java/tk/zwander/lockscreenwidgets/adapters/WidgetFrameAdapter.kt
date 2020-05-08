package tk.zwander.lockscreenwidgets.adapters

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.widget_page_holder.view.*
import kotlinx.coroutines.*
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.host.WidgetHost
import tk.zwander.lockscreenwidgets.interfaces.ItemTouchHelperAdapter
import tk.zwander.systemuituner.lockscreenwidgets.R
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class WidgetFrameAdapter(private val manager: AppWidgetManager, private val host: WidgetHost) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), ItemTouchHelperAdapter, CoroutineScope by MainScope() {
    companion object {
        const val VIEW_TYPE_WIDGET = 0
        const val VIEW_TYPE_ADD = 1
    }

    val widgets = ArrayList<WidgetData>()

    fun updateWidgets(newWidgets: List<WidgetData>) {
        if (widgets.isEmpty()) {
            widgets.addAll(newWidgets)
        } else {
            val oldWidgets = widgets.toList()
            this.widgets.clear()
            this.widgets.addAll(newWidgets)

            val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldWidgets[oldItemPosition].id == newWidgets[newItemPosition].id
                }

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldWidgets[oldItemPosition] == newWidgets[newItemPosition]
                }

                override fun getNewListSize(): Int {
                    return newWidgets.size
                }

                override fun getOldListSize(): Int {
                    return oldWidgets.size
                }
            }, true)

            result.dispatchUpdatesTo(this)
        }
    }

    override fun onMove(from: Int, to: Int): Boolean {
        return if (to < widgets.size && from < widgets.size) {
            if (from < to) {
                for (i in from until to) {
                    Collections.swap(widgets, i, i + 1)
                }
            } else {
                for (i in from downTo to + 1) {
                    Collections.swap(widgets, i, i - 1)
                }
            }
            notifyItemMoved(from, to)
            true
        } else false
    }

    override fun getItemCount(): Int {
        return widgets.size + 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == widgets.size) VIEW_TYPE_ADD
        else VIEW_TYPE_WIDGET
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_ADD) AddWidgetVH(inflater.inflate(R.layout.add_widget, parent, false))
        else WidgetVH(inflater.inflate(R.layout.widget_page_holder, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position < widgets.size) {
            (holder as WidgetVH).onBind(widgets[position])
        }
    }

    inner class WidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        fun onBind(data: WidgetData) {
            itemView.widget_holder.apply {
                launch {
                    val widgetInfo = withContext(Dispatchers.Main) {
                        manager.getAppWidgetInfo(data.id)
                    }

                    removeAllViews()
                    addView(withContext(Dispatchers.Main) { host.createView(itemView.context, data.id, widgetInfo) })
                }
            }
        }
    }

    inner class AddWidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        init {
            itemView.setOnClickListener {
                val intent = Intent(view.context, AddWidgetActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                view.context.startActivity(intent)
            }
        }
    }
}